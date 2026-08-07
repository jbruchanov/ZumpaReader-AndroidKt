# ZumpaReader – Compose migration plan

Follow-up to `UPGRADE_PLAN.md` §D. Architecture as it stands: [`ARCHITECTURE.md`](ARCHITECTURE.md).

Every screen except `SettingsActivity` is already MVVM with an immutable `UiState`, so this is a
migration of the **render half only** — which is what the MVVM work was shaped for. `SettingsActivity`
is converted last, as Compose + MVVM in one step.

Like `MVVM_PLAN.md` before it, this document is expected to be folded into `ARCHITECTURE.md` and
deleted once the work lands.

## Decisions

| # | Decision | Consequence |
|---|---|---|
| 1 | **`ZumpaTheme` wraps Material 3.** | The ~30 custom attrs of `ThemeBlack` become `ZumpaColors`/`ZumpaDimens`/`ZumpaTypography` CompositionLocals; M3 underneath supplies `PullToRefreshBox`, `Scaffold`, `TextField`, ripples. M3 defaults that fight the look get overridden once, in the theme. |
| 2 | **Coil, and Fresco + Picasso are deleted at the end.** | The app currently ships two loaders. Ends with one. The offline disk prefetch (`OfflineDownloadUseCase`) and the cookie-aware downloader (`PicassoHttpDownloader2`) have to be rehosted on Coil/OkHttp — see §C9. |
| 3 | **Navigation goes through `LocalNavigator`.** | Keeps `Screen(uiState, eventHandler)` at two arguments and keeps the fragment free of screen-specific code. The host provides a `FragmentManager`-backed implementation now and a nav-compose one later. |
| 4 | **The thread screen's bottom pull is hand-written.** | Preserves the interaction exactly. It is the only reason `swipy` exists, and dropping swipy is what unblocks Jetifier → AGP 9 (`UPGRADE_PLAN.md` §B/§C). |

---

## Conventions

### The two Screen overloads

`Screen(uiState, eventHandler)` takes exactly two arguments, so it cannot also receive the
ViewModel, the effects flow or a navigation callback. Each screen is therefore the **same name
overloaded twice**, in one file:

```kotlin
// XyzScreen.kt

@Composable
fun XyzScreen(vm: XyzViewModel = koinViewModel()) {
    //nav wiring
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is XyzEffect.OpenThread -> navigator.openThread(effect.threadId)
                is ShowToast -> context.toast(effect)
                …
            }
        }
    }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val eventHandler = vm
    XyzScreen(uiState, eventHandler)
}

@Composable
private fun XyzScreen(uiState: XyzUiState, eventHandler: XyzEventHandler) { … }
```

Two notes on the shape:

* **`collectAsStateWithLifecycle()`, not `collectAsState()`.** The plain one keeps collecting while
  the screen is stopped; the lifecycle-aware one is what preserves the `repeatOnLifecycle(STARTED)`
  behaviour the MVVM migration deliberately introduced (`ARCHITECTURE.md` → "The shape").
* **The inner overload takes the unwrapped value**, via `by`, not `State<XyzUiState>`. Passing the
  `State` would defer the read one level further but forces every preview and fixture to wrap itself
  in `mutableStateOf`. Say the word if you want the deferred-read version instead.

Because the inner overload is `private`, **the previews live in the same file** — which is what you
want anyway: the screen, its parts and their previews next to each other.

The fragment or activity contains one line of content and no wiring:

```kotlin
override fun onCreateView(…) = zumpaContent { MainListScreen() }
```

`zumpaContent {}` is a shared helper (`ui/compose/Host.kt`) that installs a `ComposeView` with
`ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`, wraps in `ZumpaTheme`, and provides
`LocalNavigator`. When fragments go away, only `zumpaContent` changes.

### EventHandler

One interface per screen, **past tense**, implemented by the ViewModel directly:

```kotlin
interface MainListEventHandler {
    fun onRefreshRequested()
    fun onThreadClicked(threadId: String)
    fun onFavoriteClicked(threadId: String)
    fun onOfflineToggled()
    fun onEndReached()
}

class MainListViewModel(…) : BaseViewModel<MainListUiState>(…), MainListEventHandler
```

So `XyzScreen(uiState, viewModel)` just works, and the interface is the exact contract a preview has
to satisfy. The existing VM methods are present-tense (`onRefresh`, `onThreadClick`) — renaming them
is a mechanical step in each screen's phase.

### Previews and Fixtures

Every screen and every non-trivial component gets a `@Preview`. Trivial means a composable whose
preview would be a single widget with a literal — those can skip it.

