package com.pillreminder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Всплывающее окно напоминания (поверх экрана блокировки). */
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val id = intent.getLongExtra(Reminders.EXTRA_DOSE, -1L)
        setContent {
            AppTheme {
                Surface {
                    ReminderContent(id) { finish() }
                }
            }
        }
    }
}

@Composable
private fun ReminderContent(doseId: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<DoseView?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(doseId) {
        view = AppDb.get(ctx).dao().getDoseView(doseId)
        loaded = true
    }

    val v = view
    if (loaded && (v == null || v.dose.status != Status.PENDING)) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    if (v == null) return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("💊 Пора принять лекарство", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            v.medicineName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        )
        Text("Время приёма: ${fmtTime(v.dose.scheduledAt)}", style = MaterialTheme.typography.bodyLarge)
        if (v.regimen.isNotBlank()) {
            Text(v.regimen, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch {
                    Reminders.setStatus(ctx, doseId, Status.TAKEN)
                    onClose()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) {
            Text("✔ Принял", fontSize = 18.sp)
        }
        if (v.dose.remindCount < Reminders.MAX_SHOWS) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        Reminders.snooze(ctx, doseId)
                        onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отложить на 30 мин")
            }
        } else {
            Text(
                "Это последнее напоминание: если не отметить приём, он будет считаться пропущенным.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
