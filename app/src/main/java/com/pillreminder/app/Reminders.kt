package com.pillreminder.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Логика напоминаний.
 *
 * Для каждого приёма хранится nextAt - время ближайшего напоминания.
 * Схема: напоминание в назначенное время, затем ещё 3 повтора с шагом 30 минут
 * (всего 4 показа). Если после последнего показа приём не подтверждён
 * ещё 30 минут - он помечается как пропущенный.
 *
 * Будильник всегда один: он ставится на ближайшее nextAt среди ожидающих приёмов.
 */
object Reminders {
    const val CHANNEL = "meds_reminders"
    const val EXTRA_DOSE = "doseId"
    const val ACTION_TAKE = "com.pillreminder.app.TAKE"
    const val ACTION_SNOOZE = "com.pillreminder.app.SNOOZE"
    const val STEP_MS = 30L * 60_000L
    const val MAX_SHOWS = 4

    private val lock = Mutex()

    fun nid(doseId: Long): Int = (doseId % 100_000_000L).toInt()

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(
                    CHANNEL, "Напоминания о лекарствах", NotificationManager.IMPORTANCE_HIGH
                )
                ch.description = "Уведомления о времени приёма лекарств"
                ch.enableVibration(true)
                ch.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                nm.createNotificationChannel(ch)
            }
        }
    }

    /** Обрабатывает все наступившие напоминания и ставит будильник на следующее. */
    suspend fun run(context: Context) = lock.withLock {
        val ctx = context.applicationContext
        val dao = AppDb.get(ctx).dao()
        val now = System.currentTimeMillis()
        var next = Long.MAX_VALUE

        for (d in dao.pendingDoses()) {
            if (d.nextAt > now) {
                next = minOf(next, d.nextAt)
                break
            }
            val stale = d.remindCount == 0 && now - d.scheduledAt > 4 * STEP_MS
            if (d.remindCount >= MAX_SHOWS || stale) {
                dao.updateDose(d.copy(status = Status.MISSED))
                NotificationManagerCompat.from(ctx).cancel(nid(d.id))
                continue
            }
            val shows = d.remindCount + 1
            dao.updateDose(d.copy(remindCount = shows, nextAt = now + STEP_MS))
            dao.getDoseView(d.id)?.let { notifyDose(ctx, it) }
            next = minOf(next, now + STEP_MS)
        }
        schedule(ctx, next)
    }

    private fun schedule(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (at == Long.MAX_VALUE) {
            am.cancel(pi)
            return
        }
        val show = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Точный будильник; если система отказала (нет разрешения) - запасной вариант,
        // чтобы приложение никогда не падало из-за планирования.
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } catch (e: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: SecurityException) {
            }
        }
    }

    private fun notifyDose(ctx: Context, v: DoseView) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)

        val id = nid(v.dose.id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            ctx, id * 4,
            Intent(ctx, ReminderActivity::class.java)
                .putExtra(EXTRA_DOSE, v.dose.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        val take = PendingIntent.getBroadcast(
            ctx, id * 4 + 1,
            Intent(ctx, ActionReceiver::class.java).setAction(ACTION_TAKE).putExtra(EXTRA_DOSE, v.dose.id),
            flags
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, id * 4 + 2,
            Intent(ctx, ActionReceiver::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_DOSE, v.dose.id),
            flags
        )

        var text = "Приём в ${fmtTime(v.dose.scheduledAt)}"
        if (v.regimen.isNotBlank()) text += " · ${v.regimen}"
        if (v.dose.remindCount > 1) text += " · напоминание ${v.dose.remindCount} из $MAX_SHOWS"

        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Пора принять: ${v.medicineName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "✔ Принял", take)
        if (v.dose.remindCount < MAX_SHOWS) {
            b.addAction(0, "Отложить на 30 мин", snooze)
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (_: SecurityException) {
        }
    }

    /** Ручная или по кнопке отметка приёма. */
    suspend fun setStatus(ctx: Context, doseId: Long, status: Int) {
        val dao = AppDb.get(ctx).dao()
        val d = dao.getDose(doseId) ?: return
        dao.updateDose(
            d.copy(status = status, takenAt = if (status == Status.TAKEN) System.currentTimeMillis() else null)
        )
        NotificationManagerCompat.from(ctx).cancel(nid(doseId))
        run(ctx)
    }

    /** Отложить: следующее напоминание ровно через 30 минут от нажатия. */
    suspend fun snooze(ctx: Context, doseId: Long) {
        val dao = AppDb.get(ctx).dao()
        val d = dao.getDose(doseId) ?: return
        if (d.status == Status.PENDING) {
            dao.updateDose(d.copy(nextAt = System.currentTimeMillis() + STEP_MS))
        }
        NotificationManagerCompat.from(ctx).cancel(nid(doseId))
        run(ctx)
    }
}
