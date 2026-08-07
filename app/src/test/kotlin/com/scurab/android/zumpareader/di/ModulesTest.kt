package com.scurab.android.zumpareader.di

import android.content.Context
import com.scurab.android.zumpareader.ZumpaAPI
import com.scurab.android.zumpareader.ZumpaPHPAPI
import com.scurab.android.zumpareader.ZumpaWSAPI
import com.scurab.android.zumpareader.arch.DeviceConfig
import com.scurab.android.zumpareader.util.ZumpaPrefs
import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.koin.test.verify.verify

/**
 * Without this a missing binding is a crash at first injection instead of a build failure, which is
 * the one thing a hand-written koin graph cannot tell you on its own.
 *
 * Only [viewModelModule] is verified. Koin's `verify()` works by reflecting over the declared type's
 * *constructor*, so it can only check definitions of the `single { Foo(get(), get()) }` shape.
 * [coreModule] and [networkModule] are built out of factory functions and retrofit `create()` calls
 * whose declared types are interfaces, which `verify()` cannot analyse. [viewModelModule] is the one
 * that grows a line per screen during the mvvm migration, so it is the one worth guarding.
 */
class ModulesTest {

    @Test
    fun `view model module resolves every dependency it declares`() {
        viewModelModule.verify(
            extraTypes = listOf(
                // provided by coreModule / networkModule
                Context::class,
                Gson::class,
                DeviceConfig::class,
                ZumpaPrefs::class,
                ZumpaAPI::class,
                ZumpaWSAPI::class,
                ZumpaPHPAPI::class,
                // androidx plumbing koin injects into a ViewModel definition
                androidx.lifecycle.SavedStateHandle::class,
            )
        )
    }
}
