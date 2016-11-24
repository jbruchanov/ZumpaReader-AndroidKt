package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.model.ZumpaBody
import com.scurab.android.zumpareader.model.ZumpaMainPageResult
import com.scurab.android.zumpareader.model.ZumpaThreadResult
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.io.InputStream
import java.lang.reflect.Type

/**
 * Created by JBruchanov on 24/11/2015.
 */
class ZumpaConverterFactory(val parser: ZumpaSimpleParser) : Converter.Factory() {

    private val mainPageConverter: ZumpaMainPageConverter by lazy { ZumpaMainPageConverter(parser) }
    private val threadPageConverter: ZumpaThreadPageConverter by lazy { ZumpaThreadPageConverter(parser) }
    private val postConverter: ZumpaGenericConverter by lazy { ZumpaGenericConverter() }
    private val httpPostConverter by lazy {
        object : Converter<ZumpaBody, RequestBody> {
            override fun convert(value: ZumpaBody?): RequestBody? {
                return RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), value?.toHttpPostString())
            }
        }
    }

    override fun responseBodyConverter(type: Type?, annotations: Array<out Annotation>?, retrofit: Retrofit?): Converter<ResponseBody, *>? {
        return when (type) {
            ZumpaMainPageResult::class.java -> mainPageConverter
            ZumpaThreadResult::class.java -> threadPageConverter
            else -> postConverter
        }
    }


    override fun requestBodyConverter(type: Type?, parameterAnnotations: Array<out Annotation>?, methodAnnotations: Array<out Annotation>?, retrofit: Retrofit?): Converter<*, RequestBody>? {
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

