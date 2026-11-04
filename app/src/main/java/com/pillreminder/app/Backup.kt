package com.pillreminder.app

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/** Резервная копия: все лекарства и история приёмов в одном JSON-файле. */
object Backup {
    suspend fun export(ctx: Context): String {
        val dao = AppDb.get(ctx).dao()
        val meds = JSONArray()
        for (m in dao.allMedicines()) {
            meds.put(
                JSONObject()
                    .put("id", m.id).put("name", m.name).put("regimen", m.regimen)
                    .put("dose", m.dose).put("startEpochDay", m.startEpochDay)
                    .put("durationDays", m.durationDays).put("times", m.times)
                    .put("paused", m.paused).put("stock", m.stock)
            )
        }
        val doses = JSONArray()
        for (d in dao.allDoses()) {
            doses.put(
                JSONObject()
                    .put("id", d.id).put("medicineId", d.medicineId)
                    .put("scheduledAt", d.scheduledAt).put("status", d.status)
                    .put("takenAt", d.takenAt ?: JSONObject.NULL)
                    .put("remindCount", d.remindCount).put("nextAt", d.nextAt)
            )
        }
        return JSONObject().put("app", "pill-reminder").put("version", 2)
            .put("medicines", meds).put("doses", doses).toString()
    }

    /** Заменяет все данные содержимым копии. Возвращает число лекарств. */
    suspend fun restore(ctx: Context, json: String): Int {
        val root = JSONObject(json)
        require(root.optString("app") == "pill-reminder") { "Это не файл копии приложения" }
        val medsJson = root.getJSONArray("medicines")
        val dosesJson = root.getJSONArray("doses")
        val meds = (0 until medsJson.length()).map {
            val o = medsJson.getJSONObject(it)
            Medicine(
                id = o.getLong("id"), name = o.getString("name"), regimen = o.getString("regimen"),
                dose = o.getString("dose"), startEpochDay = o.getLong("startEpochDay"),
                durationDays = o.getInt("durationDays"), times = o.getString("times"),
                paused = o.optBoolean("paused", false), stock = o.optInt("stock", -1)
            )
        }
        val doses = (0 until dosesJson.length()).map {
            val o = dosesJson.getJSONObject(it)
            Dose(
                id = o.getLong("id"), medicineId = o.getLong("medicineId"),
                scheduledAt = o.getLong("scheduledAt"), status = o.getInt("status"),
                takenAt = if (o.isNull("takenAt")) null else o.getLong("takenAt"),
                remindCount = o.getInt("remindCount"), nextAt = o.getLong("nextAt")
            )
        }
        val db = AppDb.get(ctx)
        db.withTransaction {
            db.dao().deleteAllDoses()
            db.dao().deleteAllMedicines()
            db.dao().putMedicines(meds)
            db.dao().putDoses(doses)
        }
        Reminders.run(ctx)
        return meds.size
    }
}
