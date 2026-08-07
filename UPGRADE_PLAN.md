# ZumpaReader – upgrade plan

Branch `deps_update`. Versions resolved from Maven Central / `dl.google.com` on 2026-08-06.

**Where it stands.** Dependencies are current, RxJava is gone, DI is Koin, and every screen except
`SettingsActivity` is MVVM. How it is built now: [`ARCHITECTURE.md`](ARCHITECTURE.md).

Verified: `clean :app:assembleDebug :app:assembleRelease`, `lintVitalRelease` and `lintDebug` clean,
56 unit tests. **Not verified on a device — see §A, which is still the biggest open item.**

---

## Done

One commit per step, each verified with `./gradlew :app:assembleDebug`.

### Dependencies and toolchain

| # | commit | content |
|---|---|---|
| 1 | `5ceb0df` | all hard-coded coordinates into `libs.versions.toml`; compile/targetSdk 34 → **36**; AGP 8.5.0 → 8.13.2, Gradle 8.8 → 8.14.3 |
| 2 | `59a25a6` | Kotlin 1.9.24 → **2.2.21**, coroutines 1.8.1 → **1.11.0** |
| 3 | `651dca4` | firebase-bom → **34.17.0**, core-ktx → **1.18.0**, material → **1.14.0**, firebase `-ktx` artifacts dropped |
| 4 | `e6dee60` | jsoup → **1.23.1**, okhttp → **5.4.0**, retrofit → **3.0.0**, gson → 2.14.0 |
| 5 | `0654d99` | Gradle DSL hygiene for Gradle 9/10 |
| 6 | `97ed634` | catalog stripped to what the app resolves |
| 7 | `213f2a0` | window insets for the enforced edge-to-edge of targetSdk 36 |
| 8 | `0e6de26` | `onBackPressed` → `OnBackPressedDispatcher` (predictive back is on at targetSdk 36) |
| 9 | `a976c88` | **Wave C**: the whole RxJava2 stack replaced with coroutines |
| 10 | `4e31fa3`, `8f92ede`, `4984020` | non-transitive R classes, the 9 lint errors, the dead `GCMReceiver` |
| 11 | `fd5d6c8` | **Koin**: the hand-rolled service locator on the Application becomes a module |

Two notes worth keeping:

* **jsoup 1.10.3 → 1.23.1** was verified by dumping the DOM of the live main page and a thread page
  the way `ZumpaSimpleParser` walks it under both versions and diffing: identical structure and
  `html()` entity output. The only difference is `text()` normalizing `&nbsp;` and trimming, which
  the parser already neutralizes.
* **Retrofit resolves the converter for a `suspend` function from the unwrapped continuation type**,
  so `ZumpaConverterFactory`'s `when (type)` still matches. That is the one thing in the Rx removal
  that fails at first call rather than at compile time.

### MVVM

| phase | commit | content |
|---|---|---|
| 0 | `79fdb23` | `arch` (BaseViewModel, effects, `collectWhileStarted`, DeviceConfig), `ZumpaTextRenderer`, test source set |
| 1 | `c96bac3` | repositories take over the shared mutable state; otto's two uses split into a store and a bus |
| 2 | `44ebb93` | `MainActivity` chrome; intent parsing into the ViewModel |
| 3 | `77bd4b0` | `MainListFragment` |
| 4 | `91513d9` | `SubListFragment` — the big one |
| 5 | `82e3c64` | the post dialog and its two tabs; `startActivityForResult` → ActivityResult API |
| 6 | `e823288` | offline download (the last `AsyncTask`), otto deleted |
| 7 | `abfb43f` | `ImageActivity` |
| 8 | `17fbacd` | cleanup: `SendingFragment`, `IsReloadable`, model caches, transitional accessors |
| — | `2237f55` | one package per screen under `ui.` |
| — | `9090ded` | `extension` package merged into `ext` |

Bugs fixed along the way rather than ported: a blocking fresco call on the main thread in
`ImageActivity`, sharing a thread starting two activities, three hand-rolled lifecycle retentions
(including the `//TODO: doesn't work with lifecycle!` in `PostFragment`), missing session checks on
favourite/ignore/share, and an image-vs-link classification that silently misclassified everything
in tests because it went through `android.net.Uri`.

---

## Remaining

### A. Runtime check on a device — nothing else can substitute for it

**Still not done, and it now covers the MVVM work too.** Every screen's load path is new code, so
the first call is the first test.

