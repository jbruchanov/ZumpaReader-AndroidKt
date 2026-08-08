package com.scurab.android.zumpareader.ui.post

/**
 * The two formatters that used to be private extensions inside `PostImagePanelView`. They were the
 * only logic in that widget, so pulling them out is what makes them testable.
 */
object ImageMetaFormat {

    private val UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")
    private const val MOD = 1024

    fun size(bytes: Long): String {
        if (bytes == 0L) {
            return "0 B"
        }
        var index = 0
        var size = bytes.toFloat()
        while (size > MOD) {
            size /= MOD
            index++
        }
        return "%.2f %s".format(size, UNITS[index])
    }

    fun resolution(width: Int, height: Int): String = "%sx%s".format(width, height)

    /** The `1/1 … 1/8` labels of the old size spinner; the sample size is `1 shl index`. */
    val sampleLabels: List<String> = listOf("1/1", "1/2", "1/4", "1/8")
}
