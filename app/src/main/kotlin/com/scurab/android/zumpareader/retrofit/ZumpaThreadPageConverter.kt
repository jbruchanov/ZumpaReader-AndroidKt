package com.scurab.android.zumpareader.retrofit

import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.squareup.okhttp.ResponseBody
import retrofit.Converter
import java.io.IOException

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class ZumpaThreadPageConverter(private val parser: ZumpaSimpleParser) : Converter<ResponseBody, ZumpaThreadResult> {

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
