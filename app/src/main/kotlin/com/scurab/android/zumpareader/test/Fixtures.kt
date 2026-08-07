package com.scurab.android.zumpareader.test

/**
 * Namespace for preview data. One marker object per screen; the data itself lives in
 * `XyzFixtures.kt` next to this file as extensions, so a screen's fixtures grow without touching
 * anyone else's:
 *
 * ```
 * fun Fixtures.MainList.uiState(rows: Int = 6) = MainListUiState(…)
 * ```
 *
 * This is a package in `src/main`, not the `src/test` source set - previews live in `main` and the
 * release build compiles them, so they cannot reference test sources.
 */
object Fixtures {
    object Image
    object MainList
    object OfflineDownload
    object Post
    object PostImage
    object Settings
    object SubList
}
