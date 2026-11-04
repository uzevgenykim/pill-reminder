package com.pillreminder.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Проверяет, что база версии 1 (как у пользователя на телефоне) успешно обновляется до версии 2. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migratesV1ToV2() {
        AppDb.resetForTest()
        ctx.deleteDatabase("pills.db")
        val f = ctx.getDatabasePath("pills.db")
        f.parentFile?.mkdirs()
        val sdb = SQLiteDatabase.openOrCreateDatabase(f, null)
        sdb.execSQL(
            "CREATE TABLE medicines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                "regimen TEXT NOT NULL, dose TEXT NOT NULL, startEpochDay INTEGER NOT NULL, " +
                "durationDays INTEGER NOT NULL, times TEXT NOT NULL)"
        )
        sdb.execSQL(
            "CREATE TABLE doses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, medicineId INTEGER NOT NULL, " +
                "scheduledAt INTEGER NOT NULL, status INTEGER NOT NULL, takenAt INTEGER, " +
                "remindCount INTEGER NOT NULL, nextAt INTEGER NOT NULL, " +
                "FOREIGN KEY(medicineId) REFERENCES medicines(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        sdb.execSQL("CREATE UNIQUE INDEX index_doses_medicineId_scheduledAt ON doses (medicineId, scheduledAt)")
        sdb.execSQL("CREATE INDEX index_doses_status ON doses (status)")
        sdb.execSQL(
            "INSERT INTO medicines (id, name, regimen, dose, startEpochDay, durationDays, times) " +
                "VALUES (1, 'Старое', 'утром', '', 20000, 5, '09:00')"
        )
        sdb.execSQL(
            "INSERT INTO doses (medicineId, scheduledAt, status, takenAt, remindCount, nextAt) " +
                "VALUES (1, 1000, 0, NULL, 0, 1000)"
        )
        sdb.version = 1
        sdb.close()

        val meds = runBlocking { AppDb.get(ctx).dao().allMedicines() }
        assertEquals("Старое", meds[0].name)
        assertFalse(meds[0].paused)
        assertEquals(-1, meds[0].stock)
        assertEquals(1, runBlocking { AppDb.get(ctx).dao().allDoses().size })

        AppDb.resetForTest()
        ctx.deleteDatabase("pills.db")
    }
}
