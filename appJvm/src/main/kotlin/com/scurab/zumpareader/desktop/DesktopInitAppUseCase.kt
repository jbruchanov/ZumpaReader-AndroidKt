package com.scurab.zumpareader.desktop

import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.scurab.android.zumpareader.usecase.InitAppUseCase

/**
 * What the desktop app does at launch - the other half of [InitAppUseCase], and a much shorter list
 * than Android's. There are no notification channels here and no Crashlytics to tell who is using
 * the app, so all that is left is the image loader.
 *
 * [imageLoader] is a lambda because `setSafe` takes a factory: Coil resolves it the first time an
 * `AsyncImage` needs one, which keeps the whole client stack out of the startup path.
 */
class DesktopInitAppUseCase(
    private val imageLoader: () -> ImageLoader,
) : InitAppUseCase {

    override fun invoke() {
        //the loader every AsyncImage resolves to, so none of them has to be handed one
        SingletonImageLoader.setSafe { imageLoader() }
    }
}
