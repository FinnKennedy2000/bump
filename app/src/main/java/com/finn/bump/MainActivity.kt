package com.finn.bump

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finn.bump.notify.AppVisibility
import com.finn.bump.notify.EXTRA_OPEN
import com.finn.bump.notify.OPEN_NAMES
import com.finn.bump.ui.BumpApp
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.theme.BumpTheme

class MainActivity : ComponentActivity() {

    /** Set when a name notification was tapped: open straight onto the names screen. */
    private var openNames by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only a fresh launch from the notification: a rotation, a theme change or a launch
        // from recents re-delivers the same intent, and must not reopen names each time.
        val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) takeOpenNames(intent)
        setContent {
            BumpTheme {
                val vm: BumpViewModel = viewModel()

                // Pull whatever the other phone added while this one was closed. Sync also
                // runs after every write; resume covers the case where the writing happened
                // somewhere else entirely.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.syncNow() }

                BumpApp(vm, openNames = openNames, onOpened = { openNames = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeOpenNames(intent)
    }

    private fun takeOpenNames(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_NAMES) {
            openNames = true
            intent.removeExtra(EXTRA_OPEN)
        }
    }

    override fun onResume() {
        super.onResume()
        AppVisibility.inForeground = true
    }

    override fun onPause() {
        AppVisibility.inForeground = false
        super.onPause()
    }
}
