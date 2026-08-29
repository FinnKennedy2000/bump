package com.finn.bump.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.ui.screens.KicksScreen
import com.finn.bump.ui.screens.LogScreen
import com.finn.bump.ui.screens.NowScreen
import com.finn.bump.ui.screens.OnboardingScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Now("Now", Icons.Filled.Favorite),
    Log("Log", Icons.Filled.EditNote),
    Kicks("Kicks", Icons.Filled.ChildCare),
}

@Composable
fun BumpApp(vm: BumpViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Null means the first DataStore read hasn't landed yet. Render nothing rather
    // than flashing onboarding at someone who already has a due date saved.
    val current = state ?: return

    if (current.dueDate == null) {
        OnboardingScreen(
            onDueDate = vm::setDueDate,
            onLmp = vm::setDueDateFromLmp,
        )
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (Tab.entries[tab]) {
            Tab.Now -> NowScreen(current, vm.weeks, modifier)
            Tab.Log -> LogScreen(current, vm, snackbar, modifier)
            Tab.Kicks -> KicksScreen(current, vm, snackbar, modifier)
        }
    }
}
