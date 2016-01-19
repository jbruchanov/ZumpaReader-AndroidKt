package com.scurab.android.zumpareader.gson

import com.google.gson.*

/**
 * Created by JBruchanov on 18/01/2016.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
public annotation class GsonExclude


public class GsonExcludeStrategy : ExclusionStrategy {
    override fun shouldSkipClass(type: Class<*>): Boolean = false
    override fun shouldSkipField(f: FieldAttributes): Boolean {
        return f.getAnnotation(GsonExclude::class.java) != null
                || f.name.endsWith("\$delegate")
    }
}