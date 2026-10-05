package com.finn.bump.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.measuredOn
import com.finn.bump.domain.Pregnancy
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.screens.ChecklistScreen
import com.finn.bump.ui.screens.ContractionsScreen
import com.finn.bump.ui.screens.JourneyScreen
import com.finn.bump.ui.screens.KicksScreen
import com.finn.bump.ui.screens.LogScreen
import com.finn.bump.ui.screens.OnboardingScreen
import com.finn.bump.ui.screens.SettingsScreen
import com.finn.bump.ui.screens.SizeChartScreen
import com.finn.bump.ui.screens.TodayScreen
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import java.time.LocalDate

/**
 * Three tabs, because there are three things you do: see where you are, look along the
 * pregnancy, and write something down. Counting kicks and the size chart are full-screen
 * actions you open and close — they used to be a tab and a screen respectively.
 */
private enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Filled.Schedule),
    Journey("Journey", Icons.Filled.Timeline),
    Log("Log", Icons.AutoMirrored.Filled.List),
}

/** A screen that covers the tabs while it is open. */
private enum class Overlay { Kicks, Contractions, Checklist, SizeChart, Settings }

@Composable
fun BumpApp(vm: BumpViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Null means the first DataStore read hasn't landed yet. Render nothing rather
    // than flashing onboarding at someone who already has a due date saved.
    val current = state ?: return

    if (current.dueDate == null) {
        OnboardingScreen(vm)
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Saved by name: an enum is only Bundle-savable by accident, and losing the kick
    // counter to a rotation is exactly what this is here to prevent.
    var overlayName by rememberSaveable { mutableStateOf<String?>(null) }
    val overlay = overlayName?.let(Overlay::valueOf)
    val snackbar = remember { SnackbarHostState() }

    when (overlay) {
        Overlay.Kicks -> {
            KicksScreen(current, vm, onClose = { overlayName = null })
            return
        }

        Overlay.Contractions -> {
            ContractionsScreen(current, vm, onClose = { overlayName = null })
            return
        }

        Overlay.Checklist -> {
            ChecklistScreen(current, vm, onClose = { overlayName = null })
            return
        }

        Overlay.SizeChart -> {
            SizeChartScreen(
                weeks = vm.weeks,
                currentWeek = Pregnancy.week(current.dueDate!!, current.measuredOn(LocalDate.now())),
                dueDate = current.dueDate!!,
                photos = current.photos,
                onBack = { overlayName = null },
            )
            return
        }

        Overlay.Settings -> {
            SettingsScreen(current, vm, onBack = { overlayName = null })
            return
        }

        null -> Unit
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomBar(tab) { tab = it } },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (Tab.entries[tab]) {
            Tab.Today -> TodayScreen(
                state = current,
                vm = vm,
                onCountKicks = { overlayName = Overlay.Kicks.name },
                onContractions = { overlayName = Overlay.Contractions.name },
                onBag = { overlayName = Overlay.Checklist.name },
                onEveryWeek = { overlayName = Overlay.SizeChart.name },
                onSettings = { overlayName = Overlay.Settings.name },
                modifier = modifier,
            )

            Tab.Journey -> JourneyScreen(
                state = current,
                vm = vm,
                onEveryWeek = { overlayName = Overlay.SizeChart.name },
                modifier = modifier,
            )

            Tab.Log -> LogScreen(current, vm, snackbar, modifier)
        }
    }
}

/** A hairline and three labels. Material's NavigationBar brings a tonal slab this design has no room for. */
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.background)
            // Scaffold does not inset a hand-rolled bottom bar the way NavigationBar
            // insets itself, and the activity is edge to edge.
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Rule()
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Spacing.p3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEachIndexed { index, t ->
                val on = index == selected
                val tint = if (on) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalBumpExtras.current.label
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClickLabel = t.label) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(t.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(
                        t.label,
                        style = if (on) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        color = tint,
                    )
                }
            }
        }
    }
}
