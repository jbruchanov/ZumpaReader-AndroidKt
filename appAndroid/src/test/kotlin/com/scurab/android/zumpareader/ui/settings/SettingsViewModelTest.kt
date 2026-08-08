package com.scurab.android.zumpareader.ui.settings

import com.scurab.android.zumpareader.repository.AuthRepository
import com.scurab.android.zumpareader.repository.ZumpaSettingsRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsViewModelTest {

    //stand-ins for the preference-backed flows, so a "write" only reaches the ui state when the
    //test says it does - which is the point of the first few tests here
    private val userName = MutableStateFlow("")
    private val password = MutableStateFlow("")
    private val nick = MutableStateFlow("")
    private val filter = MutableStateFlow("0")
    private val isOffline = MutableStateFlow(false)
    private val loadImages = MutableStateFlow(true)
    private val showLastAuthor = MutableStateFlow(false)
    private val isLoggedIn = MutableStateFlow(false)

    private val settings = mockk<ZumpaSettingsRepository>(relaxed = true) {
        every { this@mockk.userName } returns this@SettingsViewModelTest.userName
        every { this@mockk.password } returns this@SettingsViewModelTest.password
        every { this@mockk.nick } returns this@SettingsViewModelTest.nick
        every { this@mockk.filter } returns this@SettingsViewModelTest.filter
        every { this@mockk.isOffline } returns this@SettingsViewModelTest.isOffline
        every { this@mockk.loadImages } returns this@SettingsViewModelTest.loadImages
        every { this@mockk.showLastAuthor } returns this@SettingsViewModelTest.showLastAuthor
        every { this@mockk.isLoggedIn } returns this@SettingsViewModelTest.isLoggedIn
        every { userId } returns null
    }
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val notifications = mockk<NotificationState> {
        every { areEnabled() } returns false
        every { canRequestPermission() } returns false
    }

    private fun viewModel() = SettingsViewModel(settings, auth, notifications)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    //region typing
    /**
     * The regression these three guard: the fields used to hand the value straight to the
     * preferences and wait for it to come back through a `callbackFlow` on another dispatcher. Until
     * it did, the field still rendered the previous value, and a value landing after later keystrokes
     * reset the text field's selection - the caret jumped to the end mid-word.
     *
     * `password.value` is deliberately never updated here, so a state that already holds the typed
     * text can only have come from the synchronous update.
     */
    @Test
    fun `a typed password reaches the ui state without waiting for the preferences round trip`() = runTest {
        val viewModel = viewModel()

        viewModel.onPasswordChanged("hunter2")

        assertEquals("hunter2", viewModel.uiState.value.password)
    }

    @Test
    fun `a typed user name reaches the ui state without waiting for the preferences round trip`() = runTest {
        val viewModel = viewModel()

        viewModel.onUserNameChanged("someone")

        assertEquals("someone", viewModel.uiState.value.userName)
    }

    @Test
    fun `a typed nick reaches the ui state without waiting for the preferences round trip`() = runTest {
        val viewModel = viewModel()

        viewModel.onNickChanged("nickname")

        assertEquals("nickname", viewModel.uiState.value.nick)
    }

    @Test
    fun `every keystroke of a fast burst is kept in order`() = runTest {
        val viewModel = viewModel()

        "hunter2".forEachIndexed { index, _ ->
            viewModel.onPasswordChanged("hunter2".substring(0, index + 1))
        }

        assertEquals("hunter2", viewModel.uiState.value.password)
    }

    @Test
    fun `the typed value is still persisted`() = runTest {
        justRun { settings.setPassword(any()) }
        val viewModel = viewModel()

        viewModel.onPasswordChanged("hunter2")

        verify(exactly = 1) { settings.setPassword("hunter2") }
    }

    @Test
    fun `an echo from the preferences carrying the same value leaves the state alone`() = runTest {
        val viewModel = viewModel()
        viewModel.onPasswordChanged("hunter2")

        //what the shared preference listener does once the write lands
        password.value = "hunter2"

        assertEquals("hunter2", viewModel.uiState.value.password)
    }

    @Test
    fun `a password changed somewhere else still reaches the screen`() = runTest {
        val viewModel = viewModel()

        password.value = "set elsewhere"

        assertEquals("set elsewhere", viewModel.uiState.value.password)
    }
    //endregion

    //region password visibility
    @Test
    fun `the password starts hidden`() = runTest {
        assertFalse(viewModel().uiState.value.isPasswordVisible)
    }

    @Test
    fun `the eye reveals and hides the password again`() = runTest {
        val viewModel = viewModel()

        viewModel.onPasswordVisibilityToggled()
        assertTrue(viewModel.uiState.value.isPasswordVisible)

        viewModel.onPasswordVisibilityToggled()
        assertFalse(viewModel.uiState.value.isPasswordVisible)
    }

    @Test
    fun `revealing the password does not persist anything`() = runTest {
        val viewModel = viewModel()

        viewModel.onPasswordVisibilityToggled()

        verify(exactly = 0) { settings.setPassword(any()) }
    }

    @Test
    fun `typing while the password is visible keeps it visible`() = runTest {
        val viewModel = viewModel()
        viewModel.onPasswordVisibilityToggled()

        viewModel.onPasswordChanged("hunter2")

        assertTrue(viewModel.uiState.value.isPasswordVisible)
        assertEquals("hunter2", viewModel.uiState.value.password)
    }
    //endregion
}
