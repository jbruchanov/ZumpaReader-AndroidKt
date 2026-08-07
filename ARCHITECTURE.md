# ZumpaReader – architecture

How the app is built after the MVVM migration. This is the reference; `UPGRADE_PLAN.md` is the
history and the remaining work.

DI (Koin) and MVVM are in place for every screen **except `SettingsActivity`**, which is still MVC
on the framework `PreferenceActivity` on purpose — see [Deliberate exceptions](#deliberate-exceptions).

## The shape

Every screen owns a `ScreenNameUiState` data class holding **the whole** visible state of that
screen. The view subscribes to one `StateFlow<ScreenNameUiState>` and renders it; it never reads
state back out of a widget. One-shot things — navigation, toasts, scrolling, clipboard, keyboard —
are not state, they go through a separate effects `Flow`, so a configuration change cannot replay
them.

```
repository (StateFlow, suspend)  →  ViewModel (UiState + effects)  →  Fragment/Activity (render)
                                          ↑ intents (onXClick, onRefresh, …)
```

`arch/BaseViewModel<S>` is the whole contract:

```kotlin
val uiState: StateFlow<S>          // render this
val effects: Flow<UiEffect>        // react to this once
protected fun setState(reducer: S.() -> S)
protected fun effect(effect: UiEffect)
protected fun onError(err: Throwable)   // every screen did the same thing with a failed call
```

Fragments collect with `collectWhileStarted` (`arch/LifecycleExt.kt`), which is
`repeatOnLifecycle(STARTED)`. Work lives in `viewModelScope` and keeps running when the screen
stops; only the *collection* pauses. That is why no screen needs the old
`onPause { isLoading = false }` workaround.

## Packages

```
com.scurab.android.zumpareader
├── arch/         BaseViewModel, UiEffect, collectWhileStarted, DeviceConfig
├── repository/   the single owners of shared state, + AppEventBus, SelectedThreadStore
├── text/         ZumpaTextRenderer<T> and its Spanned implementation
├── usecase/      OfflineDownloadUseCase, CreateNotificationChannelsUseCase
├── data/ model/ util/ ext/   retrofit converters, parser output, helpers
├── widget/       custom views and view holders shared across screens
└── ui/           BaseFragment, BaseDialogFragment, SendingDialogController, view helpers
    ├── main/     MainActivity      + MainViewModel        (app chrome: progress, fab)
    ├── mainlist/ MainListFragment  + MainListViewModel    + adapter + render
    ├── sublist/  SubListFragment   + SubListViewModel     + adapter + render
    ├── post/     PostFragment + PostMessageFragment + PostImageFragment
    │   └── tasks/    + PostViewModel + PostImageViewModel
    ├── offline/  OfflineDownloadFragment + OfflineDownloadViewModel
    ├── image/    ImageActivity     + ImageViewModel
    ├── tablet/   TabletFragment (two-pane container, no ViewModel)
    └── settings/ SettingsActivity (still MVC)
```

One package per screen: its ViewModel, ui state, effects, fragment/activity, adapter and renderer.
Test sources mirror it (`ui/sublist/SubListViewModelTest`, …).

## Repositories

`repository/` holds the single owners of state that outlives a screen. Nothing else may hold it —
before the migration this was two `TreeMap`s on the `Application` that eight call sites reached into.

| | |
|---|---|
| `ZumpaThreadRepository` | the loaded threads; the **only** thing that talks to `ZumpaAPI`, so `retrying {}` and `ignoringZumpaRedirect {}` are written once |
| `ZumpaSettingsRepository` | `ZumpaPrefs` as `StateFlow`s, off a `SharedPreferences` listener |
| `ZumpaReadStateRepository` | how much of each thread has been seen, plus its persistence |
| `OfflineDataRepository` | the offline snapshot on disk and the api that serves it |
| `ImageCacheRepository` / `ImageUploadRepository` | fresco pipeline reads, fotodisk uploads |

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
  It was never an event; a re-subscribing fragment can now tell what is selected. On a phone nothing
  writes to it, so one code path covers both form factors.
* **`AppEventBus`** — a `SharedFlow<AppEvent>` for genuine one-shots (`OfflineDataChanged`,
  `ContentPosted`). Anything a screen needs the *current value* of belongs on a repository instead.

## Text rendering

`ZumpaSimpleParser.parseBody` builds `ImageSpan`s and resolves `R.attr.contextColorText2` off the
**theme**, so it needs a themed `Context` — an application context resolves the wrong attribute.
That is why rendering cannot live in a ViewModel.

`text/ZumpaTextRenderer<T>` is the single seam. UiState carries **raw markup strings**; the fragment
maps state → rendered state on `Dispatchers.Default` and hands that to the adapter:

```kotlin
viewModel.uiState.map { it.rows }.distinctUntilChanged()
    .map { render.rows(it) }
    .flowOn(Dispatchers.Default)
    .collectWhileStarted { adapter.setItems(it) }
```

`SpannedTextRenderer` (→ `CharSequence`) is the implementation today; the Compose phase adds
`AnnotatedTextRenderer` (→ `AnnotatedString`) and changes nothing else. The renderer owns the
`LruCache`, which is why the model classes no longer carry `styledBody`/`styledAuthor` fields.

## Adapters

Adapters own no data. `setItems(wholeList)` per emission; merging, sorting, decorating and
flattening a message into its message/link/image/survey rows all happen in the ViewModel — that is
list shaping, not view work, and it is what makes the eventual `LazyColumn` a direct translation.

`setItems` still detects the two cases where a blanket `notifyDataSetChanged` would be visible: a
pure append (new answers arriving while you read a thread) and a single removal (ignoring a thread).
Everything else is a full rebind, as it always was.

## Rules

* `StateFlow` for state, `SharedFlow`/`Channel` for one-shots. **No `LiveData`** — the codebase has
  never had any.
* Nothing in a `*UiState` may be mutable, or a `View`/`Context`/`Drawable`. `@StringRes` /
  `@DrawableRes` ints are fine.
* A ViewModel never imports `android.view`, `android.widget` or `R.layout`.
* A ViewModel injects repositories, never `ZumpaAPI` (see above).
* Navigation, toasts and scrolling are effects, not state.
* Every new `viewModel { … }` line must keep `ModulesTest` green — it verifies the graph at build
  time instead of at first injection.

## Deliberate exceptions

* **`SettingsActivity` is still MVC.** It extends the framework `android.preference.PreferenceActivity`,
  which has no `ViewModelStore` at all. Migrating it to `androidx.preference` only to migrate it
  again to Compose is wasted work, so it is the *first* screen of the Compose phase and gets
  Compose + MVVM in one step. Until then it talks to `ZumpaPrefs` directly, and the settings flows
  pick those writes up.
* **`ZumpaItemViewHolder` lives in `widget/`**, not a screen package — both list adapters extend it.
* **`SurveyUiState`/`SurveyItemUiState` live in `ui.sublist`** even though `widget.SurveyView` binds
  them. They are the sub-list's state, and the widget already depended on those types.
* **`ToggleAdapter`'s open state is view-only** (`translationX` on the view holder, reset on rebind).
  Moving it into UiState would be more correct — the row menu snaps shut on any refresh — but it is
  a behaviour change, so it is left for the Compose phase.
* **`ProgressDialog`** is deprecated but kept behind `SendingDialogController`, driven by
  `isSending` in each screen's state. Replacing it with an inline overlay is a Compose-phase job.

## Testing

`app/src/test` — junit5, mockk, turbine, koin-test. 56 tests: the ViewModels' state machines, the
repository's offline switching, and `ModulesTest` verifying the Koin graph.

ViewModel tests set `Dispatchers.setMain(UnconfinedTestDispatcher())` and assert on
`uiState.value` / `effects` via turbine. Anything reaching `android.net.Uri` is a stub in a JVM
test — that is why url classification uses `looksLikeImageUrl()` rather than `isImageUri()`, and
the ViewModel and the fragment's link routing must keep sharing it.

`ZumpaSimpleParser` has **no tests**, and it is the component most likely to break when the forum's
HTML changes.
