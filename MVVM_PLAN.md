# ZumpaReader – MVC → MVVM migration plan

Follow-up to `UPGRADE_PLAN.md` §D. The Koin step (`fd5d6c8`) put DI in place; this document is the
screen-by-screen plan for the actual MVVM work.

**Goal shape.** Every screen gets a `ScreenNameUiState` data class that holds *the whole* visible
state of that screen. The view layer subscribes to one `StateFlow<ScreenNameUiState>` and renders
it; it never reads state back out of widgets. One-shot things (navigation, toasts, scrolling,
clipboard) are not state — they go through a separate effects `Flow`. This is deliberately the
shape Compose wants, so the later Compose migration is a rewrite of the render half only.

**LiveData.** There is none in the codebase — `grep LiveData` returns nothing. So this is not a
LiveData→Flow replacement, it is a "no observable layer at all" → `StateFlow` introduction. The rule
stands for anything new: `StateFlow` for state, `SharedFlow`/`Channel` for events, never `LiveData`.

## Decisions taken up front

| # | Decision | Consequence |
|---|---|---|
| 1 | **`SettingsActivity` is out of scope.** | It extends the framework `android.preference.PreferenceActivity` and has no `ViewModelStore` at all. Migrating it to `androidx.preference` only to migrate it again to Compose is wasted work — it goes to the Compose phase (§ Phase 9) and is done as Compose+MVVM in one step. Until then it keeps talking to `ZumpaPrefs` directly. |
| 2 | **UiState holds immutable UI row models.** | `ThreadRowUiState`, `ThreadItemUiState` etc. — plain `val`-only data classes carrying only what the row draws. `ZumpaThread`/`ZumpaThreadItem` stay as the parser's output but stop being the thing the UI sees, and stop being mutated in place by screens. |
| 3 | **Text styling is extracted behind one interface.** | `ZumpaTextRenderer<T>` with a `CharSequence`/`Spanned` implementation today and an `AnnotatedString` implementation in the Compose phase. UiState carries **raw markup strings**; rendering happens at the UI boundary, off the main thread. |
| 4 | **Adapters keep their current classes but take whole lists.** | `MainListAdapter.setItems(list)` / `SubListAdapter.setItems(list)` replace `addItems`/`updateItems`/`removeItem`. No `ListAdapter`+`DiffUtil` rewrite now — see the mitigation in §Risks for keeping the sub-list's insert animation. |

---

## 0. Inventory

| Screen | Class | Today | Target |
|---|---|---|---|
| App chrome | `MainActivity` | `AppCompatActivity`, owns toolbar/title/progress/FAB, parses intents, relays `onActivityResult` | `MainViewModel` + `MainUiState`, activity-scoped, shared with child fragments |
| Thread list | `MainListFragment` | `BaseFragment`, loads + pages + mutates adapter | `MainListViewModel` + `MainListUiState` |
| Thread detail | `SubListFragment` | `BaseFragment`, load/post/vote/reply, phone+tablet delegate | `SubListViewModel` + `SubListUiState` |
| Two-pane host | `TabletFragment` | container only | no ViewModel; two child fragments, selection via `SelectedThreadStore` |
| Post dialog | `PostFragment` | `BaseDialogFragment`, `FragmentTabHost`, `startActivityForResult` | `PostViewModel` + `PostUiState` (scoped to the dialog, shared with its children) |
| Post message tab | `PostMessageFragment` | `DialogFragment`, sends thread/response | `PostMessageViewModel` + `PostMessageUiState` |
| Post image tab | `PostImageFragment` | `Fragment`, 7 hand-retained fields + `restoreState` flag | `PostImageViewModel` + `PostImageUiState` |
| Offline download | `OfflineDownloadFragment` | `DialogFragment` + `AsyncTask` | `OfflineDownloadViewModel` + `OfflineDownloadUiState` |
| Image viewer | `ImageActivity` | `AppCompatActivity`, Fresco callbacks, blocking call on main thread | `ImageViewModel` + `ImageUiState` |
| Settings | `SettingsActivity` | `PreferenceActivity` | **deferred to Phase 9 (Compose)** |

Not screens, but in the blast radius: `BaseFragment`, `BaseDialogFragment`, `SendingFragment`,
`BusProvider` + `event/*`, `ZumpaReaderApp.zumpaData` / `zumpaReadStates`, `LoaderTask`,
`MainListAdapter`, `SubListAdapter`, `ToggleAdapter`.

---

## Phase 0 — infrastructure

New package `com.scurab.android.zumpareader.arch`.

```kotlin
abstract class BaseViewModel<S : Any>(initialState: S) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()
    protected val state: S get() = _uiState.value

    protected fun setState(reducer: S.() -> S) = _uiState.update(reducer)

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()
    protected fun effect(effect: UiEffect) { _effects.trySend(effect) }
}

interface UiEffect
```

