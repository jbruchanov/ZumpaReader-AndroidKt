package com.scurab.android.zumpareader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.scurab.android.zumpareader.model.ZumpaThread
import com.scurab.android.zumpareader.reader.ZumpaSimpleParser
import com.scurab.android.zumpareader.retrofit.ZumpaConverterFactory
import com.scurab.android.zumpareader.util.ParseUtils
import com.scurab.android.zumpareader.util.exec
import com.squareup.okhttp.OkHttpClient
import com.squareup.picasso.Downloader
import com.squareup.picasso.OkHttpDownloader
import com.squareup.picasso.Picasso
import retrofit.Retrofit
import retrofit.RxJavaCallAdapterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by JBruchanov on 24/11/2015.
 */
public class ZumpaReaderApp:Application(){

    override fun onCreate() {
        super.onCreate()


        val client = OkHttpClient()
        client.setConnectTimeout(2000L, TimeUnit.MILLISECONDS)
        client.setReadTimeout(2000L, TimeUnit.MILLISECONDS)
        client.setWriteTimeout(2000L, TimeUnit.MILLISECONDS)

        val picasso = Picasso.Builder(this)
                .downloader(object : OkHttpDownloader(client){
                    override fun load(uri: Uri?, networkPolicy: Int): Downloader.Response? {
                        var resultBitmap : Bitmap? = null
                        var md5Uri = ParseUtils.MD5(uri.toString())
                        if (md5Uri != null) {
                            resultBitmap = tryLoadImage(md5Uri)
                        }
                        if (resultBitmap == null) {
                            var response = super.load(uri, networkPolicy)
                            var mem = ByteArrayOutputStream(Math.max(64 * 1024, response.contentLength.toInt()))
                            response.inputStream.copyTo(mem)
                            var byteArray = mem.toByteArray()
                            resultBitmap = ParseUtils.resizeImageIfNecessary(byteArray, this@ZumpaReaderApp.resources)
                            if (md5Uri != null) {
                                saveImage(resultBitmap, md5Uri)
                            }
                        }
                        return Downloader.Response(resultBitmap, false)
                    }
                })
                .listener({ picasso, uri, exception ->
                    Log.d("PicassoLoader", "URL:%s Exception:%s".format(uri, exception))
                    exception.printStackTrace()
                }).build()
        Picasso.setSingletonInstance(picasso)
    }

    public val zumpaParser : ZumpaSimpleParser by lazy { ZumpaSimpleParser() }

    public val zumpaAPI: ZumpaAPI by lazy {
        val retrofit = Retrofit.Builder()
                .baseUrl(ZR.Constants.ZUMPA_MAIN_URL)
                .addConverterFactory(ZumpaConverterFactory(zumpaParser))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build()

        retrofit.create(ZumpaAPI::class.java)
    }

    public val zumpaData: TreeMap<String, ZumpaThread> = TreeMap()

    private fun tryLoadImage(md5: String): Bitmap? {
        val path = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        path.exec {
            var file = File(path.absolutePath + "/" + md5)
            val exists = file.exists() && file.isFile && file.length() > 0
            Log.d("Disk", "Loading image md5:%s exists:%s".format(file.absolutePath, exists))
            if (exists) {
                return BitmapFactory.decodeFile(file.absolutePath)
            }
        }
        return null
    }

    private fun saveImage(image: Bitmap, md5: String) {
        try {
            val path = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            path.exec {
                var file = File(path.absolutePath + "/" + md5)
                image.compress(Bitmap.CompressFormat.JPEG, 85, FileOutputStream(file));
                Log.d("Disk", "Saving image md5:%s ".format(file.absolutePath))
            }
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }
}