When preview data is more than two lines it moves into the **`test` package**, which holds nothing
but preview support:

```kotlin
// test/Fixtures.kt — the namespace, one nested marker object per screen
object Fixtures {
    object MainList
    object SubList
    object Post
    …
}

// test/MainListFixtures.kt — the data, as extensions on the marker
fun Fixtures.MainList.uiState(rows: Int = 6) = MainListUiState(rows = List(rows) { row(it) }, …)
fun Fixtures.MainList.uiStateOffline() = uiState().copy(isOffline = true)
fun Fixtures.MainList.row(index: Int) = RenderedThreadRow(…)
```

One file per screen, so a screen's fixtures grow without touching anyone else's, and
`Fixtures.MainList.` autocompletes to exactly that screen's set.

**`com.scurab.android.zumpareader.test` is a package in `src/main`, not the `src/test` source set.**
It has to be: previews live in `main` and release builds compile them, so they cannot reference test
sources. The cost is that the fixtures ship in the APK — negligible here (`minifyEnabled = false`
already), and it is the same trade every Compose codebase with previews makes.

### `mock()`

Previews need an `EventHandler` that does nothing. No mockk, no test library in `main` — lives in
the same `test` package:

```kotlin
// test/Mock.kt
inline fun <reified T : Any> mock(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        defaultValue(method.returnType)   // 0 / false / null, and null for void
    } as T
```

Interfaces only, which is all an `EventHandler` ever is.

```kotlin
@Preview
@Composable
private fun MainListScreenPreview() = ZumpaTheme {
    MainListScreen(Fixtures.MainList.uiState(), mock())
}
```

### Navigator

```kotlin
interface Navigator {
    fun openThread(threadId: String)
    fun openImage(url: String)
    fun openLink(url: String)
    fun openSettings()
    fun openPostDialog(threadId: String? = null, flag: Int? = null)
    fun openOfflineDownload()
    fun back()
}
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator provided") }
```

`openImage` drops the shared-element transition, which **fixes bug E1** (`UPGRADE_PLAN.md` §E1 — the
image vanishing from the list) as a side effect rather than as a separate fix. If the transition is
wanted back later it returns as `SharedTransitionLayout`, which does not have the
recycled-view problem the View API has.

---

## Phase C0 — infrastructure

Dependencies (catalog): compose-bom, `ui`, `material3`, `ui-tooling-preview`, `activity-compose`,
`lifecycle-runtime-compose` (for `collectAsStateWithLifecycle`), `koin-androidx-compose`
(`koinViewModel()`), `coil-compose`; `debugImplementation ui-tooling`. Plus the
`org.jetbrains.kotlin.plugin.compose` Gradle plugin, version-matched to Kotlin 2.4.10, and
`buildFeatures { compose = true }`.

`ui/compose/`:

* `ZumpaTheme.kt` — `ZumpaColors`, `ZumpaDimens`, `ZumpaTypography` data classes + CompositionLocals,
  built from the values in `theme_black.xml` / `colors.xml` / `dimens.xml`, wrapping `MaterialTheme`
  with a dark `ColorScheme` (`primary` = `contextColor` #FFA710, `background` = black,
  `surface` = #202020, `onSurface` = white). One theme only — the app has never had a light mode.
  The drawable-reference attrs (`threadItemBackground`, `imageButtonBackground`, …) become
  `Modifier` extensions or `Brush`es rather than colours; the alternating row background is a
  `background(if (index % 2 == 0) …)`.
* `Host.kt` — `zumpaContent {}`, `FragmentNavigator`.
* `Navigator.kt` — the `Navigator` interface and `LocalNavigator`.

`test/` (a package in `src/main`, see Conventions): `Fixtures.kt` with one marker object per screen,
`Mock.kt`. Per-screen `XyzFixtures.kt` files arrive with their screen's phase.

Deliverable: a throwaway preview proving the theme renders, nothing wired into the app yet.

---

## Phase C1 — `ImageActivity`

Smallest screen, its own activity, no app chrome.

* `ImageUiState` already is `Loading` / `Loaded(bitmap)` / `Failed(url)` — unchanged, except
  `Loaded` should carry a Coil model (url) rather than a `Bitmap` once §C9 lands. Keep `Bitmap` for
  now; the VM already produces it.
* `ImageEventHandler`: nothing to handle — the screen is display-only, so it takes
  `ImageEventHandler` with a single `onCloseRequested()` for the back affordance.
* **`pinchtozoom` is replaced here**, by `Modifier.graphicsLayer` + `detectTransformGestures` with
  scale clamped to 1f..8f and pan bounded to the scaled image. ~40 lines, and it removes a
  Jetifier blocker.
* Composables: `ImageScreen`, `ZoomableImage` (preview-worthy — preview it with a solid-colour
  placeholder), `ImageFailed`.
* Previews: Loading, Loaded, Failed.

---

## Phase C2 — `OfflineDownloadFragment`

A dialog with a form and a progress read-out. Pure state, no lists, no images — the best place to
shake out the theme and the form controls.

* `OfflineDownloadUiState` is already exactly right (`pages`, `downloadImages`, counters,
  `isRunning`, `isDismissable`).
* `OfflineDownloadEventHandler`: `onPagesChanged(String)`, `onDownloadImagesToggled(Boolean)`,
  `onStartClicked()`, `onStopClicked()`.
* Stays a `DialogFragment` hosting `zumpaContent { OfflineDownloadScreen() }`; the back-swallowing
  while running moves to `BackHandler(enabled = !uiState.isDismissable)` inside the screen.
* Composables: `OfflineDownloadScreen`, `ProgressRow` (threads / images counters).
* Previews: idle, running mid-download, finished.

---

## Phase C3 — `PostMessageFragment` and `PostImageFragment`

Both are tab bodies inside `PostFragment`; converting them first means C4 only has to deal with the
tab host.

**PostMessage** — subject field, message field, send/camera/photo buttons.

* Both fields are driven by `PostUiState.subject` / `.message`; in Compose use
  `TextFieldValue`-free `OutlinedTextField(value = uiState.subject, onValueChange = handler::onSubjectChanged)`,
  which removes the `isSettingText` re-entrancy guard the View version needs.
* `PostMessageEventHandler`: `onSubjectChanged`, `onMessageChanged`, `onSendClicked`,
  `onCameraClicked`, `onPhotoClicked`.
* Composables: `PostMessageScreen`, `PostActionsRow`. Previews: new thread (subject editable),
  reply (subject fixed), sending.

**PostImage** — thumbnail, size/rotation panel, upload.

* `PostImageUiState` is already complete. The `SendingDialogController` for `isBusy` becomes an
  overlay inside the screen.
* `PostImageEventHandler`: `onSampleSizeSelected(Int)`, `onResizeClicked()`, `onRotateClicked()`,
  `onUploadClicked()`, `onCopyLinkClicked()`.
* The spinner becomes an M3 `ExposedDropdownMenuBox`; `imagePanel.setImageSize` formatting
  (`toReadableSize`, `toResolution`) moves out of `PostImagePanelView` into pure functions the
  composable calls — and gets unit tests, since it is the only real logic in that widget.
* Composables: `PostImageScreen`, `ImagePanel`, `ImageMetaRow`. Previews: fresh, resized, uploaded.

---

## Phase C4 — `PostFragment`

* `FragmentTabHost` → M3 `PrimaryTabRow` + `HorizontalPager`. This deletes the child-fragment
  machinery entirely: `PostMessageScreen` and `PostImageScreen` become pager pages, so
  `addedTabTags`, `syncTabs` and the `FragmentTabHost` "only ever grows" workaround all go.
* `PostImageViewModel` is per-image today (one per child fragment). As pager pages there is no
  fragment to scope it to — either key it by tab tag with `koinViewModel(key = tab.tag)`, or fold
  the per-image state into a `Map<String, PostImageUiState>` in `PostViewModel`. **Recommend the
  keyed ViewModel**: it keeps the state machine per image and untouched.
* `PostEventHandler`: `onTabSelected(String)`, `onSendClicked()`, plus the message/image handlers
  delegated to the page.
* Camera/gallery pickers become `rememberLauncherForActivityResult` in the ViewModel-taking `PostScreen` overload.
* Composables: `PostScreen`, `PostTabRow`. Previews: message tab only, message + two images.

---

## Phase C5 — `MainListFragment`

First screen with a list and with app chrome.

* `Scaffold` with `TopAppBar` (title from `uiState.isOffline`, overflow menu with settings + offline
  toggle) and a `FloatingActionButton`. **The chrome moves into the screen**, which is where
  nav-compose will want it. `MainActivity`'s View toolbar/FAB/progress stay for the not-yet-migrated
  screens and are deleted in C7.
* `LazyColumn(items(uiState.rows, key = { it.id }))`. `RenderedThreadRow` already carries exactly
  what a row draws.
* The swipe-to-reveal row menu (`ToggleAdapter`'s `translationX`) becomes `SwipeToDismissBox` or a
  small `anchoredDraggable` row. **This is where the open state finally belongs in `UiState`** —
  the `isMenuOpen` item noted in `ARCHITECTURE.md` as a deliberate exception.
* Paging: `OnShowItemListener` (15 from the end) becomes a `LaunchedEffect` on
  `listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index`.
* Pull-to-refresh: M3 `PullToRefreshBox`, top direction — the straightforward case.
* `MainListEventHandler`: `onRefreshRequested`, `onEndReached`, `onThreadClicked`,
  `onThreadLongPressed`, `onFavoriteClicked`, `onIgnoreClicked`, `onShareClicked`,
  `onOfflineToggled`, `onSettingsClicked`, `onFabClicked`.
* Composables: `MainListScreen`, `ThreadRow`, `ThreadRowMenu`, `ThreadStateBar` (the
  `LevelListDrawable` becomes a `Box` with a colour from `ThreadState` — the ordinal-matching test
  from MVVM phase 3 can then be deleted).
* Previews: populated, empty, offline, loading, and `ThreadRow` in each `ThreadState`.

---

## Phase C6 — `SubListFragment`

The big one, as in the MVVM migration.

* `LazyColumn` over `List<RenderedSubListRow>` with `contentType` per row type — the flattening is
  already done in the ViewModel, so this is a direct translation of the four cases.
* **The bottom pull is hand-written** (decision 4): a `nestedScroll` connection that accumulates
  overscroll past the last item, exposes a progress fraction for a custom indicator, and calls
  `onRefreshRequested()` past a threshold. Budget real time for tuning fling/settle behaviour;
  it is the one piece here with no library behind it. Keep the top pull as `PullToRefreshBox`.
* The reply panel becomes a `BottomSheetScaffold` or a plain animated `Column` pinned to the bottom;
  `updateRecycleViewPadding` and its `OnGlobalLayoutListener` disappear — `imePadding()` and
  `Scaffold` insets replace all of it.
* `DraftUiState` is already headers + body, so the reply text field binds directly and the
  `AuthorSpan` re-application in `renderDraft` goes away: the headers are rendered as styled
  `AnnotatedString` spans by the renderer instead of being re-applied to an `Editable`.
* `SubListEventHandler`: `onRefreshRequested`, `onMessageClicked`, `onMessageLongPressed`,
  `onReplyClicked`, `onQuoteClicked`, `onCopyClicked`, `onDraftChanged`, `onSendClicked`,
  `onSurveyItemClicked`, `onLinkClicked`, `onImageClicked`, `onBackPressed`.
* Composables: `SubListScreen`, `MessageRow`, `LinkRow`, `ImageRow`, `SurveyCard`, `SurveyOption`,
  `ReplyPanel`, `BottomPullIndicator` — all preview-worthy; `SurveyCard` and `MessageRow` especially.
* Previews: thread with mixed rows, survey at top, reply panel open, sending.

---

## Phase C7 — `TabletFragment` and the `MainActivity` shell

* `TabletFragment` becomes a `Row { MainListScreen(…); SubListScreen(…) }` behind
  `DeviceConfig.isTablet` — or better, a `TwoPaneScreen` composable so the split is one place.
* `MainActivity` loses the toolbar, progress bar, FAB and `CoordinatorLayout`; every screen now
  brings its own `Scaffold`. `MainViewModel`/`MainUiState` shrink to what is genuinely global (the
  launch payload routing) or disappear entirely.
* `QuickHideBehavior` / `ScrollAppBarFABBehavior` are replaced by M3's
  `TopAppBarScrollBehavior` — two custom `CoordinatorLayout.Behavior` classes deleted.

---

## Phase C8 — `SettingsActivity` (last)

The only screen that is still MVC, converted straight to Compose + MVVM as you asked.

* `PreferenceActivity` → `ComponentActivity` + `setContent`. `res/xml/settings.xml` and
  `ButtonPreference` are replaced by composables; **`androidx.preference` is never introduced**,
  which is the whole reason this was deferred.
* New `SettingsUiState`: `userName`, `password`, `isLoggedIn`, `nickName`, `filter`,
  `showLastAuthor`, `loadImages`, `areNotificationsEnabled`, `userId`, `isBusy`.
* New `AuthRepository` — the ~90 lines of login / logout / push-registration currently inline in the
  activity, including the `FirebaseMessaging.token` await and the `[OK]` response parsing. This is
  the piece that makes the screen testable and is worth more than the UI conversion.
* `SettingsEventHandler`: `onUserNameChanged`, `onPasswordChanged`, `onLoginClicked`,
  `onLogoutClicked`, `onFilterChanged`, `onShowLastAuthorToggled`, `onLoadImagesToggled`,
  `onNotificationsClicked`, `onUserIdClicked`.
* `ZumpaPrefs` stays as the storage; `ZumpaSettingsRepository` gains writers for what only this
  screen sets, so nothing writes to `ZumpaPrefs` directly any more.
* The `onPause` block that re-primes `ZumpaSimpleParser` and the cookie jar moves into
  `AuthRepository`, next to the login that invalidates them.
* Composables: `SettingsScreen`, `CredentialsSection`, `PreferenceSwitchRow`, `PreferenceClickRow`,
  `FilterSelector`. Previews: logged out, logged in, busy.

---

## Phase C9 — cleanup, and the toolchain it unblocks

1. **Coil-only.** Delete Fresco and Picasso. Two things must move first:
   `OfflineDownloadUseCase`'s `prefetchToDiskCache` → Coil's `ImageLoader.enqueue` with
   `MemoryCachePolicy.DISABLED`, and `PicassoHttpDownloader2`'s cookie handling → the shared
   `OkHttpClient` in Coil's `ImageLoader` (which already has the cookie jar, so this mostly deletes
   code).
2. **Delete `swipy` and `pinchtozoom`** — replaced in C6 and C1.
3. That empties `UPGRADE_PLAN.md` §B, so **`android.enableJetifier` comes out**, and §C unblocks:
   AGP 9, Gradle 9, core-ktx 1.19, compileSdk 37.
4. **nav-compose**: fragments and `zumpaContent` are replaced by a `NavHost`; `LocalNavigator` gets
   its second implementation and the `FragmentNavigator` is deleted. `BaseFragment`,
   `BaseDialogFragment`, `MainActivity.openFragment` and the `OnBackPressedCallback` plumbing go
   with them.
5. Fold this document into `ARCHITECTURE.md` and delete it.

---

## Risks

0. **There is a dependency ceiling, and it is circular.** Found while doing C0: the
   `lifecycle-runtime-compose` / `lifecycle-viewmodel-compose` artifacts of 2.11 require **AGP 9.1
   and compileSdk 37**. Getting there means dropping Jetifier, which means removing `swipy` and
   `pinchtozoom` — which is C9, the *last* step of this migration. So the whole Compose migration
   runs one androidx release behind, and `app/build.gradle` holds those two artifacts at 2.10.0 with
   a `resolutionStrategy.force`. Expect the same for other Compose-era androidx libraries added
   later (nav-compose especially). The force comes out in C9 together with Jetifier, and that is the
   moment to move everything forward at once. Note this does *not* affect `compose-bom` itself —
   2026.06.01 is fine on AGP 8.13.
1. **The bottom pull (C6)** is the only bespoke gesture work in the plan and the only piece with no
   library to fall back on. If it fights the `LazyColumn` fling, the escape hatch is the "auto-load
   at the end + top pull" option that was on the table.
2. **Theme fidelity.** M3 will impose its own ripples, elevation tints and typography. Expect to
   override `LocalRippleConfiguration`, surface tint and `Typography` in `ZumpaTheme` to keep the
   flat black look, and check the yellow accent against M3's tonal defaults early — C2 exists partly
   to catch this while the surface area is small.
3. **`AnnotatedString` vs `ImageSpan`.** `AnnotatedTextRenderer` must reproduce inline smileys with
   `InlineTextContent`, which is a different mechanism from `ImageSpan` and needs the ids threaded
   through. Do it in C6 where it is exercised hardest, not as an afterthought.
4. **Two ViewModel scoping models coexist.** Fragment-scoped `by viewModel()` and Compose
   `koinViewModel()` resolve to different stores if both are used for one screen. Each screen must
   pick one at conversion time — the `Screen(vm)` overload owns the ViewModel, the fragment must not
   also inject it.
5. **Previews need the app's fonts and drawables**, and `@Preview` renders without a real `Context`
   for some of them. Anything reading a `Drawable` resource in a preview path needs a
   `LocalInspectionMode` fallback.
6. **Coil and the offline cache (C9)** is the one step that can silently break a feature that is
   hard to test — offline mode with images. Verify it against a real offline download before
   deleting Fresco.

## Working rules

* One phase = one commit, each verified with `./gradlew :app:assembleDebug` plus the unit tests.
* The app builds and runs after every phase; unconverted screens keep their Views.
* No screen-specific code in a fragment or activity — only `zumpaContent { XyzScreen() }`.
* Every `Screen` is `(uiState, eventHandler)` and previewable with `mock()`.
* ViewModels keep their existing `UiState`/effects contracts; if a Compose conversion wants a
  different shape, change the state — never move logic back into the UI.
