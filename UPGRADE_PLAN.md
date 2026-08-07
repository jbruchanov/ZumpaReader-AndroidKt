# ZumpaReader – upgrade plan

Branch `deps_update`. Versions resolved from Maven Central / `dl.google.com` on 2026-08-06.

## Done

One commit per step, each one verified with `./gradlew :app:assembleDebug`; the final state also
builds `clean :app:assembleDebug :app:assembleRelease` and passes `lintVitalRelease`.

| # | commit | content |
|---|---|---|
| 1 | `5ceb0df` | all hard-coded coordinates moved into `libs.versions.toml`; compile/targetSdk 34 → **36**; AGP 8.5.0 → 8.13.2 and Gradle 8.8 → 8.14.3 (compileSdk 36 needs AGP ≥ 8.9.1) |
| 2 | `59a25a6` | Kotlin 1.9.24 → **2.2.21**, coroutines 1.8.1 → **1.11.0**, `-Xcontext-receivers` dropped (unused) |
| 3 | `651dca4` | firebase-bom 33.1.1 → **34.17.0**, google-services 4.4.2 → 4.5.0, crashlytics plugin 3.0.2 → 3.0.7, firebase `-ktx` artifacts dropped, core-ktx 1.13.1 → **1.18.0**, annotation 1.8.0 → 1.10.0, material 1.12.0 → **1.14.0** |
| 4 | `e6dee60` | jsoup 1.10.3 → **1.23.1**, okhttp 4.12.0 → **5.4.0**, retrofit 2.10.0 → **3.0.0**, gson → 2.14.0 and now declared explicitly |
| 5 | `0654d99` | Gradle DSL hygiene: space-assignment → `=`, `namespace` into the `android` block, `kotlinOptions` → `kotlin { compilerOptions }`, dead `depsize`/`clean` tasks removed, refreshVersions 0.60.6 |
| 6 | `97ed634` | catalog stripped to what the app resolves (~60 unused entries, the dead test deps, unused plugin aliases) |
| 7 | `213f2a0` | window insets for the enforced edge-to-edge of targetSdk 36; dead storage + C2DM permissions removed from the manifest |
| 8 | `0e6de26` | `onBackPressed` → `OnBackPressedDispatcher` (predictive back is on by default at targetSdk 36) |
| 9 | `a976c88` | **Wave C**: the whole RxJava2 stack replaced with coroutines |

Notes on the two risky ones:

* **jsoup 1.10.3 → 1.23.1.** Verified by dumping the DOM of the live main page and a thread page
  the way `ZumpaSimpleParser` walks it (`getElementsByTag` / `child(n)` / `text()` / `html()`)
  under both versions and diffing: identical table/row/column structure, identical `html()` entity
  output, identical `Datum:&nbsp;` and `reply2('@…:` matches. The only behavioural difference is
  that `text()` now normalizes `&nbsp;` to a space and trims — which the parser already neutralizes
  (`safeInt` strips it, `time.replace(NBSP_CHAR, ' ')`, `getAuthorName` reads
  `textNodes().getWholeText()`).
* **core-ktx stops at 1.18.0.** 1.19.0 requires AGP 9.1 + compileSdk 37; see step C below.
* **Wave C.** `rxjava`, `rxandroid`, `rxbinding` (which had no call site at all), `rxlifecycle` and
  `adapter-rxjava2` are gone; `lifecycle-runtime-ktx` 2.9.4 and `kotlinx-coroutines-android` are in.
  The retrofit interfaces use `suspend` functions, which retrofit 3 supports without a call adapter.
  `bindToLifecycle()` became `BaseFragment.launchWithView()` — view lifecycle scope, falling back to
  the fragment scope for a back-stacked fragment that reacts to a bus event without a view.
  Thread affinity was kept as it was, including building the message spans on the main thread.

## Remaining

### A. Runtime check on a device — nothing else can substitute for it

Not done here: the connected device (Android 17) has the **Play Store** build of
`com.scurab.zumpareader` installed, so a debug-signed APK cannot be installed over it without
uninstalling first and losing the app data. Install from Android Studio with the release keystore,
or uninstall the Play build first.

What to look at, in order of risk:

1. **Edge-to-edge.** `MainActivity` (toolbar behind the status bar, FAB and the post panel above
   the nav bar, keyboard open in the post message screen), `SettingsActivity` (preference list),
   dialogs (`AppTheme.Dialog*`). `ImageActivity` was deliberately left drawing behind the bars.
2. **Back navigation.** Back gesture inside a thread, inside the post screen and on the main list —
   `SubListFragment.onBackButtonClick()` is the only overriding implementation.
3. **The parser against live HTML**: thread list dates, answer counts, author names, survey
   percentages, and the "show last author" setting (that path splits the last column on spaces and
   is the only place where the jsoup `text()` change could still bite).
