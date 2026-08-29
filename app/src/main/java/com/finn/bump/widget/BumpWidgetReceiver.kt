package com.finn.bump.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BumpWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = BumpWidget()

    /**
     * The displayed value changes exactly once a day, at midnight. `ACTION_DATE_CHANGED`
     * is one of the implicit broadcasts still delivered to manifest-declared receivers,
     * so the platform does the scheduling and the app needs no alarm or worker of its
     * own. `updatePeriodMillis` in the widget XML is the backstop.
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_DATE_CHANGED) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    refreshBumpWidget(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
