package com.pillreminder.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

object Status {
    const val PENDING = 0
    const val TAKEN = 1
    const val MISSED = 2
}

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val regimen: String,
    val dose: String,
    val startEpochDay: Long,
    val durationDays: Int,
    val times: String // "09:00,15:00,21:00"
) {
    fun timeList(): List<LocalTime> =
        times.split(",").filter { it.isNotBlank() }.map { LocalTime.parse(it.trim()) }

    fun endEpochDay(): Long = startEpochDay + durationDays - 1
}

@Entity(
    tableName = "doses",
    foreignKeys = [
        ForeignKey(
            entity = Medicine::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["medicineId", "scheduledAt"], unique = true),
        Index(value = ["status"])
    ]
)
data class Dose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val scheduledAt: Long,          // запланированное время приёма (мс)
    val status: Int = Status.PENDING,
    val takenAt: Long? = null,      // когда реально принят (мс)
    val remindCount: Int = 0,       // сколько напоминаний уже показано
    val nextAt: Long                // когда сработает следующее напоминание (мс)
)

data class DoseView(
    @Embedded val dose: Dose,
    val medicineName: String,
    val regimen: String
)

@Dao
interface AppDao {
    @Insert
    suspend fun insertMedicine(m: Medicine): Long

    @Update
    suspend fun updateMedicine(m: Medicine)

    @Query("DELETE FROM medicines WHERE id = :id")
    suspend fun deleteMedicine(id: Long)

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun getMedicine(id: Long): Medicine?

    @Query("SELECT * FROM medicines ORDER BY name")
    fun observeMedicines(): Flow<List<Medicine>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDoses(doses: List<Dose>)

    @Query("DELETE FROM doses WHERE medicineId = :medicineId AND status = 0 AND scheduledAt >= :from")
    suspend fun deletePendingFrom(medicineId: Long, from: Long)

    @Query("SELECT * FROM doses WHERE status = 0 ORDER BY nextAt")
    suspend fun pendingDoses(): List<Dose>

    @Query("SELECT * FROM doses WHERE id = :id")
    suspend fun getDose(id: Long): Dose?

    @Update
    suspend fun updateDose(d: Dose)

    @Query(
        "SELECT d.*, m.name AS medicineName, m.regimen AS regimen " +
            "FROM doses d INNER JOIN medicines m ON m.id = d.medicineId " +
            "WHERE d.scheduledAt >= :from AND d.scheduledAt < :to ORDER BY d.scheduledAt"
    )
    fun observeDoses(from: Long, to: Long): Flow<List<DoseView>>

    @Query(
        "SELECT d.*, m.name AS medicineName, m.regimen AS regimen " +
            "FROM doses d INNER JOIN medicines m ON m.id = d.medicineId WHERE d.id = :id"
    )
    suspend fun getDoseView(id: Long): DoseView?
}

@Database(entities = [Medicine::class, Dose::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var instance: AppDb? = null

        fun get(ctx: Context): AppDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    ctx.applicationContext, AppDb::class.java, "pills.db"
                ).build().also { instance = it }
            }
    }
}

object Repo {
    /** Создаёт приёмы на весь курс; пропускает те, время которых уже прошло. */
    fun generateDoses(m: Medicine, after: Long): List<Dose> {
        val list = mutableListOf<Dose>()
        val times = m.timeList()
        for (day in 0 until m.durationDays) {
            val date = LocalDate.ofEpochDay(m.startEpochDay + day)
            for (t in times) {
                val ms = date.atTime(t).atZone(zone()).toInstant().toEpochMilli()
                if (ms > after) {
                    list += Dose(medicineId = m.id, scheduledAt = ms, nextAt = ms)
                }
            }
        }
        return list
    }

    suspend fun saveMedicine(ctx: Context, med: Medicine) {
        val db = AppDb.get(ctx)
        val dao = db.dao()
        val now = System.currentTimeMillis()
        db.withTransaction {
            val id = if (med.id == 0L) {
                dao.insertMedicine(med)
            } else {
                dao.updateMedicine(med)
                dao.deletePendingFrom(med.id, now)
                med.id
            }
            dao.insertDoses(generateDoses(med.copy(id = id), now))
        }
        Reminders.run(ctx)
    }

    suspend fun deleteMedicine(ctx: Context, id: Long) {
        AppDb.get(ctx).dao().deleteMedicine(id)
        Reminders.run(ctx)
    }
}
