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
import app.exhale.domain.Reminders
import app.exhale.ui.theme.ExColors
import app.exhale.ui.theme.exColors
import androidx.compose.ui.graphics.Color

/**
 * Posts reminders (SPEC §3.9). One channel per kind, so each can also be muted in Android's own
 * settings; one notification per kind, so a newer one replaces the last. Sound and vibration are
 * the phone's own: Exhale's haptic patterns each mean something in the app and aren't reused here.
 */
object ReminderNotifier {

    /**
     * How one kind shows: its channel, and the accent that tints its small icon (craving before a busy
     * hour, recovery for a milestone, money for the goal, in between for the plan; ink tones, so they
     * read on a light shade too).
     */
    private class Spec(
        val channelId: String,
        val name: String,
        val description: String,
        val importance: Int,
        val category: String,
        val accent: (ExColors) -> Color,
    )

    private fun spec(kind: ReminderKind) = when (kind) {
        ReminderKind.BUSY_HOUR -> Spec(
            "busy_hours", "Before your busy hours",
            "A nudge ${Reminders.BUSY_LEAD.toMinutes()} minutes before the hours you usually smoke most.",
            NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.CATEGORY_REMINDER, ExColors::flareInk,
        )
        ReminderKind.MILESTONE -> Spec(
            "milestones", "Recovery milestones", "The first time you reach each milestone on Air.",
            NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.CATEGORY_STATUS, ExColors::oxyInk,
        )
        ReminderKind.GOAL -> Spec(
            "savings_goal", "Savings goal", "When what you've kept covers what you're saving for.",
            NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.CATEGORY_STATUS, ExColors::sunInk,
        )
        // Quiet: it says what the day's plan is, nothing needs doing at that moment.
        ReminderKind.PLAN_WEEK -> Spec(
            "plan_weeks", "New plan weeks", "When a new week of your plan lowers the allowance.",
            NotificationManager.IMPORTANCE_LOW, NotificationCompat.CATEGORY_STATUS, ExColors::skyInk,
        )
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
            ReminderKind.entries.map { spec(it) }.map { NotificationChannel(it.channelId, it.name, it.importance).apply { description = it.description } },
        )
    }

    // Permission is checked through allowed() first: on Android 13+ it is false without it.
    @SuppressLint("MissingPermission")
    fun post(context: Context, r: Reminder) {
        if (!allowed(context)) return
        ensureChannels(context)
        val spec = spec(r.kind)
        // Tapping opens the screen for its kind (see ExhaleNav).
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN)
            .putExtra(MainActivity.EXTRA_OPEN, r.kind.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(
            context, r.kind.ordinal, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, spec.channelId)
            .setSmallIcon(R.drawable.ic_ember)
            .setColor(spec.accent(exColors(dark = false)).toArgb())
            .setContentTitle(r.title)
            .setContentText(r.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setWhen(r.at.toEpochMilli())
            .setShowWhen(true)
            // A hidden lock screen shows only "Exhale" and that there is something.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(spec.category)
            .build()
        NotificationManagerCompat.from(context).notify(r.kind.ordinal + 1, n)
    }
}