4. Push notification tap-through (`MyFirebaseService`), image upload, offline download.
5. **Everything the Rx stack used to drive**, since it is all new plumbing and the first call is the
   first test: list load and pull-to-refresh, opening a thread, posting a message and a reply
   (the 302-as-success path in `ignoringZumpaRedirect`), ignore/favourite toggles, survey voting,
   image copy/resize/rotate/upload, login and logout in the settings.
   Note that retrofit resolves the converter for a `suspend` function from the unwrapped
   continuation type, so `ZumpaConverterFactory`'s `when (type)` still matches — that is the one
   thing in the migration that fails at first call rather than at compile time.

### B. The dependencies that are kept on purpose

`rxlifecycle2` is gone with Wave C, but two support-library users remain, so
**`android.enableJetifier=true` has to stay**:

* `swipy` 1.2.3 (support 23.1.1, 2016) — kept deliberately: its pull-to-refresh side is
  configurable and `SubListFragment` uses the bottom direction, which
  `androidx.swiperefreshlayout` cannot do. Replacing it means either changing the thread screen's
  UX or writing a custom pull-up widget.
* `pinchtozoom` 0.1 (support 25.3.1, 2017) — kept: with swipy staying, dropping it would not remove
  Jetifier anyway, and vendoring it means untested gesture code.

Not Jetifier-related, also kept and still dead upstream: `otto` (deprecated 2015), `kotson` (2019),
`picasso` (last release 2022, while **Fresco** is also shipped — two image loaders).

### C. Wave D — toolchain, partly blocked by B

1. `android.enableJetifier=true` → **blocked** by swipy + pinchtozoom above. AGP 9 drops Jetifier
   entirely, so AGP 9 requires resolving B first.
2. ~~`android.nonTransitiveRClass`~~ → done, it is `true` since `4e31fa3`. Nothing had to be
   re-imported, the app never reached for library resources through its own R class.
3. AGP 8.13.2 → 9.x (latest 9.3.1) + Gradle 8.14.3 → 9.x — blocked by 1.
4. Then core-ktx 1.19.0 and compileSdk 37 become available — the dev device already runs Android 17.
5. Optional while in there: `.gradle` → `.gradle.kts`, `org.gradle.configuration-cache=true`,
   a `jvmToolchain(17)` declaration so the build stops depending on the launching JDK.

### D. MVVM — what the Koin step prepared, and what each screen still needs

**Done � phases 0�8, see [`MVVM_PLAN.md`](MVVM_PLAN.md)** for the plan, the decisions behind it and
the remaining Compose phase. What follows is the summary of the starting conditions it was written
against, kept for context.

DI is in place (`di/Modules.kt`, started in `ZumpaReaderApp.onCreate`), `viewModelModule` is the
empty slot each screen adds a line to. What the screens will run into:

* **`SettingsActivity` cannot host a ViewModel.** It extends the framework
  `android.preference.PreferenceActivity`, not a `ComponentActivity`, so there is no
  `ViewModelStore`. It has to move to `AppCompatActivity` + `androidx.preference` first — the
  biggest single piece of the MVVM work, and it is the screen with the most logic (login, logout,
  push registration).
* **`MainActivity` and `ImageActivity` are `AppCompatActivity`**, and the fragments are plain
  androidx fragments since the rx removal, so `by viewModel()` works there today.
* **The shared mutable state is `ZumpaReaderApp.zumpaData`** (a `TreeMap<String, ZumpaThread>`
  eight call sites reach into) plus `zumpaReadStates`. That is the repository that should end up
  behind an interface in `coreModule`, not a field on the Application.
* **`otto` is the fragment-to-fragment channel** — 2 `@Subscribe` handlers, 2 `post()` calls
  (`LoadThreadEvent`, `DialogEvent`). A `SharedFlow` on a shared ViewModel replaces it, and that
  removes otto (deprecated 2015) as a side effect.
* **Text styling happens on the UI thread** (`styledAuthor` / `styledBody` per item in
  `SubListFragment.loadData`). Once a ViewModel owns the load, that work belongs in it, off the
  main thread — the one place where the current behaviour is deliberately preserved but wrong.
* The Koin graph is **not verified**: like any Koin setup without a `koin-test` `verify()` test, a
  missing binding shows up at first injection. The definitions were audited by hand; a test source
  set with `koin-test` would make it a build-time failure instead.

### E. Smaller leftovers

* ~~9 lint errors~~ → fixed in `8f92ede`, `./gradlew :app:lintDebug` is clean (warnings remain).
  Note that lint could not run at all before this upgrade: Jetifier failed to transform
  `shadows-support-v4-3.3.1.jar`.
* ~~`GCMReceiver`~~ → deleted in `4984020`, it was dead code with a `PendingIntent` that would have
  thrown on Android 12+.
* No test source set exists. The catalog no longer carries test dependencies; adding
  `app/src/test` with junit5 + mockk for `ZumpaSimpleParser` would pay for itself the next time
  the forum HTML changes.
* Bump `versionCode` / `versionName` in `app/build.gradle` before releasing.