Effects that every screen needs live in `arch`; screen-specific ones are nested in the screen's own
file:

```kotlin
data class ShowToast(val text: String? = null, @StringRes val resId: Int = 0) : UiEffect
data object HideKeyboard : UiEffect
data class CopyToClipboard(val text: CharSequence) : UiEffect
```

Collection helper on `BaseFragment` (and a twin on `MainActivity`):

```kotlin
protected fun <T> Flow<T>.collectWhileStarted(block: suspend (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { collect(block) }
    }
}
```

This replaces `launchWithView()`. Note the semantic change and why it is wanted: `launchWithView`
runs work bound to the view and keeps running while the fragment is in the background;
`repeatOnLifecycle(STARTED)` stops *collecting* in the background while the ViewModel's own
`viewModelScope` work keeps running. That is exactly the fix for the current
`onPause { isLoading = false }` workarounds in `MainListFragment` and `SubListFragment` — the load
no longer has to be abandoned to stop the UI from being touched.

Also in Phase 0:

* `single { DeviceConfig(isTablet = androidContext().resources.getBoolean(R.bool.is_tablet)) }` —
  removes `resources.getBoolean` from `BaseFragment`/`BaseDialogFragment` and makes tablet
  behaviour a constructor input a test can set.
* `app/src/test` source set with `koin-test`, `mockk`, `turbine`, junit5, plus a
  `KoinModulesTest.verify()` so a missing binding fails the build instead of the first injection
  (`UPGRADE_PLAN.md` §D last bullet).
* Add `androidx-lifecycle-viewmodel-savedstate` to the catalog — `PostFragment` and
  `MainListFragment` both hold state that should survive process death (see the `argFlagUsed` TODO).

No dependency additions otherwise: `koin-android` 4.x already carries
`org.koin.androidx.viewmodel.ext.android.viewModel`, and `lifecycle-runtime-ktx` /
`lifecycle-viewmodel-ktx` are already in `bundles.android-lifecycle`.

### The text rendering layer (decision 3)

`ZumpaSimpleParser.parseBody` builds `ImageSpan`s and reads `R.attr.contextColorText2` off the
**theme**, so it needs a themed `Context` — an `Application` context resolves the wrong attribute.
That is the reason styling cannot simply move into a ViewModel. New package `text`:

```kotlin
/** T is CharSequence today, androidx.compose.ui.text.AnnotatedString in the Compose phase. */
interface ZumpaTextRenderer<T> {
    fun body(markup: String): T
    fun subject(markup: String): T
    fun author(name: String, rating: String?): T
}

class SpannedTextRenderer(private val themedContext: Context) : ZumpaTextRenderer<CharSequence> {
    private val cache = LruCache<String, CharSequence>(512)
    override fun body(markup: String) = cache.getOrPut(markup) {
        ZumpaSimpleParser.parseBody(markup, themedContext)
    }
    // subject() -> parseBody(.., ALIGN_BASELINE), author() -> the ForegroundColorSpan rating logic
}
```

Everything that today calls `ZumpaSimpleParser.parseBody` / `item.styledBody(context)` /
`item.styledAuthor(context)` / `thread.styledSubject(context)` goes through this one interface, and
the lazily-cached `_styledBody` / `_styledAuthor` / `_styledSubject` fields are **deleted from the
model classes** — that caching moves into the renderer, where it is not a mutation of shared domain
state.

Applied at the UI boundary, off the main thread — this is the fix for the last bullet of
`UPGRADE_PLAN.md` §D:

```kotlin
// in the fragment
private val renderer by lazy { SpannedTextRenderer(requireContext()) }

viewModel.uiState
    .map { it.toRendered(renderer) }        // pure function, UiState -> RenderedUiState
    .flowOn(Dispatchers.Default)
    .distinctUntilChanged()
    .collectWhileStarted { adapter.setItems(it.rows) }
```

`RenderedThreadRowUiState` is the same data class with `CharSequence` where the source had `String`.
In the Compose phase the fragment-side mapper is dropped and the composable calls
`AnnotatedTextRenderer` inside `remember`.

---

## Phase 1 — repositories (no UI change)

The shared mutable state has to move off the `Application` before any screen can own its state.
New package `repository`, all in `coreModule`.

### `ZumpaThreadRepository`

Replaces `ZumpaReaderApp.zumpaData` (a `TreeMap` reached into from 8 call sites).

