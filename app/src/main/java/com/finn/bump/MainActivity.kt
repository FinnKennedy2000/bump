package com.finn.bump

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finn.bump.ui.BumpApp
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.theme.MoonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MoonTheme {
                val vm: BumpViewModel = viewModel()
                BumpApp(vm)
            }
        }
    }
}
