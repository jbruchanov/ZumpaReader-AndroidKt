package com.scurab.android.zumpareader.ui.post

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageMetaFormatTest {

    @Test
    fun `zero bytes is not scaled`() {
        assertEquals("0 B", ImageMetaFormat.size(0))
    }

    @Test
    fun `bytes below a kilobyte stay bytes`() {
        assertEquals("512.00 B", ImageMetaFormat.size(512))
    }

    @Test
    fun `a kilobyte and above scales up`() {
        assertEquals("2.00 KiB", ImageMetaFormat.size(2048))
        assertEquals("1.50 MiB", ImageMetaFormat.size(1024L * 1024 * 3 / 2))
    }

    /**
     * The loop is `while (size > MOD)`, so exactly 1024 bytes stays in bytes rather than becoming
     * 1 KiB. Pinned because it is the original behaviour, not because it is the nicer answer.
     */
    @Test
    fun `exactly one kilobyte stays in bytes`() {
        assertEquals("1024.00 B", ImageMetaFormat.size(1024))
    }

    @Test
    fun `resolution is width by height`() {
        assertEquals("1920x1080", ImageMetaFormat.resolution(1920, 1080))
    }

    @Test
    fun `sample labels line up with the shift used to build the sample size`() {
        assertEquals(listOf("1/1", "1/2", "1/4", "1/8"), ImageMetaFormat.sampleLabels)
        ImageMetaFormat.sampleLabels.forEachIndexed { index, label ->
            assertEquals("1/${1 shl index}", label)
        }
    }
}