```kotlin
interface ZumpaThreadRepository {
    val threads: StateFlow<Map<String, ZumpaThread>>
    fun thread(id: String): ZumpaThread?
    suspend fun loadMainPage(fromThread: String?, filter: String): ZumpaMainPageResult
    suspend fun loadThread(id: String): List<ZumpaThreadItem>
    suspend fun toggleFavorite(id: String)
    suspend fun toggleIgnore(id: String)
    suspend fun sendThread(body: ZumpaThreadBody): Boolean
    suspend fun sendResponse(threadId: String, body: ZumpaThreadBody): Boolean
    suspend fun voteSurvey(body: ZumpaVoteSurveyBody): Unit
    fun replaceAll(data: Map<String, ZumpaThread>)   // offline download result
}
```

**The one non-obvious constraint.** `ZumpaAPI` is a Koin `factory` on purpose — the online/offline
switch is a runtime setting, so a holder that injects it once keeps the API it was handed at
construction (`di/Modules.kt:61-68`). A ViewModel or a `single` repository is exactly such a holder.
So the repository must take a **provider**, never an instance:

```kotlin
single<ZumpaThreadRepository> {
    ZumpaThreadRepositoryImpl(api = { get() }, prefs = get(), readStates = get())
}
```

and call `api()` inside each suspend function. Every ViewModel injects the repository, never
`ZumpaAPI`. Add a test that flips `isOffline` and asserts the repository starts returning offline
data — this is the failure mode that only shows up at runtime.

Retry/redirect wrapping (`retrying { }`, `ignoringZumpaRedirect { }`) moves into the repository, so
no screen repeats it.

### `ZumpaSettingsRepository`

Wraps `ZumpaPrefs` and makes the reactive parts observable, so screens stop polling prefs in
`onResume`:

```kotlin
class ZumpaSettingsRepository(private val prefs: ZumpaPrefs, context: Context) {
    val isOffline: StateFlow<Boolean>
    val isLoggedIn: StateFlow<Boolean>
    val isLoggedInNotOffline: StateFlow<Boolean>   // combine(isLoggedIn, isOffline)
    val filter: StateFlow<String>
    val loadImages: StateFlow<Boolean>
    val showLastAuthor: StateFlow<Boolean>
    val nickName: String
    fun setOffline(value: Boolean)
}
```

Backed by `SharedPreferences.OnSharedPreferenceChangeListener` in a `callbackFlow`, `stateIn`'d on
a repository-owned scope. `ZumpaPrefs` itself is unchanged so `SettingsActivity` keeps working
during the whole migration.

This single change removes: `MainActivity.onResume`'s FAB recompute, `MainListFragment.lastOffline`
bookkeeping, `MainListFragment.invalidateOptionsMenu` flag, and `SubListFragment`'s repeated
`zumpaApp.zumpaPrefs.isLoggedInNotOffline` reads.

### `ZumpaReadStateRepository`

Wraps `zumpaReadStates` + the `MAX_STATES_TO_STORE` trimming + the gson persistence currently
driven from `ZumpaReaderApp`'s `ActivityLifecycleCallbacks`. Keeps the same
"persist when the last activity stops" trigger, just moved behind an interface.

### `SelectedThreadStore` and `AppEventBus` — otto's replacement

otto has exactly 2 `@Subscribe` handlers and 2 `post()` calls. They are two different things and
should not both become a bus:

* **`LoadThreadEvent`** is *tablet pane selection*, i.e. state → a store:
  ```kotlin
  class SelectedThreadStore {
      private val _selected = MutableStateFlow<String?>(null)
      val selected: StateFlow<String?> = _selected.asStateFlow()
      fun select(id: String) { _selected.value = id }
  }
  ```
  `MainListViewModel` writes, `SubListViewModel` collects. On a phone nothing writes to it, so the
  same ViewModel code covers both form factors and the `TabletBehaviour`/`PhoneBehaviour` split
  shrinks to pure view behaviour.

* **`DialogEvent(DIALOG_EVENT_STOP)`** is a genuine one-shot ("offline download finished, reload"):
  ```kotlin
  class AppEventBus {
      private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
      val events: SharedFlow<AppEvent> = _events.asSharedFlow()
      fun emit(event: AppEvent) { _events.tryEmit(event) }
  }
  sealed interface AppEvent { data object OfflineDataChanged : AppEvent }
  ```

At the end of Phase 6 this deletes `BusProvider.kt`, `event/DialogEvent.kt`, `event/LoadThreadEvent.kt`,
the `BusProvider.register/unregister` pairs in both base classes, and the `otto` dependency
(deprecated 2015 — `UPGRADE_PLAN.md` §B).

### The `isSending` ProgressDialog

`SendingFragment` is an interface with a `var sendingDialog: ProgressDialog?` whose setter *is* the
state. It becomes a plain `isSending: Boolean` field in each screen's UiState, plus one view-layer
helper that reacts to it:

