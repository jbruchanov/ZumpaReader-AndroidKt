package com.scurab.android.zumpareader.util

import com.fleeksoft.ksoup.Ksoup

/**
 * Strips tags and decodes HTML entities - what `android.text.Html.fromHtml(x).toString()` was used
 * for in five places, none of which wanted a `Spanned`.
 *
 * `wholeText()` and not `text()`: `text()` normalises runs of whitespace, which would silently
 * reflow message bodies. `wholeText()` keeps them, which is what `Html.fromHtml` did.
 *
 * One known difference from the Android version: an `<img>` no longer leaves a `U+FFFC` object
 * replacement character behind. Nothing depended on it - image urls are collected separately by
 * `ZumpaSimpleParser.getLinks` before the tags are stripped, and
 * [com.scurab.android.zumpareader.text.AnnotatedTextRenderer] works off the urls, not the placeholder.
 */
fun String.htmlToText(): String = Ksoup.parseBodyFragment(this).body().wholeText()
