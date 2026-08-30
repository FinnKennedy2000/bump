package com.finn.bump

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finn.bump.ui.BumpApp
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.theme.BumpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BumpTheme {
                val vm: BumpViewModel = viewModel()

                // Pull whatever the other phone added while this one was closed. Sync also
                // runs after every write; resume covers the case where the writing happened
                // somewhere else entirely.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.syncNow() }

                BumpApp(vm)
            }
        }
    }
}