```kotlin
class SendingDialogController(private val context: Context) {
    fun update(isSending: Boolean)   // show/dismiss, same AppTheme.Dialog style
}
```

`SendingFragment` is deleted. `ProgressDialog` is deprecated but kept as-is here — swapping it for
an inline overlay is a Compose-phase job, not a reason to grow this migration.

---

## Phase 2 — `MainActivity`

`MainActivity` is the chrome host: title, progress bar, FAB (visibility + `QuickHideBehavior`),
fragment container, intent handling, back dispatch.

```kotlin
data class MainUiState(
    val title: String = "",
    val isProgressVisible: Boolean = false,
    val fab: FabUiState = FabUiState(),
)

data class FabUiState(
    val isVisible: Boolean = false,
    val isScrollHideEnabled: Boolean = true,
)
```

```kotlin
sealed interface MainEffect : UiEffect {
    data class OpenThread(val threadId: String) : MainEffect
    data class OpenPostDialog(val subject: String?, val text: String?, val uris: List<Uri>?) : MainEffect
    data object ReloadCurrentScreen : MainEffect
}
```

`MainViewModel` is activity-scoped and injected into child fragments with
`by activityViewModel()`; the child screens push their `title` / `isProgressVisible` /
`fab` requirements into it from their own state collector. That keeps one owner of the chrome while
each screen still describes what it wants.

**Intent parsing moves into the ViewModel.** The activity converts the `Intent` into a plain data
class and hands it over — the decision logic (push thread vs share vs "log in first") becomes
testable:

```kotlin
data class LaunchPayload(
    val threadId: String? = null,
    val subject: String? = null,
    val text: String? = null,
    val uris: List<Uri> = emptyList(),
)
fun MainViewModel.onLaunch(payload: LaunchPayload)
```

The `isLoggedIn` check that today emits `toast(R.string.err_login_first)` inline
(`MainActivity.kt:108`) becomes `effect(ShowToast(resId = R.string.err_login_first))`.

FAB visibility stops being recomputed in `onResume` and becomes derived:
`combine(settings.isLoggedInNotOffline, screenWantsFab) { a, b -> a && b }`.

`onActivityResult` relay to `PostFragment` is deleted here — Phase 5 moves `PostFragment` to the
ActivityResult API, which removes the need for the activity to forward anything.

---

## Phase 3 — `MainListFragment`

```kotlin
data class MainListUiState(
    val rows: List<ThreadRowUiState> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val isLoggedIn: Boolean = false,
    val title: String = "",
    val hasMore: Boolean = true,
)

data class ThreadRowUiState(
    val id: String,
    val subject: String,          // raw markup, rendered by ZumpaTextRenderer
    val author: String,
    val lastAuthor: String?,
    val answerCount: Int,
    val time: Long,
    val useShortTimeFormat: Boolean,   // today: lastAuthor != null
    val state: ThreadState,            // enum replacing the STATE_* Int constants
    val isFavorite: Boolean,
    val isSelected: Boolean,           // tablet only
)

enum class ThreadState { None, New, Updated, Own, ResponseForYou }
```

`ThreadState.ordinal` must keep matching the `LevelListDrawable` levels the item layout uses
(`STATE_NONE=0 … STATE_RESPONSE_4U=4`) — declare it in that order and add a test asserting it.

Fragment → ViewModel:

```kotlin
fun onRefresh()
fun onLoadMore()
fun onThreadClick(id: String)
fun onFavoriteClick(id: String)
fun onIgnoreClick(id: String)
fun onShareClick(id: String)
fun onOfflineToggle()
fun onFabClick()
```

```kotlin
sealed interface MainListEffect : UiEffect {
    data class OpenThread(val id: String) : MainListEffect          // phone only
    data class ShareThread(val link: String) : MainListEffect
    data object OpenSettings : MainListEffect
    data object OpenPostDialog : MainListEffect
    data object ShowOfflineDownloadDialog : MainListEffect
}
```

Notes:

* `nextThreadId`, `lastFilter`, `lastOffline` become private ViewModel fields — as a side effect
  paging position now survives rotation, which it does not today.
* The `firstLoad && isTablet → openThread(zumpaData.lastEntry())` branch becomes
  `selectedThreadStore.select(id)` guarded by `deviceConfig.isTablet`.
* Read-state decoration (`setStateBasedOnReadValue`) moves into the mapping from `ZumpaThread` to
  `ThreadRowUiState`, so it is a pure function of (thread, readState, userName) instead of an
  in-place mutation of a shared object.
* The offline toggle currently does prefs write + dialog + `invalidateOptionsMenu` + FAB animation
  in one `when` branch (`MainListFragment.kt:120-140`). It becomes
  `settings.setOffline(!isOffline)` and everything else falls out of the observed state.