Blocked the same way as before: the connected device (Android 17) has the **Play Store** build of
`com.scurab.zumpareader` installed, so a debug-signed APK cannot install over it. Install from
Android Studio with the release keystore, or uninstall the Play build first (losing app data).

In order of risk:

1. **Everything that loads or posts**, since all of it is new plumbing: list load and pull-to-refresh,
   paging, opening a thread, posting a thread and a reply (the 302-as-success path), ignore/favourite,
   survey voting, image copy/resize/rotate/upload, offline download, login/logout.
2. **`ImageSpan` construction off the main thread.** The renderer runs on `Dispatchers.Default` and
   `parseBody` inflates drawables. Drawable *creation* is safe off-main; confirm no span
   implementation touches a `View`. If one does, render in the binder and let the `LruCache` absorb
   the cost.
3. **`repeatOnLifecycle(STARTED)`.** Background mid-load and return: you should see the finished
   list, not a stuck spinner. The old code cancelled the UI-side effect; the new code has to render
   a result that arrived while stopped.
4. **The reply box.** The old code kept the `@author: ` headers in the `Editable` and found them
   again through `AuthorSpan` — spans used as data. `DraftUiState` models that as headers-ahead-of-
   body with a re-parse on edit. Twelve tests cover it, but it is the piece where behaviour could
   differ in a way tests will not catch.
5. **Edge-to-edge** (`MainActivity`, `SettingsActivity`, dialogs; `ImageActivity` deliberately draws
   behind the bars) and **back navigation** inside a thread, the post screen and the main list.
6. **The parser against live HTML**: thread list dates, answer counts, author names, survey
   percentages, and the "show last author" setting.
7. Push notification tap-through (`MyFirebaseService`).

### B. ~~The dependencies kept on purpose~~ → resolved

**`android.enableJetifier` is off**, since Compose phase C6. Both support-library users are gone:

* ~~`swipy` 1.2.3~~ → removed in C6; the thread screen's bottom pull is hand written
  (`ui/sublist/BottomPullToRefresh.kt`).
* ~~`pinchtozoom` 0.1~~ → removed in C1; the zoomable viewer is `graphicsLayer` +
  `detectTransformGestures`.

Still shipped and still dead upstream: `kotson` (2019), `picasso` (2022) and **Fresco** — both image
loaders are replaced by Coil as each screen converts, and get deleted in C9. `otto` went in MVVM
phase 6.

### C. Toolchain — **unblocked**

1. ~~`android.enableJetifier=true`~~ → off as of C6, verified with a clean
   `assembleDebug assembleRelease`.
2. AGP 8.13.2 → 9.x + Gradle 9.x — **now possible**. Worth doing before C7: it is also what lifts
   the `resolutionStrategy.force` holding `lifecycle-*-compose` at 2.10.0 (COMPOSE_PLAN risk 0).
3. Then core-ktx 1.19.0 and compileSdk 37 become available — the dev device already runs Android 17.
4. Optional while in there: `.gradle` → `.gradle.kts`, `org.gradle.configuration-cache=true`, a
   `jvmToolchain(17)` declaration so the build stops depending on the launching JDK.

### D. Compose

**The detailed per-screen plan is in [`COMPOSE_PLAN.md`](COMPOSE_PLAN.md)** — conventions, the two
`Screen` overloads, previews and fixtures, and a phase per screen. Progress: **C0 and C1 done**
(`ff326e1`, `0f09920`). Summary of the order:

`SettingsActivity` is converted **last**, not first as this section originally said: every other
screen is already MVVM, so converting them is a render-only change, while Settings is the one screen
that needs a ViewModel invented for it. Doing it last means it is written against a Compose codebase
that already works, and `androidx.preference` is never introduced at all.

Superseded ordering, kept for the reasoning about what each step unblocks:

1. **`SettingsActivity` first** — it has no ViewModel today, so there is nothing to throw away.
   `PreferenceActivity` → `ComponentActivity` + `setContent`, `res/xml/settings.xml` and
   `ButtonPreference` replaced by composables, `SettingsUiState` + `SettingsViewModel`, and the
   login/logout/push-registration logic (~90 lines in the activity) into an `AuthRepository`. Most
   logic, least UI — the cheapest real test of the pattern.
2. `AnnotatedTextRenderer : ZumpaTextRenderer<AnnotatedString>` — the second implementation of the
   interface. `SpannedTextRenderer` stays until the last RecyclerView goes.
3. `ImageActivity`, then `PostImageFragment` / `PostMessageFragment` — small and already
   state-driven.
