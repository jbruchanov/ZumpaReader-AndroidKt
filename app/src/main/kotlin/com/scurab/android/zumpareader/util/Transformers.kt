package com.scurab.android.zumpareader.util

import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import retrofit2.HttpException
import java.net.HttpURLConnection

/**
 * Created by JBruchanov on 27/12/2017.
 */
object RxTransformers {

    fun <T> zumpaRedirectHandler(): ObservableTransformer<T, Boolean> {
        return ObservableTransformer { result ->
            result
                    .map {
                        true
                    }
                    .onErrorResumeNext { err: Throwable ->
                        if ((err as? HttpException)?.code() == HttpURLConnection.HTTP_MOVED_TEMP) {
                            Observable.just(true)
                        } else {
                            Observable.error<Boolean>(err)
                        }
                    }
        }
    }
}