* `MainListAdapter` keeps its class; `addItems`/`removeAll`/`removeItem`/`setSelectedItem` collapse
  into `setItems(List<RenderedThreadRowUiState>)`. `OnShowItemListener` (the 15-from-end paging
  trigger) stays, but calls `viewModel.onLoadMore()`.
* `IsReloadable` can go — reload is `viewModel.onRefresh()` reached through `MainViewModel`'s
  `ReloadCurrentScreen` effect, or better, the screen collects `AppEvent.OfflineDataChanged` itself.

---

## Phase 4 — `SubListFragment` (the big one)

```kotlin
data class SubListUiState(
    val threadId: String = "",
    val title: String = "",                  // raw subject markup
    val rows: List<SubListRowUiState> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isPostPanelVisible: Boolean = false,
    val draft: String = "",
    val canPost: Boolean = false,            // isLoggedInNotOffline
)

sealed interface SubListRowUiState {
    val itemIndex: Int
    data class Message(
        override val itemIndex: Int,
        val author: String,
        val authorReal: String?,
        val rating: String?,
        val body: String,                    // raw markup
        val time: Long,
    ) : SubListRowUiState
    data class Link(override val itemIndex: Int, val url: String) : SubListRowUiState
    data class Image(override val itemIndex: Int, val url: String) : SubListRowUiState
    data class Survey(override val itemIndex: Int, val survey: SurveyUiState) : SubListRowUiState
}

data class SurveyUiState(
    val id: String,
    val question: String,
    val responses: Int,
    val items: List<SurveyItemUiState>,
)
data class SurveyItemUiState(val id: Int, val surveyId: String, val text: String, val percents: Int, val voted: Boolean)
```

**The flattening moves into the ViewModel.** `SubListAdapter.buildAdapterItems` today turns each
`ZumpaThreadItem` into 1 message row + N link/image rows + an optional survey row, and decides
image-vs-link from `loadImages`. That is list-shaping logic, not view logic — it becomes a pure
function in the ViewModel producing `List<SubListRowUiState>`, and `SubListAdapter` is left with
`getItemViewType` + bind. This is also what makes the eventual `LazyColumn` a direct translation.

```kotlin
sealed interface SubListEffect : UiEffect {
    data object ScrollToBottom : SubListEffect
    data class ScrollBy(val dy: Int) : SubListEffect
    data class OpenThread(val id: String) : SubListEffect
    data class OpenImage(val url: String, val transitionView: Int) : SubListEffect
    data class OpenLink(val url: String) : SubListEffect
    data class OpenPostFragment(val flag: Int?) : SubListEffect
}
```

Scroll direction (`SCROLL_UP` / `SCROLL_NONE` / `SCROLL_DOWN`) is a one-shot, so it is an effect,
not state. The `offsetY = -2 * rv.computeVerticalScrollOffset()` read stays in the fragment: the
ViewModel emits `ScrollBy` intent, the fragment computes the pixel amount.

Pure logic that moves into the ViewModel:

* `onReplyClick(item)` — the `@author: \n` insert-or-remove against the draft
  (`containsAuthor`/`appendReply`, `SubListFragment.kt:316-331`) becomes a string transform on
  `state.draft`. The `contextColorText` span it applies today is a *render* concern and moves to
  the renderer.
* `onSpeakClick(item)` — the `"$author: $body\n----\n"` append, same treatment.
* `onCopyClick(item)` — `effect(CopyToClipboard(item.body))`.
* `onSend()` — the empty-message check, body construction, `ignoringZumpaRedirect`, and the
  follow-up reload.
* `onSurveyVote(item)` and the reload after it.
* `onBackPressed(): Boolean` — "hide the post panel if visible, else let the back stack handle it".
* `storeReadState` → `readStateRepository.markRead(threadId, count)`.

Stays in the fragment (genuinely view behaviour): `updateRecycleViewPadding` and its
`OnGlobalLayoutListener`, the show/hide animations, the `PhoneBehaviour`/`TabletBehaviour` delegate
— reduced to navigation style (open a fragment vs `show()` a dialog vs swap the pane) now that
"which thread" comes from `SelectedThreadStore`.

`SubListFragment.onLoadThreadEvent` mutating its own `arguments` (`SubListFragment.kt:189`) goes
away: the thread id lives in the ViewModel, seeded from `SavedStateHandle` and updated by the
`SelectedThreadStore` collector.

---

## Phase 5 — the post trio

### `PostFragment` → `PostViewModel` / `PostUiState`

Scoped to the dialog and shared with both child fragments via
`by viewModel(ownerProducer = { requireParentFragment() })` — that replaces the
`(parentFragment as PostFragment).onSharedImage(link)` up-calls.

