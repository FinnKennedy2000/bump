package com.finn.bump.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.finn.bump.MainActivity
import com.finn.bump.R
import com.finn.bump.data.Bump
import com.finn.bump.data.NameNews
import com.finn.bump.data.nameNews
import com.finn.bump.data.sync.SyncSettings
import com.finn.bump.widget.refreshBumpWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** The intent extra that opens the app straight onto the names screen. */
const val EXTRA_OPEN = "com.finn.bump.OPEN"
const val OPEN_NAMES = "names"

private const val CHANNEL = "names"
private const val NOTIFICATION_ID = 1
private const val JOB_ID = 1

/**
 * The app's one piece of background work: about every 15 minutes, while paired and on a
 * network, sync and tell you what your partner has done with names.
 *
 * The platform's own JobScheduler rather than WorkManager or a push service — one
 * periodic job does not need a library, and push would mean a third-party SDK. Fifteen
 * minutes is the platform's floor for periodic jobs; the system may stretch it further
 * in Doze, which is fine for news about names.
 */
class NameCheckJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                Bump.syncer(applicationContext).sync()
                refreshBumpWidget(applicationContext)
                checkNames(applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed round is retried at the next period; nothing is lost by waiting.
                Log.w("Bump", "Background name check failed", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.cancel()
        return true
    }
}

/** Start the periodic check. Called whenever the phone is paired; harmless to repeat. */
fun scheduleNameChecks(context: Context) {
    val scheduler = context.getSystemService(JobScheduler::class.java)
    if (scheduler.getPendingJob(JOB_ID) != null) return
    scheduler.schedule(
        JobInfo.Builder(JOB_ID, ComponentName(context, NameCheckJob::class.java))
            .setPeriodic(TimeUnit.MINUTES.toMillis(15))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
    )
}

/** Unpaired there is no partner to hear from, and the app goes back to no background work. */
fun cancelNameChecks(context: Context) {
    context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
}

/**
 * Compare the record with what this phone last told you about, and say what is new.
 * Quiet while the app is open — you are looking at it — but the seen mark still moves on.
 */
suspend fun checkNames(context: Context) {
    val settings = SyncSettings(context)
    val me = settings.voterId()
    val state = Bump.repository(context).rawState.first()
    val known = Bump.nameTable(context).all.mapTo(HashSet()) { it.name.lowercase() }
    val seenAt = settings.namesSeenAt.first()
    val news = nameNews(state.nameVotes, me, known, seenAt)
    if (seenAt == 0L) {
        // The first check on a phone has no mark yet: take everything already there as
        // seen rather than announcing every name the partner ever liked — and mark *now*,
        // not the newest vote, so a partner with no votes yet still leaves a mark and
        // their first likes after this are news.
        settings.setNamesSeenAt(maxOf(news.newest, System.currentTimeMillis()))
        return
    }
    if (!news.isEmpty && !AppVisibility.inForeground) post(context, news)
    settings.setNamesSeenAt(news.newest)
}

/** Opening the names screen counts as having seen everything on it. */
suspend fun markNamesSeen(context: Context) {
    val settings = SyncSettings(context)
    val me = settings.voterId()
    val newest = Bump.repository(context).rawState.first().nameVotes
        .filter { it.voter != me }.maxOfOrNull { it.updatedAt } ?: return
    settings.setNamesSeenAt(newest)
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}

private fun post(context: Context, news: NameNews) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL, "Baby names", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When your partner likes or suggests a name"
        }
    )

    val (title, text) = words(news)
    val open = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_NAMES)
            // SINGLE_TOP: hand the running app the intent rather than recreate it, which
            // would clear the ViewModel and with it a contraction being timed.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    NotificationManagerCompat.from(context).notify(
        NOTIFICATION_ID,
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build(),
    )
}

/** Blind by design: matches and their own suggestions are named; their likes are only counted. */
internal fun words(news: NameNews): Pair<String, String> {
    val lines = buildList {
        if (news.suggested.isNotEmpty()) add("Your partner suggested ${list(news.suggested)}.")
        if (news.liked > 0) {
            add(
                if (news.liked == 1) "Your partner liked a new name. Swipe to see if you match."
                else "Your partner liked ${news.liked} new names. Swipe to see if you match."
            )
        }
    }
    return when {
        news.matches.isNotEmpty() -> "You both like ${list(news.matches)}" to
            (lines.firstOrNull() ?: "It's on your shared list.")
        news.suggested.isNotEmpty() -> "A name from your partner" to lines.joinToString(" ")
        else -> "New names from your partner" to lines.joinToString(" ")
    }
}

private fun list(names: List<String>): String = when (names.size) {
    1 -> names[0]
    2 -> "${names[0]} and ${names[1]}"
    else -> "${names.dropLast(1).joinToString(", ")} and ${names.last()}"
}

/** Set from MainActivity's resume and pause, so a check while you are in the app stays quiet. */
object AppVisibility {
    @Volatile var inForeground = false
}
