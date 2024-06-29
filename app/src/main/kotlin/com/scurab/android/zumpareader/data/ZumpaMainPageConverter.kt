package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import java.io.IOException
import okhttp3.ResponseBody
import retrofit2.Converter

/**
 * Created by JBruchanov on 24/11/2015.
 */
class ZumpaMainPageConverter(private val parser: ZumpaSimpleParser) : Converter<ResponseBody, ZumpaMainPageResult> {

    @Throws(IOException::class)
    override fun convert(value: ResponseBody): ZumpaMainPageResult {
        val stream = value.byteStream()
        try {
            return parser.parseMainPage(stream)
        } finally {
            closeQuietly(stream)
        }
    }
}