```kotlin
data class PostUiState(
    val tabs: List<PostTabUiState> = listOf(PostTabUiState.Message),
    val selectedTabTag: String = MESSAGE_TAB,
    val pendingPickerRequest: PickerRequest? = null,   // Camera / Gallery, one-shot arm
)

sealed interface PostTabUiState {
    data object Message : PostTabUiState
    data class Image(val tag: String, val uri: Uri, @DrawableRes val iconRes: Int) : PostTabUiState
}
```

Two latent bugs are fixed for free by this move:

* `argFlagUsed` carries a `//TODO: doesn't work with lifecycle!, has to be saved` comment
  (`PostFragment.kt:156`). In the ViewModel it survives rotation without the manual
  `onSaveInstanceState` dance; with `SavedStateHandle` it survives process death too.
* `pendingGiphyLink` is applied in `onResume` and is lost if the process dies — same fix.

`startActivityForResult` (`REQ_CODE_IMAGE` / `REQ_CODE_CAMERA` / `REQ_CODE_GIPHY`) becomes
`registerForActivityResult` with `ActivityResultContracts.GetContent()` and `TakePicture()`. That
deletes `PostFragment.isRequestCode`, `PostFragment.onActivityResult`, and the relay in
`MainActivity.onActivityResult` (`MainActivity.kt:193-200`). Also deletes the
`prefs.lastCameraUri` round-trip — `TakePicture` gets the target `Uri` as its input, so it no
longer has to be smuggled through SharedPreferences.

### `PostMessageFragment` → `PostMessageViewModel` / `PostMessageUiState`

```kotlin
data class PostMessageUiState(
    val subject: String = "",
    val message: String = "",
    val isSubjectEditable: Boolean = true,   // false when replying to an existing thread
    val isSending: Boolean = false,
    val canSend: Boolean = false,            // subject.isNotBlank() && message.isNotBlank()
)
```

The `links` `ArrayList` + the `onViewStateRestored` append (`PostMessageFragment.kt:85-98`) is the
same class of hand-rolled retention as above and becomes `pendingLinks` in the ViewModel.

The empty-subject / empty-message toasts become `canSend` in the state (send button disabled) plus
the toast as a fallback effect. Send picks `sendThread` vs `sendResponse` on `threadId == null`,
both through `ZumpaThreadRepository`.

`dismiss()` today reaches up two levels — `(parentFragment as PostFragment).dismissAllowingStateLoss()`
plus `mainActivity.reloadData()`. That becomes `effect(Dismiss)` + the parent `PostViewModel`
signalling the list to refresh.

### `PostImageFragment` → `PostImageViewModel` / `PostImageUiState`

The clearest ViewModel case in the codebase: 7 fields hand-retained across view destruction behind
a `restoreState` boolean flipped in `onDestroyView`/`onDestroy`.

```kotlin
data class PostImageUiState(
    val thumbnailPath: String? = null,
    val original: ImageMetaUiState? = null,
    val resized: ImageMetaUiState? = null,
    val rotationDegrees: Int = 0,
    val sampleSizeIndex: Int = 0,
    val uploadedLink: String? = null,
    val isBusy: Boolean = false,
)
data class ImageMetaUiState(val width: Int, val height: Int, val bytes: Long)
```

`CopyFromResourcesTask.execute()` and `ProcessImageTask.execute()` are already `suspend` +
`withContext(Dispatchers.IO)`, so they move into `viewModelScope` unchanged. The upload
(`zumpaPHPAPI.postImage`) moves behind an `ImageUploadRepository` for symmetry with the rest.
`restoreState`, `imageFile`, `imageFileToUpload` all disappear.

---

## Phase 6 — `OfflineDownloadFragment`, and otto's funeral

```kotlin
data class OfflineDownloadUiState(
    val pages: String = "1",
    val downloadImages: Boolean = false,
    val threadsDownloaded: Int = 0,
    val imagesDownloaded: Int = 0,
    val imagesTotal: Int = 0,
    val isRunning: Boolean = false,
    val isCancellable: Boolean = false,   // back key is swallowed while running today
)
```

`LoaderTask` (the last `AsyncTask` in the app) becomes `OfflineDownloadUseCase`:

```kotlin
class OfflineDownloadUseCase(private val ws: ZumpaWSAPI, private val gson: Gson) {
    fun run(pages: Int, downloadImages: Boolean, outFile: String): Flow<OfflineProgress>
}
sealed interface OfflineProgress {
    data class Threads(val count: Int) : OfflineProgress
    data class Images(val done: Int, val total: Int) : OfflineProgress
    data class Done(val data: LinkedHashMap<String, ZumpaThread>) : OfflineProgress
}
```

