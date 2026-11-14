package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.util.AppLanguage
import com.example.data.util.AppThemeMode
import com.example.data.util.LocalNotificationHelper
import com.example.data.util.PreferencesManager
import com.example.ui.navigation.BigBazerAppRoot
import com.example.ui.theme.BigBazerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LocalNotificationHelper.ensureChannels(applicationContext)

        setContent {
            val prefs = remember { PreferencesManager(applicationContext) }
            val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
            val language by prefs.language.collectAsStateWithLifecycle(initialValue = AppLanguage.ENGLISH)

            BigBazerTheme(themeMode = themeMode) {
                BigBazerAppRoot(
                    language = language,
                    themeMode = themeMode,
                    prefsManager = prefs
                )
            }
        }
    }
}
