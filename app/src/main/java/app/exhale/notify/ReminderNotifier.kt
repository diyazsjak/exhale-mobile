package app.exhale.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.exhale.MainActivity
import app.exhale.R
import app.exhale.domain.Reminder
import app.exhale.domain.ReminderKind
import app.exhale.ui.OpenTarget
import app.exhale.ui.theme.exColors

/**
 * Posts reminders (SPEC §3.9). One channel per kind, so each can also be muted in Android's own
 * settings; one notification per kind, so a newer one replaces the last. Sound and vibration are
 * the phone's own: Exhale's haptic patterns each mean something in the app and aren't reused here.
 */
object ReminderNotifier {

    private fun channelId(kind: ReminderKind) = when (kind) {
        ReminderKind.BUSY_HOUR -> "busy_hours"
        ReminderKind.MILESTONE -> "milestones"
        ReminderKind.GOAL -> "savings_goal"
        ReminderKind.PLAN_WEEK -> "plan_weeks"
    }

    /** Where tapping it goes: a busy-hour nudge to Urge Wave, a milestone to Air, the goal to Money, a plan week to Ember. */
    fun target(kind: ReminderKind) = when (kind) {
        ReminderKind.BUSY_HOUR -> OpenTarget.WAVE
        ReminderKind.MILESTONE -> OpenTarget.AIR
        ReminderKind.GOAL -> OpenTarget.MONEY
        ReminderKind.PLAN_WEEK -> OpenTarget.EMBER
    }

    /** Whether Android lets Exhale post notifications: on Android 13+ that needs the permission too. */
    fun allowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Whether asking for the notification permission can help: Android 13+ and not granted yet. */
    fun canAsk(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Android's notification settings for Exhale. */
    fun settingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(channelId(ReminderKind.BUSY_HOUR), "Before your busy hours", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "A nudge 15 minutes before the hours you usually smoke most." },
                NotificationChannel(channelId(ReminderKind.MILESTONE), "Recovery milestones", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "The first time you reach each milestone on Air." },
                NotificationChannel(channelId(ReminderKind.GOAL), "Savings goal", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "When what you've kept covers what you're saving for." },
                // Quiet: it says what the day's plan is, nothing needs doing at that moment.
                NotificationChannel(channelId(ReminderKind.PLAN_WEEK), "New plan weeks", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "When a new week of your plan lowers the allowance." },
            ),
        )
    }

    // Permission is checked through allowed() first: on Android 13+ it is false without it.
    @SuppressLint("MissingPermission")
    fun post(context: Context, r: Reminder) {
        if (!allowed(context)) return
        ensureChannels(context)
        // Accents tint the small icon: craving (flare) before a busy hour, recovery (oxygen) for a
        // milestone, money (sun) for the goal, in between (sky) for the plan. Ink tones, so they read on a light shade too.
        val c = exColors(dark = false)
        val accent = when (r.kind) {
            ReminderKind.BUSY_HOUR -> c.flareInk
            ReminderKind.MILESTONE -> c.oxyInk
            ReminderKind.GOAL -> c.sunInk
            ReminderKind.PLAN_WEEK -> c.skyInk
        }
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN)
            .putExtra(MainActivity.EXTRA_OPEN, target(r.kind).name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(
            context, r.kind.ordinal, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channelId(r.kind))
            .setSmallIcon(R.drawable.ic_ember)
            .setColor(accent.toArgb())
            .setContentTitle(r.title)
            .setContentText(r.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setWhen(r.at.toEpochMilli())
            .setShowWhen(true)
            // A hidden lock screen shows only "Exhale" and that there is something.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(if (r.kind == ReminderKind.BUSY_HOUR) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(context).notify(r.kind.ordinal + 1, n)
    }
}
