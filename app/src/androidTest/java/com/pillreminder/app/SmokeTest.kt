package com.pillreminder.app

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val perm: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private var doseId = 0L

    @Before
    fun setup() = runBlocking {
        val db = AppDb.get(ctx)
        db.clearAllTables()
        val today = LocalDate.now().toEpochDay()
        Repo.saveMedicine(
            ctx,
            Medicine(
                name = "Нольпаза", regimen = "3 раза в день после еды", dose = "",
                startEpochDay = today, durationDays = 7, times = "09:00,15:00,21:00"
            )
        )
        val medId = db.dao().observeMedicines().first().first().id
        val now = System.currentTimeMillis()
        // приём, время которого уже наступило: проверяем путь показа напоминания
        db.dao().insertDoses(listOf(Dose(medicineId = medId, scheduledAt = now - 60_000, nextAt = now - 60_000)))
        Reminders.run(ctx)
        doseId = db.dao().pendingDoses().first { it.scheduledAt == now - 60_000 }.id
    }

    private fun waitText(text: String) {
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun mainShowsTodayChartAndMeds() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Нольпаза")
            rule.onNodeWithText("График").performClick()
            waitText("Принято")
            rule.onNodeWithText("Неделя").performClick()
            waitText("Нольпаза")
            rule.onNodeWithText("Месяц").performClick()
            waitText("Пн")
            rule.onNodeWithText("Лекарства").performClick()
            waitText("Курс:")
        }
    }

    @Test
    fun reminderActivityOpens() {
        val i = Intent(ctx, ReminderActivity::class.java).putExtra(Reminders.EXTRA_DOSE, doseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<ReminderActivity>(i).use {
            waitText("Принял")
        }
    }

    @Test
    fun addMedicineThroughUi() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Нольпаза")
            rule.onNodeWithText("+").performClick()
            waitText("Новое лекарство")
            rule.onNodeWithText("Название (например, Нольпаза)").performTextInput("Тестовое")
            rule.onNodeWithText("Сохранить").performClick()
            rule.onNodeWithText("Лекарства").performClick()
            waitText("Тестовое")
        }
    }
}
