# ZumpaReader – architecture

How the app is built after the MVVM, Compose and Navigation-3 migrations. This is the reference;
`UPGRADE_PLAN.md` is the history and `KMP_PLAN.md` covers the multiplatform split.

**Three modules.**

| Module | What it is |
|---|---|
| `:shared` | Kotlin Multiplatform library — the parser, model, Ktor layer, repositories and use cases. Targets `androidTarget()` and `jvm()`. |
| `:appAndroid` | The Android app: `androidx.compose`, navigation-3, Firebase, and the platform halves of the seams `:shared` declares (`KeyValueStore`, `PushTokenProvider`, `ImagePrefetcher`). |
| `:appJvm` | A desktop app on Compose Multiplatform. `./gradlew :appJvm:run`. |

The two apps **do not share UI**: `:appAndroid` is on `androidx.compose` and `:appJvm` on Compose
Multiplatform, so each has its own screens. Merging them is phase 3 in `KMP_PLAN.md`.

The `jvm()` target of `:shared` ships nothing on its own — it exists so an Android import leaking
into `commonMain` breaks the build. `:appJvm` is what actually runs that code. iOS targets go in the
same list later.

**One activity, no fragments, no XML layouts.** Every screen is Compose + MVVM + Koin. Navigation is
`androidx.navigation3` — the back stack is an observable list of keys.

## The shape

Every screen owns a `ScreenNameUiState` data class holding **the whole** visible state of that
screen. The composable subscribes to one `StateFlow<ScreenNameUiState>` and renders it; it never
reads state back out of a widget. One-shot things — navigation, toasts, scrolling, clipboard,
keyboard — are not state, they go through a separate effects `Flow`, so a configuration change
cannot replay them.

```
repository (StateFlow, suspend)  →  ViewModel (UiState + effects)  →  Screen (render)
                                          ↑ events (onXClicked, onRefreshRequested, …)
```

`arch/BaseViewModel<S>` is the whole contract:

```kotlin
val uiState: StateFlow<S>          // render this
val effects: Flow<UiEffect>        // react to this once
protected fun setState(reducer: S.() -> S)
protected fun effect(effect: UiEffect)
protected fun onError(err: Throwable)   // every screen did the same thing with a failed call
```

Screens collect with `collectAsStateWithLifecycle()`, not `collectAsState()`. The plain one keeps
collecting while the screen is stopped; the lifecycle-aware one preserves the
`repeatOnLifecycle(STARTED)` behaviour the MVVM migration introduced. Work lives in `viewModelScope`
and keeps running when the screen stops; only the *collection* pauses. That is why no screen needs
the old `onPause { isLoading = false }` workaround.

## Packages

```
com.scurab.android.zumpareader
├── arch/         BaseViewModel, UiEffect, DeviceConfig
├── repository/   the single owners of shared state, + AppEventBus, SelectedThreadStore
├── text/         AnnotatedTextRenderer - markup to AnnotatedString
├── usecase/      OfflineDownloadUseCase, CreateNotificationChannelsUseCase
├── data/ model/ util/ ext/   retrofit converters, interceptors, parser output, helpers
├── test/         preview fixtures and mock() — in src/main on purpose, see Previews
└── ui/
    ├── compose/  Navigator, AppTheme, ImageLoader, TextRenderer, RowBackground
    ├── nav/      ZumpaKey, ZumpaNavHost, BackStackNavigator   ← the whole graph
    ├── main/     MainActivity + MainViewModel                 (window + Intent routing only)
    ├── mainlist/ MainListScreen  + MainListViewModel
    ├── sublist/  SubListScreen   + SubListViewModel + BottomPullToRefresh
    ├── post/     PostScreen + PostMessageScreen + PostImageScreen
    │   └── tasks/    + PostViewModel + PostImageViewModel
    ├── offline/  OfflineDownloadScreen + OfflineDownloadViewModel
    ├── image/    ImageScreen     + ImageViewModel
    ├── tablet/   TwoPaneScreen (list + detail side by side, no ViewModel)
    └── settings/ SettingsScreen  + SettingsViewModel
```

One package per screen: its ViewModel, ui state, effects, event handler and composables. Test
sources mirror it (`ui/sublist/SubListViewModelTest`, …).

## Navigation