`flow { … }.flowOn(Dispatchers.IO)`, collected in `viewModelScope`. Cancellation is
`job.cancel()` instead of `AsyncTask.cancel(true)` — and unlike today it actually interrupts the
in-flight image prefetch loop. `notifyProgressChanged` + the `images.post(Runnable)` hop are gone;
progress is just state.

On `Done`: `threadRepository.replaceAll(data)` + `appEventBus.emit(OfflineDataChanged)`.
`MainListViewModel` collects that and reloads — replacing `onDialogEvent` +
`zumpaApp.loadOfflineData()` + `loadPage()` (`MainListFragment.kt:75-82`).

Then delete: `BusProvider.kt`, `event/DialogEvent.kt`, `event/LoadThreadEvent.kt`, the
register/unregister pairs in `BaseFragment`/`BaseDialogFragment`, `LoaderTask.kt`, `SendingFragment.kt`,
and the `otto` entries from `libs.versions.toml` + `app/build.gradle`.

---

## Phase 7 — `ImageActivity`

```kotlin
sealed interface ImageUiState {
    data object Loading : ImageUiState
    data class Loaded(val bitmap: Bitmap) : ImageUiState
    data class Failed(val url: String) : ImageUiState
}
```

The three nested Fresco `DataSubscriber` callbacks become two `suspendCancellableCoroutine`
wrappers in an `ImageCacheDataSource`, and the ViewModel is:

```
bitmapCache(url) ?: encoded(url)?.decode() ?: Failed(url)
```

**This fixes a real bug**: `DataSources.waitForFinalResult(dataSource)` (`ImageActivity.kt:79`) is a
blocking call currently made on the main thread. In `viewModelScope` with
`withContext(Dispatchers.IO)` it stops blocking the UI thread.

`Failed` is rendered by the activity as `startLinkActivity(url) + finish()` — same behaviour, but
now a state the activity reacts to rather than a call made from inside a callback.

---

## Phase 8 — cleanup

* `BaseFragment` loses `zumpaData`, `zumpaApp`, `progressBarVisible`, `isLoading`, `_isTablet`,
  the otto hooks, and `launchWithView`. What is left is `collectWhileStarted`, the activity-scoped
  `MainViewModel` handle, and `onBackButtonClick`.
* `ZumpaReaderApp` loses `zumpaData`, `zumpaReadStates`, `loadReadStates`, `storeReadStates`,
  `loadOfflineData`, and the `zumpa*API` convenience accessors. `onCreate` keeps Koin, Fresco,
  Picasso, Firebase, notification channels. The `ActivityLifecycleCallbacks` block delegates to
  `ZumpaReadStateRepository.persist()`.
* Delete the `styledBody` / `styledAuthor` / `styledSubject` caches and the `STATE_*` Int constants
  from `model/ZumpaModel.kt`.
* `IsReloadable` deleted.
* Re-run `./gradlew :app:lintDebug` and confirm it is still clean.

---

## Phase 9 — Compose (next project, not this one)

Ordered so the first screen carries the least risk of the shared plumbing:

1. **`SettingsActivity` first** (decision 1) — it has no ViewModel today, so there is nothing to
   throw away. `PreferenceActivity` → `ComponentActivity` + `setContent`, `res/xml/settings.xml` and
   `ButtonPreference` replaced by composables, `SettingsUiState` + `SettingsViewModel`, and the
   login/logout/push-registration logic (currently 90 lines of the activity) into an
   `AuthRepository`. It is the screen with the most logic and the least UI, so it is the cheapest
   real test of the pattern.
2. `AnnotatedTextRenderer : ZumpaTextRenderer<AnnotatedString>` — the second implementation of the
   interface introduced in Phase 0. The `SpannedTextRenderer` stays until the last RecyclerView goes.
3. `ImageActivity`, then `PostImageFragment` / `PostMessageFragment` — small, already state-driven
   by then.
4. `MainListFragment` and `SubListFragment` last: `List<ThreadRowUiState>` / `List<SubListRowUiState>`
   → `LazyColumn`, which is where decision 4 gets repaid (the adapters are thrown away rather than
   migrated to `ListAdapter` twice).
5. Only then is `swipy` replaceable — `SubListFragment` uses its bottom-direction pull-to-refresh,
   which `androidx.swiperefreshlayout` cannot do (`UPGRADE_PLAN.md` §B), but Compose's
   `PullToRefreshBox` can. That unblocks dropping Jetifier, which unblocks AGP 9
   (`UPGRADE_PLAN.md` §C).

---

## Phase 10 — known backend-facing bugs, after the Compose migration

