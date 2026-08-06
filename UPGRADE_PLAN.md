# ZumpaReader – upgrade plan

State captured on 2026-08-06, branch `deps_update`. Every version listed as "latest" was resolved
from Maven Central / `dl.google.com` on that date.

## 0. Done in this change (verified with `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL)

* All hard-coded coordinates moved from `app/build.gradle` into `gradle/libs.versions.toml`
  (rx*, rxlifecycle, fresco-middleware, pinchtozoom, firebase `-ktx` artifacts, junit4, robolectric,
  fest-android, mockito). New bundles: `firebase`, `fresco`, `retrofit`, `rxlifecycle`.
  `fresco` → `fresco-base` and `retrofit` → `retrofit-base` were renamed so the bundle names stay
  free, matching the existing `okhttp-base` / `groupie-base` convention.
* `android-sdk-compile` / `android-sdk-target` 34 → **36** (Android 16; Play Console requires
  target 36 from 2026-08-31).
* Required side-effects of compileSdk 36: AGP 8.5.0 → **8.13.2** (36 needs ≥ 8.9.1),
  Gradle wrapper 8.8 → **8.14.3**.

The resolved `debugRuntimeClasspath` was diffed against a clean worktree of `HEAD`: the module set
(`group:artifact`, 163 entries) is identical, so the catalog migration changed no coordinates.
Resolved *versions* were not compared — the AGP bump can shift transitive AndroidX versions.

---

## 1. Validate targetSdk 36 at runtime — highest risk, do this first

`targetSdk ≥ 35` turns on **edge-to-edge enforcement**; the `windowOptOutEdgeToEdgeEnforcement`
escape hatch is ignored on Android 16 for apps targeting 36. The app themes are plain
`Theme.AppCompat` (`res/values/styles.xml`), i.e. nothing handles insets except
`android:fitsSystemWindows="true"` on the `CoordinatorLayout` in `activity_main.xml`.

Steps:
1. Run on an Android 15 and an Android 16 device/emulator, check every screen:
   `MainActivity` (toolbar + FAB + bottom post panel), `ImageActivity` (`AppTheme.Image`,
   fullscreen-ish), `SettingsActivity`, dialogs (`AppTheme.Dialog*`), `activity_giphy.xml`.
2. Expect breakage at the **bottom** (nav bar over `PostMessageView` / edit text) and in
   `ImageActivity`. Fix with `ViewCompat.setOnApplyWindowInsetsListener` +
   `WindowInsetsCompat.Type.systemBars() or ime()` padding, not with `fitsSystemWindows` alone.
3. Other 35/36 behaviour changes worth checking for this app:
   * foreground-service / notification behaviour of `MyFirebaseService` (POST_NOTIFICATIONS is
     already declared and handled),
   * `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` in the manifest are dead — all file IO
     goes through `getExternalFilesDir(...)` (`ZumpaReaderApp.kt`, `CopyFromResourcesTask.kt`,
     `PicassoHttpDownloader2.kt`, `OfflineDownloadFragment.kt`). Delete both permissions plus the
     unused `android.permission.STORAGE` line.
   * the legacy C2DM `permission.C2D_MESSAGE` block in the manifest is obsolete since FCM — remove.
4. Bump `versionCode`/`versionName` in `app/build.gradle` before any release build.

## 2. Kotlin / coroutines (do before further library bumps)

Current: Kotlin 1.9.24, coroutines 1.8.1. Latest: Kotlin 2.4.10, coroutines 1.11.0.
Recent OkHttp/Retrofit/AndroidX releases are compiled against Kotlin 2.x metadata, so this gates
step 3.

1. Kotlin 1.9.24 → **2.2.21** (K2). No kapt/KSP and no Compose in the project, so the migration
   surface is only the compiler. Expect the K2 nullability errors that are already warnings today:
   `ZumpaGenericConverterFactory.kt:26/30` ("incorrect nullability … will become an error soon"),
   `ParseUtils.kt:63` (String? vs CharSequence).
2. Then coroutines 1.8.1 → 1.11.0.
3. Optionally Kotlin → 2.4.x afterwards, as a separate commit.
4. `freeCompilerArgs += "-Xcontext-receivers"` in `app/build.gradle` — context receivers were
   replaced by context parameters in Kotlin 2.2; the flag is deprecated/removed. Check whether any
   source actually uses them (grep for `context(`); if not, drop the flag.

## 3. Library updates (after step 2), in waves

Each wave = one commit + one `assembleDebug` + a smoke run.

**Wave A – Google/AndroidX, low risk**

| catalog key | current | latest |
|---|---|---|
| `firebase-bom` | 33.1.1 | 34.17.0 |
| `google-services` (plugin) | 4.4.2 | 4.5.0 |
| `firebase-plugin-crashlytics` | 3.0.2 | 3.0.7 |
| `androidx-core-ktx` | 1.13.1 | 1.19.0 |
| `androidx-annotation` | 1.8.0 | 1.10.0 |
| `google-material` | 1.12.0 | 1.14.0 |

Also in this wave: the `-ktx` Firebase artifacts are deprecated and being removed — switch
`firebase-crashlytics-ktx` → `firebase-crashlytics` and `firebase-messaging-ktx` →
`firebase-messaging` in the catalog (the KTX APIs are in the main artifacts since BoM 32.5).

**Wave B – networking / parsing, needs functional testing against zumpa.nickde.com**