`ui/nav/` is the entire graph. There is one activity, `MainActivity`, and it does two things: own
the window, and turn Intents into a `LaunchPayload`. Everything else is `ZumpaNavHost`.

```kotlin
@Serializable data object MainListKey : ZumpaKey
@Serializable data class  SubListKey(val threadId: String) : ZumpaKey
@Serializable data class  ImageKey(val url: String) : ZumpaKey
…

NavDisplay(
    backStack = rememberNavBackStack(if (device.isTablet) TwoPaneKey else MainListKey),
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    sceneStrategies = listOf(DialogSceneStrategy()),
    entryProvider = entryProvider { entry<SubListKey> { SubListScreen(it.threadId) } … },
)
```

Three things to know:

* **A key carries the screen's arguments** — what used to be fragment arguments and intent extras.
  They are `@Serializable` because `rememberNavBackStack` persists the whole stack through process
  death, which is what replaces the FragmentManager's saved state. Uris travel as `String`s;
  `android.net.Uri` is not serializable and the screen only hands them back to the platform anyway.
* **`rememberViewModelStoreNavEntryDecorator()` is what makes `koinViewModel()` correct.** It scopes
  the entry's content to its own `ViewModelStore`, so a screen's ViewModel dies when its entry is
  popped instead of living as long as the activity. It requires the saveable-state-holder decorator
  in front of it, hence the order.
* **Dialogs are entries too**, selected by `DialogSceneStrategy` off entry metadata. The offline
  download dialog is always one; the post screen is a dialog on a tablet and a full screen on a
  phone, which is what it always was. `dismissOnClickOutside = false` — both screens decide for
  themselves when they may close, and their own `BackHandler` sits inside the dialog and wins over
  it.

### Navigator

No screen names a destination class. `ui/compose/Navigator.kt` is the seam:

```kotlin
interface Navigator {
    fun openThread(threadId: String); fun openImage(url: String); fun openLink(url: String)
    fun openSettings(); fun openPostDialog(threadId: String? = null, picker: PostPicker? = null)
    fun openOfflineDownload(); fun back()
}
val LocalNavigator = staticCompositionLocalOf<Navigator> { … }
```

`ui/nav/BackStackNavigator` implements it over the back stack — navigating is `backStack.add(key)`
and going back is removing the last one, except at the root where it hands over to the activity.
This interface existed before nav-3 did, backed by the FragmentManager; **swapping the
implementation changed no screen at all**, which was the point of having it.

`openImage` drops the shared-element transition, which is what fixed bug E1 (`UPGRADE_PLAN.md` §E1
— the image vanishing from the list). If the transition is wanted back it returns as `NavDisplay`'s
`sharedTransitionScope`, which has no recycled-view problem.

Tablet routing never goes through the navigator: `MainListViewModel.onThreadClicked` writes to
`SelectedThreadStore` when `DeviceConfig.isTablet`, and the detail pane's ViewModel collects it. One
code path, both form factors.

## Compose conventions

### The two Screen overloads

`Screen(uiState, eventHandler)` takes exactly two arguments, so it cannot also receive the
ViewModel, the effects flow or a navigation callback. Each screen is therefore the **same name
overloaded twice**, in one file:

```kotlin
@Composable
fun XyzScreen(vm: XyzViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is XyzEffect.OpenThread -> navigator.openThread(effect.threadId)
                is ShowToast -> context.toast(effect)
                else -> Unit
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

The inner overload takes the **unwrapped value**, via `by`, not `State<XyzUiState>` — passing the
`State` would defer the read one level further but forces every preview and fixture to wrap itself
in `mutableStateOf`. Because it is `private`, the previews live in the same file, which is where you
want them anyway.

Everything a screen needs from the platform is inside the outer overload — permission launchers,
file pickers, clipboard. There is no host left to put it in.

### EventHandler

One interface per screen, **past tense**, implemented by the ViewModel directly:

```kotlin
interface MainListEventHandler {
    fun onRefreshRequested()
    fun onThreadClicked(threadId: String)
    fun onOfflineToggled()
    fun onEndReached()
}

class MainListViewModel(…) : BaseViewModel<MainListUiState>(…), MainListEventHandler
```

So `XyzScreen(uiState, viewModel)` just works, and the interface is the exact contract a preview has
to satisfy.

### Previews and fixtures

Every screen and every non-trivial component gets a `@Preview`. Preview data longer than two lines
moves into the `test` package:

```kotlin
// test/Fixtures.kt — the namespace, one nested marker object per screen
object Fixtures { object MainList; object SubList; object Post; … }

