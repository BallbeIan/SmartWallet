package app.smartwallet

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.smartwallet.data.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Settings and scheduling for scheduled-payment reminders. */
object Reminders {
    private const val PREFS = "reminders"
    private const val WORK = "payment-reminders"
    const val CHANNEL = "payment_reminders"
    private const val REMIND_HOUR = 9

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun leadDays(c: Context) = prefs(c).getInt("lead_days", 1)
    fun setLeadDays(c: Context, d: Int) = prefs(c).edit().putInt("lead_days", d).apply()

    fun hasPermission(c: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("enabled", on).apply()
        if (on) { schedule(c); runNow(c) } else WorkManager.getInstance(c).cancelUniqueWork(WORK)
    }

    /** Once a day at 09:00; KEEP so reopening the app doesn't reset the timer. */
    fun schedule(c: Context) {
        if (!enabled(c)) return
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(REMIND_HOUR, 0)
        if (!first.isAfter(now)) first = first.plusDays(1)
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Checks immediately. [force] ignores the "already reminded" memory, for testing. */
    fun runNow(c: Context, force: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("force" to force)).build()
        WorkManager.getInstance(c).enqueue(req)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Money.load(applicationContext)
        val db = WalletDb.get(applicationContext)
        processDueRecurring(db) // keep History current even if the app wasn't opened
        reconcileMonthEnd(db, applicationContext)
        if (Reminders.enabled(applicationContext) && Reminders.hasPermission(applicationContext)) notifyUpcoming(db, inputData.getBoolean("force", false))
        return Result.success()
    }

    private suspend fun notifyUpcoming(db: WalletDb, force: Boolean) {
        val c = applicationContext
        val today = LocalDate.now()
        val horizon = today.plusDays(Reminders.leadDays(c).toLong())
        val upcoming = db.dao().recurringUpcoming(today.toEpochDay(), horizon.toEpochDay())

        // Remember what was already announced so a payment is reminded once per occurrence.
        val prefs = c.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val sent = (if (force) emptySet() else prefs.getStringSet("sent", emptySet()).orEmpty()).toMutableSet()
        val keys = upcoming.map { "${it.id}:${it.nextDate}" }.toSet()
        sent.retainAll(keys)

        val nm = NotificationManagerCompat.from(c)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(NotificationChannelCompat.Builder(Reminders.CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("Payment reminders").build())
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        for (r in upcoming) {
            val key = "${r.id}:${r.nextDate}"
            if (key in sent) continue
            val days = r.nextDate - today.toEpochDay()
            val whenText = when (days) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $days days" }
            val n = NotificationCompat.Builder(c, Reminders.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_wallet)
                .setContentTitle("${r.name} is due $whenText")
                .setContentText(Money.format(kotlin.math.abs(r.amountCents)))
                .setContentIntent(open).setAutoCancel(true).build()
            // Permission was checked in doWork, but it can be revoked at any moment; a failed notify must not crash the worker.
            try { nm.notify(r.id.toInt(), n) } catch (_: SecurityException) { return }
            sent.add(key)
        }
        prefs.edit().putStringSet("sent", sent).apply()
    }
}