| catalog key | current | latest | note |
|---|---|---|---|
| `jsoup` | 1.10.3 | 1.23.1 | 13 years of parser changes; `ZumpaSimpleParser.java` + `reader/*` must be re-verified against live HTML. Has CVE fixes — worth doing. |
| `okhttp` | 4.12.0 | 5.4.0 | major; mostly source-compatible, check `PicassoHttpDownloader2.kt` and the cookie/interceptor setup |
| `retrofit` | 2.10.0 | 2.12.0 → 3.0.0 | Retrofit 3 needs OkHttp 5; `adapter-rxjava2` 3.0.0 exists, so the Rx path survives |
| `gson` | 2.11.0 | 2.14.0 | |

**Wave C – abandoned dependencies (decide: keep pinned, or replace)**

These are the reason `android.enableJetifier=true` is still needed — they drag in
`com.android.support:appcompat-v7` (verified via `dependencyInsight`):

* `swipy` 1.2.3 (support 23.1.1) — last release 2016. Replace with
  `androidx.swiperefreshlayout:swiperefreshlayout` (the app only uses top/bottom refresh).
* `pinchtozoom` 0.1 (support 25.3.1) — 2017, ~200 lines. Vendor it or swap for a maintained
  zoomable image view.
* `rxlifecycle2` 2.2.2 (support 27.1.1) — archived 2019.

Plus, no Jetifier involvement but equally dead: RxJava2 2.2.21 (EOL), `rxbinding2` 2.0.0 (2016),
`otto` 1.3.8 (deprecated 2015), `kotson` 2.5.0 (2019), `picasso` (last release 2.8, 2022 — and the
app already ships **Fresco** as a second image loader; consolidating on one saves ~1 MB and a whole
dependency tree).

Realistic target: replace the Rx stack (RxJava + RxAndroid + RxBinding + RxLifecycle, used in 12
files: `BaseFragment`, `MainListFragment`, `SubListFragment`, `Post*Fragment`, `ZumpaAPI`,
`Transformers.kt`, …) with coroutines/Flow, which also removes `adapter-rxjava2` and unblocks
dropping Jetifier. This is the single largest item in the plan — schedule it on its own branch.

**Wave D – after Wave C: turn the legacy switches off in `gradle.properties`**

* `android.enableJetifier=true` → remove (only possible once the three support-lib users above are
  gone; AGP 9 drops Jetifier entirely).
* `android.nonTransitiveRClass=false` → `true` (mandatory in AGP 9; may require adding explicit
  `R` imports for `androidx.appcompat`/`material` resources).

## 4. Build-logic modernisation (AGP 9 / Gradle 9-10)

Blockers already reported by `--warning-mode all`:

* Groovy space-assignment is deprecated (removed in Gradle 10): `namespace "…"`,
  `multiDexEnabled true`, `signingConfig signingConfigs.release`, `versionCode 68`,
  `versionName "3.3.0"`, `manifestPlaceholders = […]` → use `=` everywhere in `app/build.gradle`.
* `build.gradle`: `task clean(type: Delete) { delete rootProject.buildDir }` — `buildDir` is
  deprecated and the `clean` task is provided by AGP anyway; delete the block.
* `app/build.gradle`: `task(depsize)` reads `configurations._debugApk`, which no longer exists in
  AGP 8 — the task fails if invoked. Delete it or rewrite against `debugRuntimeClasspath`.
* `StartParameter.isConfigurationCacheRequested` deprecation comes from a third-party plugin
  (refreshVersions / crashlytics) — resolves itself with the plugin bumps below.
* `settings.gradle` still applies `de.fayard.refreshVersions` 0.60.5 writing to
  `build/versions.properties`. Now that everything lives in the catalog, either update the plugin
  or drop it and use `./gradlew dependencyUpdates` / IDE catalog inspections instead. The
  `## ⬆ = "1.x"` comment noise under `jsoup` in the catalog is refreshVersions output — it
  disappears with the jsoup bump in Wave B.
* Then: AGP 8.13.2 → 9.x (latest 9.3.1) and Gradle 8.14.3 → 9.x, as the final step, since AGP 9
  requires KGP 2.x, non-transitive R classes and no Jetifier (steps 2 and 3 must land first).
* Consider converting `build.gradle`/`app/build.gradle` to `.gradle.kts` while doing this, and
  enabling `org.gradle.configuration-cache=true`.

## 5. Catalog hygiene (cheap, do any time)

* The catalog carries ~60 entries this project never uses (adjust, room, koin, navigation, compose,
  exoplayer, lottie, leakcanary, groupie, timber, truth, turbine, mockk, junit-jupiter, ktlint,
  …) — it was clearly copied from another project. Either delete the unused half or keep it as a
  shared template deliberately; right now `./gradlew` cannot tell you which is which.
* `androidx-activity-compose` is in the `android-base` bundle but nothing imports
  `androidx.activity` or Compose — drop it from the bundle.
* No `app/src/test` or `app/src/androidTest` source set exists, so `junit4`, `robolectric` 3.3.1
  (2017), `fest-android` (2013) and `mockito` 2.25 are dead `testImplementation` entries. Either
  delete the five lines from `app/build.gradle`, or start a test source set on junit5 + mockk +
  truth (already in the catalog as the `unittests-jvm` bundle) and use it for the HTML parser,
  which is the part that breaks most often.
* `jvmtarget = "17"` while the local toolchain is JDK 21 — consider a `kotlin { jvmToolchain(17) }`
  / `java.toolchain` declaration so the build is not dependent on the JDK that launches Gradle.