4. `MainListFragment` and `SubListFragment` last: the row lists → `LazyColumn`. The adapters get
   thrown away rather than migrated to `ListAdapter` twice, which is the point of having kept them.
5. Only then is `swipy` replaceable, by Compose's `PullToRefreshBox`. That unblocks §B → §C.

While in there: `ProgressDialog` → an inline overlay, and `ToggleAdapter`'s open state into UiState
(see the exceptions in `ARCHITECTURE.md`).

### E. Known bugs

#### E1. An image vanishes from the thread list after viewing it full screen

Tap an image in a thread → it opens full screen and zooms fine → navigate back → **the image is
gone from the list**. Reported from real use; not yet reproduced on a device here.

What the code says, without having run it:

* The shared element is set up in `SubListFragment.onItemClick(url, longClick, view)` with the
  three-argument `makeSceneTransitionAnimation(activity, view, "transition_image")`, which assigns
  the transition name to the clicked view for the duration of the transition. Only
  `activity_image.xml` declares `transitionName` in a layout; `item_sub_list_image.xml` does not.
* For an image row the clicked `view` is the whole `item_content` FrameLayout, not the
  `SimpleDraweeView` inside it — see the `TYPE_IMAGE` branch of `SubListAdapter.onCreateViewHolder`.
* The framework sets the source view **`INVISIBLE`** for the duration and restores it when the
  *return* transition completes. In a RecyclerView the row can be recycled or rebound in the
  meantime, so the restore can land on a different view or never run — which is exactly "the row is
  still there but empty".
* Nothing puts it back: `ZumpaSubItemViewHolder.loadImage` early-returns when `url == loadedUrl`,
  so a rebind does not reset the drawee, and no code sets `visibility = VISIBLE` on bind.
* `ImageActivity`'s `Failed` path calls `finish()` rather than `supportFinishAfterTransition()`,
  which skips the return transition outright.

**Phase 7 may have made it more likely, so check this before blaming it on the old code.** The
bitmap now always loads asynchronously on `Dispatchers.IO`; the previous implementation hit the
fresco *bitmap memory cache* synchronously through `CallerThreadExecutor`, so the image was
frequently already set before the enter transition ran. `ImageActivity` never calls
`supportPostponeEnterTransition()`, so the transition now starts against an empty `ImageView`.

Things to try, cheapest first:

1. `supportPostponeEnterTransition()` in `ImageActivity.onCreate`, `startPostponedEnterTransition()`
   when `ImageUiState.Loaded` arrives.
2. `setExitSharedElementCallback` on the fragment, remapping the name to whichever view holder
   currently shows that url — the standard RecyclerView shared-element recipe, and the part that is
   missing here.
3. `supportFinishAfterTransition()` on the `Failed` path.
4. Belt and braces: force `visibility = VISIBLE` when binding an image row.
5. If it stays flaky, drop the shared element for image rows entirely. A plain fade costs little and
   this transition has never had the callback machinery it needs.

Needs a device (§A).

#### Backend-facing — after Compose

Not caused by the MVVM work. Both are in the request path and are cheapest to fix once the post/send
flow is already state-driven.

1. **The cookie grows until the backend rejects the request.** `ZumpaPrefs.cookies` is a
   `Set<String>` only ever replaced wholesale on login (`ParseUtils.extractCookies`), and
   `cookiesMap` hands the whole set to `JavaNetCookieJar` on every request. It needs trimming
   automatically — drop expired and duplicate-name cookies before building the header, rather than
   leaving the user to log out and back in.
2. **Message text is not encoded/escaped properly on send.** `ZumpaThreadBody.toHttpPostString`
   hand-builds the form body with `String.encodeHttp()` (`URLEncoder.encode(this, ENCODING)`, the
   forum's legacy charset). Characters outside that charset — emoji above all — are silently dropped
   by the backend. Either encode so the forum accepts them, or reject them in the UI before sending
   rather than having them disappear after.

### F. Smaller leftovers

* **`ZumpaSimpleParser` has no tests.** The source set exists now (MVVM phase 0), and this is the
  component most likely to break when the forum's HTML changes. Cheapest remaining value.
* **`SavedStateHandle` is not wired anywhere.** The ViewModels survive rotation but not process
  death; `androidx-lifecycle-viewmodel-savedstate` is not in the catalog. It matters most for
  `PostFragment` (a half-written post) and the main list's paging position.
* Bump `versionCode` / `versionName` in `app/build.gradle` before releasing.
* ~~9 lint errors~~, ~~`GCMReceiver`~~, ~~no test source set~~ → done, see the table above.
