package nl.weerenwind.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import nl.weerenwind.MainActivity
import nl.weerenwind.R
import nl.weerenwind.data.Repo
import nl.weerenwind.logic.Alerts
import nl.weerenwind.logic.Units
import nl.weerenwind.widget.WeatherWidget
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Draait elke 30 minuten, alleen met internet en alleen als een melding aanstaat.
 * Haalt radar (plek A/B) en waarschuwingen op — niet de hele verwachting — en meldt:
 *  - regen binnen [Settings.alertLeadMin] minuten, als het nu droog is (max. 1 per plek per 90 min);
 *  - nieuwe KNMI-waarschuwingen code oranje of rood voor de provincie van je plekken.
 */
class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val s = Repo.loadSettings(ctx)
        if (!s.rainAlerts && !s.warnAlerts) return Result.success()
        val x = runCatching { Repo.refreshExtras(ctx, radar = s.rainAlerts, warnings = s.warnAlerts) }
            .getOrElse { return Result.retry() }
        WeatherWidget.updateAll(ctx)
        if (!canNotify(ctx)) return Result.success()
        val now = LocalDateTime.now(Repo.AMS)

        if (s.rainAlerts && !Alerts.quiet(now)) {
            for (p in Repo.slotPlaces(ctx)) {
                val alert = Alerts.rain(x.radar[p.id].orEmpty(), now, s.alertLeadMin) ?: continue
                val last = Repo.lastRainAlert(ctx, p.id)
                if (System.currentTimeMillis() - last < 90 * 60 * 1000) continue
                notify(ctx, CH_RAIN, p.id.hashCode(), "Regen bij ${p.name}", alert.text, TAB_RAIN)
                Repo.setLastRainAlert(ctx, p.id, System.currentTimeMillis())
            }
        }

        if (s.warnAlerts) {
            val places = Repo.loadPlaces(ctx)
            val done = Repo.notifiedWarnings(ctx).toMutableSet()
            val nowO = OffsetDateTime.now()
            for (w in x.warnings) {
                if (w.level < 2 || w.id in done) continue
                if (w.expires != null && w.expires.isBefore(nowO)) continue
                val hit = places.filter { w.appliesTo(it) }
                if (hit.isEmpty()) continue
                val hm = DateTimeFormatter.ofPattern("EEE HH:mm", Units.NL)
                val period = listOfNotNull(
                    w.onset?.atZoneSameInstant(Repo.AMS)?.format(hm)?.let { "vanaf $it" },
                    w.expires?.atZoneSameInstant(Repo.AMS)?.format(hm)?.let { "tot $it" },
                ).joinToString(" ")
                notify(ctx, CH_WARN, w.id.hashCode(), "${Alerts.codeName(w.level)} · ${w.event}",
                    "${w.area} (${hit.joinToString { it.name }}) $period".trim(), TAB_TODAY)
                done += w.id
            }
            Repo.setNotifiedWarnings(ctx, done)
        }
        return Result.success()
    }

    companion object {
        const val CH_RAIN = "regen"
        const val CH_WARN = "waarschuwingen"
        const val EXTRA_TAB = "tab"
        const val TAB_TODAY = 0
        const val TAB_RAIN = 3
        private const val WORK = "artyweather-alerts"

        /** Plant of stopt de achtergrondtaak, afhankelijk van de instellingen. */
        fun schedule(ctx: Context) {
            val s = Repo.loadSettings(ctx)
            val wm = WorkManager.getInstance(ctx)
            if (!s.rainAlerts && !s.warnAlerts) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val req = PeriodicWorkRequestBuilder<AlertWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun canNotify(ctx: Context): Boolean =
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(ctx).areNotificationsEnabled()

        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_RAIN, "Regen op komst", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Melding als de radar binnenkort regen ziet bij plek A of B" }
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_WARN, "KNMI-waarschuwingen", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Code oranje en rood voor de provincie van je plekken" }
            )
        }

        private fun notify(ctx: Context, channel: String, id: Int, title: String, text: String, tab: Int) {
            createChannels(ctx)
            val intent = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TAB, tab)
            val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_stat_drop)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            try {
                NotificationManagerCompat.from(ctx).notify(id, n)
            } catch (_: SecurityException) {
            }
        }
    }
}
