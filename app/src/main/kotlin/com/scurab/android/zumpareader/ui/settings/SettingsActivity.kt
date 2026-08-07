package com.scurab.android.zumpareader.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.scurab.android.zumpareader.ui.compose.ActivityNavigator
import com.scurab.android.zumpareader.ui.compose.LocalNavigator
import com.scurab.android.zumpareader.ui.compose.theme.AppTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Created by JBruchanov on 29/12/2015.
 *
 * Was the last `android.preference.PreferenceActivity` in the app. It is a compose host now, and
 * `androidx.preference` was never introduced - which is the whole reason this screen was left until
 * the end of the compose migration rather than converted twice.
 */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModel()

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onResumed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalNavigator provides ActivityNavigator(this)) {
                    SettingsScreen(
                        onRequestNotificationPermission = {
                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onOpenAppSettings = ::openAppSettings,
                        vm = viewModel,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //the permission can be changed outside the app
        viewModel.onResumed()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            )
        )
    }
}
