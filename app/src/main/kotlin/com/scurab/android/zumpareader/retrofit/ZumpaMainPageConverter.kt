package com.scurab.android.zumpareader.retrofit

import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.squareup.okhttp.ResponseBody
import retrofit.Converter
import java.io.IOException

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class ZumpaMainPageConverter(private val parser: ZumpaSimpleParser) : Converter<ResponseBody, ZumpaMainPageResult> {

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