Not part of the MVVM work and not caused by it. Parked here rather than in an issue tracker so they
stay attached to the plan; both are in the request path and are cheapest to fix once the post/send
flow is already state-driven.

1. **The cookie grows until the backend rejects the request.** `ZumpaPrefs.cookies` is a
   `Set<String>` that is only ever replaced wholesale on login (`ParseUtils.extractCookies`), and
   `cookiesMap` hands the whole set to `JavaNetCookieJar` on every request. Once it gets big enough
   the server answers with an error instead of a page. It needs to be trimmed automatically —
   drop expired and duplicate-name cookies before building the header, rather than leaving the user
   to log out and back in.
2. **Message text is not encoded/escaped properly on send.** `ZumpaThreadBody.toHttpPostString`
   builds the form body by hand with `String.encodeHttp()` (`URLEncoder.encode(this, ENCODING)`,
   where `ZR.Constants.ENCODING` is the forum's legacy charset). Characters outside that charset —
   emoji above all — are silently dropped by the backend. Needs the body encoded so the forum
   accepts it, or the unsupported characters rejected in the UI before sending rather than
   disappearing after.

Both should be re-raised once the Compose phase lands.

---

## Risks and things to verify

1. **The offline `factory<ZumpaAPI>` capture.** The single biggest trap: any ViewModel or `single`
   that injects `ZumpaAPI` directly freezes the online/offline choice at construction. Only
   `ZumpaThreadRepositoryImpl` may resolve it, and only per call, through a provider lambda.
   Cover it with a test that toggles `isOffline` between two calls.
2. **`ImageSpan` construction off the main thread.** The renderer's `flowOn(Dispatchers.Default)` is
   what fixes the main-thread styling, but `ZumpaSimpleParser.parseBody` inflates drawables. Drawable
   *creation* is safe off the main thread; verify on a device that no span implementation touches a
   `View`. If one does, fall back to rendering in the binder with the LRU cache absorbing the cost.
3. **The sub-list append animation.** Decision 4 replaces `updateItems`'s
   `notifyItemRangeInserted(oldSize, …)` with whole-list `setItems`. A naive
   `notifyDataSetChanged()` there loses the insert animation and jumps the scroll position while
   reading a thread. Mitigation: `setItems` compares the new list against the old and, when it is a
   pure append (the common case — new messages arrive at the end), still emits
   `notifyItemRangeInserted`; otherwise `notifyDataSetChanged`. Same for `MainListAdapter`'s
   `removeItem` on ignore. This keeps the "whole list in" contract without the visible regression.
4. **`swipy`'s `isRefreshing`.** Binding `isRefreshing` from state and also having the widget set it
   on user pull is a feedback loop. Guard the assignment with a
   `if (swipe.isRefreshing != state.isRefreshing)` check, the way the current
   `if (it.isRefreshing) { it.isRefreshing = value }` does by accident.
5. **`repeatOnLifecycle(STARTED)` vs the current `onPause { isLoading = false }`.** Confirm on a
   device that backgrounding mid-load and returning shows the finished list rather than a stuck
   spinner — the old code cancelled the UI-side effect; the new code has to render the result that
   arrived while stopped.
6. **`ToggleAdapter`'s open state is view-only** — `translationX` on the ViewHolder, reset on every
   rebind. Moving it into UiState as `isMenuOpen` would be more correct (the row menu currently
   snaps shut on any list refresh) but it is a behaviour change; keep it view-only in this migration
   and note it for the Compose phase.
7. **`SubListAdapter.updateItems`'s survey-only branch** (`items.size == updated.size` → update
   `items[0].survey`) mutates the item in place. With immutable row models the survey vote result
   is a new list, which is correct but changes the notify pattern — cover with the append/replace
   logic from risk 3.
8. **Koin graph verification.** Every phase adds `viewModel { … }` lines; without a `koin-test`
   `verify()` a missing binding is a runtime crash at first injection. Phase 0 adds the test source
   set for exactly this reason.
9. **`zumpaData` has 8 readers.** Removing it from `ZumpaReaderApp` is a wide but mechanical change;
   do it in Phase 1 as its own commit so a bisect can isolate it.

## Working rules

* One phase = one commit, each verified with `./gradlew :app:assembleDebug`, the same discipline as
  the dependency upgrade.
* The app must build and run after every phase; screens not yet migrated keep using `ZumpaPrefs` /
  the repositories directly.
* No new `LiveData` anywhere. `StateFlow` for state, `SharedFlow`/`Channel` for one-shots.
* Nothing in a `*UiState` may be a mutable type or a `View`/`Context`/`Drawable`.
* The ViewModel never imports `android.view`, `android.widget`, or `R.layout`. `@StringRes`/
  `@DrawableRes` ints are allowed in state and effects.
