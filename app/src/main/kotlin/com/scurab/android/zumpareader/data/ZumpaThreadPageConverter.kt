package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import okhttp3.ResponseBody
import retrofit2.Converter
import java.io.IOException

/**
 * Created by JBruchanov on 24/11/2015.
 */
class ZumpaThreadPageConverter(private val parser: ZumpaSimpleParser) : Converter<ResponseBody, ZumpaThreadResult> {

    @Throws(IOException::class)
    override fun convert(value: ResponseBody): ZumpaThreadResult? {
        val stream = value.byteStream()
        try {
            return parser.parseThread(stream, null)
        } finally {
            closeQuietly(stream)
        }
    }
}
