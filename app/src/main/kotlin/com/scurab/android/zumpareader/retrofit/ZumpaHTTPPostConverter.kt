package com.scurab.android.zumpareader.retrofit

import com.scurab.android.zumpareader.model.ZumpaResponse
import com.squareup.okhttp.ResponseBody
import retrofit.Converter
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class ZumpaHTTPPostConverter : Converter<ResponseBody, ZumpaResponse> {

    @Throws(IOException::class)
    override fun convert(value: ResponseBody): ZumpaResponse {
        val stream = value.byteStream()
        try {
            val baos = ByteArrayOutputStream()
            stream.copyTo(baos)
            return ZumpaResponse(baos.toByteArray(), value.contentType())
        } finally {
            closeQuietly(stream)
        }
    }
}
