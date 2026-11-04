package com.pillreminder.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Виджет рабочего стола: ближайший приём лекарства. */
class NextDoseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        appScope.launch {
            try {
                update(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun update(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NextDoseWidget::class.java))
            if (ids.isEmpty()) return
            val next = AppDb.get(ctx).dao().nextPending(System.currentTimeMillis() - 2 * 60 * 60_000L)
            val text = if (next == null) {
                "Приёмов нет"
            } else {
                val day = dateOf(next.dose.scheduledAt)
                val prefix = when (day) {
                    LocalDate.now() -> ""
                    LocalDate.now().plusDays(1) -> "завтра "
                    else -> "${day.dayOfMonth}.${day.monthValue} "
                }
                "$prefix${fmtTime(next.dose.scheduledAt)} · ${next.medicineName}"
            }
            val open = PendingIntent.getActivity(
                ctx, 2, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(ctx.packageName, R.layout.widget_next_dose)
            views.setTextViewText(R.id.w_text, text)
            views.setOnClickPendingIntent(R.id.w_root, open)
            mgr.updateAppWidget(ids, views)
        }
    }
}
