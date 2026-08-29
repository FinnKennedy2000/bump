package com.finn.bump.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.finn.bump.MainActivity
import com.finn.bump.data.Bump
import com.finn.bump.domain.Pregnancy
import com.finn.bump.ui.theme.Moon
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.abs

/**
 * Holds no state of its own: it reads the same due date the app does and recomputes,
 * so it cannot drift out of step with the app or need its own migration.
 */
class BumpWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = Bump.repository(context).state.first()
        val weeks = Bump.weekTable(context)

        provideContent {
            GlanceTheme {
                val due = state.dueDate
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    if (due == null) {
                        Text(
                            "Set a due date",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        return@Column
                    }

                    val today = LocalDate.now()
                    val week = Pregnancy.week(due, today)
                    val toGo = Pregnancy.daysToGo(due, today)

                    Text(
                        "Week $week",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        weeks[week].size,
                        style = TextStyle(color = ColorProvider(Moon.Piccolo), fontWeight = FontWeight.Medium),
                    )
                    Text(
                        countdown(toGo),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    )
                    LinearProgressIndicator(
                        progress = Pregnancy.progress(due, today),
                        modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
                        color = ColorProvider(Moon.Piccolo),
                    )
                }
            }
        }
    }
}

private fun countdown(daysToGo: Int): String = when {
    daysToGo > 1 -> "$daysToGo days to go"
    daysToGo == 1 -> "1 day to go"
    daysToGo == 0 -> "Due today"
    abs(daysToGo) == 1 -> "1 day overdue"
    else -> "${abs(daysToGo)} days overdue"
}

/** Called whenever the due date changes, so the widget never shows a stale week. */
suspend fun refreshBumpWidget(context: Context) {
    BumpWidget().updateAll(context)
}