// test/MainListFixtures.kt — the data, as extensions on the marker
fun Fixtures.MainList.uiState(rows: Int = 6) = MainListUiState(…)
fun Fixtures.MainList.uiStateOffline() = uiState().copy(isOffline = true)
```

**`com.scurab.android.zumpareader.test` is a package in `src/main`, not the `src/test` source set.**
It has to be: previews live in `main` and release builds compile them. The cost is that the fixtures
ship in the APK — negligible here, and the same trade every Compose codebase with previews makes.

`test/Mock.kt`'s `mock<T>()` is a `java.lang.reflect.Proxy` returning zero values — no mockk, no
test library in `main`. Interfaces only, which is all an `EventHandler` ever is.

```kotlin
@Preview
@Composable
private fun MainListScreenPreview() = AppTheme {
    MainListScreen(Fixtures.MainList.uiState(), mock())
}
```

### AppTheme

`ui/compose/theme/` mirrors Material's own shape: `AppTheme { … }` installs the CompositionLocals,
and `object AppTheme` exposes them through `@Composable @ReadOnlyComposable` getters —
`AppTheme.colorScheme`, `.typography`, `.shapes`, `.sizes`, `.spaces`. The values are the ones that
were in `res/values/*.xml`; Material 3 is wrapped, not replaced, so `Scaffold`, `TextField` and the
rest still work.

`Modifier.zumpaRowBackground(index, isSelected, interactionSource)` is the translation of
`item_list_background_theme_black.xml` — a level-list keyed on `position % 2`, each level a selector
with default/pressed/selected states. It is why both lists use `itemsIndexed`.

## Repositories

`repository/` holds the single owners of state that outlives a screen. Nothing else may hold it —
before the migration this was two `TreeMap`s on the `Application` that eight call sites reached into.

| | |
|---|---|
| `ZumpaThreadRepository` | the loaded threads; the **only** thing that talks to `ZumpaAPI`, so `retrying {}` and `ignoringZumpaRedirect {}` are written once |
| `ZumpaSettingsRepository` | `ZumpaPrefs` as `StateFlow`s, off a `SharedPreferences` listener |
| `ZumpaReadStateRepository` | how much of each thread has been seen, plus its persistence |
| `OfflineDataRepository` | the offline snapshot on disk and the api that serves it |
| `AuthRepository` | login, logout, push registration, re-priming the parser and cookie jar |
| `CookieRepository` | what is in the cookie jar, and throwing away what the forum accumulated in it |
| `ImageCacheRepository` / `ImageUploadRepository` | Coil cache reads, fotodisk uploads |

### ⚠ The one rule that will bite you

`ZumpaAPI` is a Koin **`factory`** because online/offline is a runtime setting. Anything that
injects it *once* freezes that choice forever. Only `ZumpaThreadRepositoryImpl` may resolve it, and
only per call, through a provider lambda:

```kotlin
single<ZumpaThreadRepository> { ZumpaThreadRepositoryImpl(api = { get() }) }
```

A ViewModel must inject the repository, never `ZumpaAPI`. This fails at runtime, not at compile
time — `ZumpaThreadRepositoryTest` pins it.

### Cross-screen signalling

otto's two uses were split by what they actually were:

* **`SelectedThreadStore`** — a `StateFlow<String?>`, which thread the tablet's detail pane shows.
  It was never an event; a re-subscribing screen can now tell what is selected. On a phone nothing
  writes to it, so one code path covers both form factors.
* **`AppEventBus`** — a `SharedFlow<AppEvent>` for genuine one-shots (`OfflineDataChanged`,
  `ContentPosted`). Anything a screen needs the *current value* of belongs on a repository instead.

## Talking to the forum

The forum is a legacy PHP site: **ISO-8859-2**, a 302 for a successful post, and a cookie jar it
never prunes. Three pieces deal with that, and each is the only place that does.

* **`util/HttpEncoding.kt`** — `String.encodeHttp()`. `URLEncoder` turns anything the charset cannot
  represent into a literal `?`, which is how emoji used to disappear. A browser substitutes an HTML
  numeric character reference (`&#128512;`) instead, and so does this, iterating by code point so a
  surrogate pair becomes one reference rather than two broken halves.
* **`CookieRepository` + `data/OversizedCookieInterceptor`** — `java.net.CookieManager` accumulates
  every `Set-Cookie` and expires nothing, so the header eventually outgrows what the backend accepts
  and it answers 502 to everything. On a 502 the jar is dropped, rebuilt from the login cookies in
  `ZumpaPrefs` and the request re-sent once; the user stays logged in. It is an **application**
  interceptor because okhttp's `BridgeInterceptor` writes the `Cookie` header below those.
* **`util/Calls.kt`** — `ignoringZumpaRedirect {}` (a 302 is a successful post) and `retrying {}`.
  Only `ZumpaThreadRepository` uses them, so they are written once.

## Text rendering

UiState carries **raw markup strings**; the screen renders them with `AnnotatedTextRenderer`
(→ `AnnotatedString` + `InlineTextContent` for the smileys), built from theme colours by
`rememberAnnotatedTextRenderer()`. It owns the `LruCache`, which is why the model classes carry no
`styledBody`/`styledAuthor` fields.

It is a port of `ZumpaSimpleParser.parseBody`, which is gone along with the `ZumpaTextRenderer<T>`
interface and its `Spanned` implementation. That is what made rendering theme-free: `parseBody`
resolved colours off a **themed Context**, which is why rendering could never live in a ViewModel.

## Images

Coil 3 is the only image loader, built once in `ui/compose/ImageLoader.kt` over the app's
`OkHttpClient` so cookies, timeouts and logging are shared with the API. Fresco and Picasso are
gone.

`painterResource` accepts vectors and rasters only — **not** a `<bitmap>` XML wrapper. Eleven icons
were such wrappers applying `?colorAccent`; they now point at the underlying PNG and let
`Icon(tint = …)` do the colouring. This is invisible to the build, to lint and to previews, and
crashes at first composition.

## Rules

* `StateFlow` for state, `SharedFlow`/`Channel` for one-shots. **No `LiveData`** — the codebase has
  never had any.
* Nothing in a `*UiState` may be mutable, or a `View`/`Context`/`Drawable`. `@StringRes` /
  `@DrawableRes` ints are fine.
* A ViewModel never imports `android.view`, `android.widget` or `R.layout`.
* A ViewModel injects repositories, never `ZumpaAPI` (see above).
* Navigation, toasts and scrolling are effects, not state.
* A screen never names a destination — it calls `LocalNavigator.current`.
* Never read `StateFlow.value` in composition; collect it. Lint catches this one.
* Every new `viewModel { … }` line must keep `ModulesTest` green — it verifies the graph at build
  time instead of at first injection.

## Deliberate exceptions

* **`ui/compose/Navigator.kt` stays outside `ui/nav/`.** Screens depend on the interface, only the
  host depends on the graph; keeping them apart is what stops a screen importing a `ZumpaKey`.
* **`SurveyUiState`/`SurveyItemUiState` live in `ui.sublist`** even though the survey composable is
  a component — they are the sub-list's state.
* **`MainViewModel` has `data object MainUiState`.** The host has nothing to draw since every screen
  brings its own `Scaffold`; all that is left is the routing decision for the launch Intent, which
  is kept in a ViewModel because that is the part worth testing.
* **The `test` package ships in the APK.** See Previews above.

## Testing

`app/src/test` — junit5, mockk, turbine, koin-test. 82 tests: the ViewModels' state machines, the
repository's offline switching, the text renderer, the form encoding, the cookie interceptor, and
`ModulesTest` verifying the Koin graph.

ViewModel tests set `Dispatchers.setMain(UnconfinedTestDispatcher())` and assert on
`uiState.value` / `effects` via turbine. Anything reaching `android.net.Uri` is a stub in a JVM
test — that is why url classification uses `looksLikeImageUrl()` rather than `isImageUri()`, and the
ViewModel and the link routing must keep sharing it.

`ModulesTest` can only verify `viewModelModule`: Koin's `verify()` reflects over constructors and
cannot analyse factory-function definitions. Each new binding a ViewModel needs must be added to
`extraTypes` — it has caught a missing binding four times.

`ZumpaSimpleParser` has **no tests**, and it is the component most likely to break when the forum's
HTML changes.
