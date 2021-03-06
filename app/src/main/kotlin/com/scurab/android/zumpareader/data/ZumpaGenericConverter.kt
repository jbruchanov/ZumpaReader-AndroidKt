package com.scurab.android.zumpareader.data

import com.scurab.android.zumpareader.model.ZumpaGenericResponse
import okhttp3.ResponseBody
import retrofit2.Converter
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Created by JBruchanov on 24/11/2015.
 */
class ZumpaGenericConverter : Converter<ResponseBody, ZumpaGenericResponse> {

    @Throws(IOException::class)
    override fun convert(value: ResponseBody): ZumpaGenericResponse {
        val stream = value.byteStream()
        try {
            val baos = ByteArrayOutputStream()
            stream.copyTo(baos)
            return ZumpaGenericResponse(baos.toByteArray(), value.contentType().toString())
        } finally {
            closeQuietly(stream)
        }
    }
}
