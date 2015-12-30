package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.model.ZumpaBody
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.ResponseBody
import retrofit.Converter
import java.io.InputStream
import java.lang.reflect.Type

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class ZumpaConverterFactory(val parser: ZumpaSimpleParser) : Converter.Factory() {

    private val mainPageConverter: ZumpaMainPageConverter by lazy { ZumpaMainPageConverter(parser) }
    private val threadPageConverter: ZumpaThreadPageConverter by lazy { ZumpaThreadPageConverter(parser) }
    private val postConverter: ZumpaHTTPPostConverter by lazy { ZumpaHTTPPostConverter() }
    private val httpPostConverter by lazy {
        object : Converter<ZumpaBody, RequestBody> {
            override fun convert(value: ZumpaBody?): RequestBody? {
                return RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), value?.toHttpPostString())
            }
        }
    }

    override fun fromResponseBody(type: Type?, annotations: Array<out Annotation>?): Converter<ResponseBody, *>? {
        return when (type) {
            ZumpaMainPageResult::class.java -> mainPageConverter
            ZumpaThreadResult::class.java -> threadPageConverter
            else -> postConverter
        }
    }

    override fun toRequestBody(type: Type?, annotations: Array<out Annotation>?): Converter<ZumpaBody, RequestBody>? {
        return httpPostConverter
    }
}

internal fun closeQuietly(stream: InputStream) {
    try {
        stream.close()
    } catch(e: Throwable) {
        e.printStackTrace()
    }
}