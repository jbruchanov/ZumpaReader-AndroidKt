package com.scurab.android.zumpareader.ui.main

import app.cash.turbine.test
import com.scurab.android.zumpareader.R
import com.scurab.android.zumpareader.arch.ShowSnackbar
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MainViewModelTest {

    private val isLoggedIn = MutableStateFlow(false)
    private val settings = mockk<ZumpaSettingsRepository> {
        every { this@mockk.isLoggedIn } returns this@MainViewModelTest.isLoggedIn
    }

    private fun viewModel() = MainViewModel(settings)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a push payload opens the thread`() = runTest {
        viewModel().run {
            effects.test {
                onLaunch(LaunchPayload(threadId = "12345"))
                assertEquals(MainEffect.OpenThread("12345"), awaitItem())
            }
        }
    }

    @Test
    fun `a push payload opens the thread even while logged out`() = runTest {
        isLoggedIn.value = false
        viewModel().run {
            effects.test {
                onLaunch(LaunchPayload(threadId = "1"))
                assertTrue(awaitItem() is MainEffect.OpenThread)
            }
        }
    }

    @Test
    fun `a share while logged out is refused with a toast`() = runTest {
        isLoggedIn.value = false
        viewModel().run {
            effects.test {
                onLaunch(LaunchPayload(text = "something to post"))
                assertEquals(ShowSnackbar(resId = R.string.err_login_first), awaitItem())
            }
        }
    }

    @Test
    fun `a share while logged in opens the post dialog`() = runTest {
        isLoggedIn.value = true
        viewModel().run {
            effects.test {
                onLaunch(LaunchPayload(subject = "subj", text = "body"))
                assertEquals(
                    MainEffect.OpenPostDialog("subj", "body", emptyList()),
                    awaitItem()
                )
            }
        }
    }

    @Test
    fun `a launch with nothing in it does nothing`() = runTest {
        isLoggedIn.value = true
        viewModel().run {
            effects.test {
                onLaunch(LaunchPayload())
                expectNoEvents()
            }
        }
    }

}
