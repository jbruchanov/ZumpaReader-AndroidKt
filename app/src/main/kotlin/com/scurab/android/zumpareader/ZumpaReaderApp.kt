package com.scurab.android.zumpareader

import android.app.Application
import com.scurab.android.zumpareader.retrofit.ZumpaConverterFactory
import com.scurab.android.zumpareader.ZR
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import retrofit.Retrofit
import java.util.*

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class ZumpaReaderApp:Application(){

    override fun onCreate() {
        super.onCreate()
    }

    public val zumpaParser : ZumpaSimpleParser by lazy { ZumpaSimpleParser() }

    public val zumpaAPI: ZumpaAPI by lazy {
        val retrofit = Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
                .addConverterFactory(ZumpaConverterFactory(zumpaParser))
                .build()

        retrofit.create(ZumpaAPI::class.java)
    }

    public val zumpaData: TreeMap<String, ZumpaThread> = TreeMap()
}