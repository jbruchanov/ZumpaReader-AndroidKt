package com.scurab.android.zumpareader.arch

/**
 * The `R.bool.is_tablet` resource as an injectable value, so the two-pane behaviour is a
 * constructor input a test can set instead of a `resources.getBoolean` call inside a fragment.
 */
data class DeviceConfig(val isTablet: Boolean)
