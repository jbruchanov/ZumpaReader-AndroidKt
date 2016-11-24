package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.model.ZumpaWSBody
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Created by JBruchanov on 24/11/2015.
 */
class ZumpaGenericConverterFactory() : Converter.Factory() {

    private val httpPostConverter by lazy {
        Converter<ZumpaWSBody, RequestBody> {
            value ->
            RequestBody.create(MediaType.parse("application/json"), value?.toHttpPostString())
        }
    }

    private val converter by lazy { ZumpaGenericConverter() }

    override fun responseBodyConverter(type: Type?, annotations: Array<out Annotation>?, retrofit: Retrofit?): Converter<ResponseBody, *>? {
        return converter
    }

    override fun requestBodyConverter(type: Type?, parameterAnnotations: Array<out Annotation>?, methodAnnotations: Array<out Annotation>?, retrofit: Retrofit?): Converter<*, RequestBody>? {
        return httpPostConverter
    }
}
