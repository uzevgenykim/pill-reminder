package com.pillreminder.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Срабатывает по будильнику: показывает напоминания и ставит следующий будильник. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        async { Reminders.run(context.applicationContext) }
    }
}

/** После перезагрузки, смены времени или обновления приложения восстанавливает будильник. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        async { Reminders.run(context.applicationContext) }
    }
}

/** Кнопки «Принял» и «Отложить» в уведомлении. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Reminders.EXTRA_DOSE, -1L)
        val action = intent.action
        if (id < 0) return
        async {
            val app = context.applicationContext
            when (action) {
                Reminders.ACTION_TAKE -> Reminders.setStatus(app, id, Status.TAKEN)
                Reminders.ACTION_SNOOZE -> Reminders.snooze(app, id)
            }
        }
    }
}
