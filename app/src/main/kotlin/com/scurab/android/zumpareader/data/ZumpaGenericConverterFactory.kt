package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.model.ZumpaWSBody
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.ResponseBody
import retrofit.Converter
import java.lang.reflect.Type

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class ZumpaGenericConverterFactory() : Converter.Factory() {

    private val httpPostConverter by lazy {
        Converter<ZumpaWSBody, RequestBody> {
            value -> RequestBody.create(MediaType.parse("application/json"), value?.toHttpPostString())
        }
    }

    private val converter by lazy { ZumpaGenericConverter() }

    override fun fromResponseBody(type: Type?, annotations: Array<out Annotation>?): Converter<ResponseBody, *>? {
        return converter
    }

    override fun toRequestBody(type: Type?, annotations: Array<out Annotation>?): Converter<*, RequestBody>? {
        return httpPostConverter
    }
}
