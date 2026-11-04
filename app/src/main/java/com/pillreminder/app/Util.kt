package com.pillreminder.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Scope уровня приложения: операции с БД не прерываются при закрытии диалога/экрана. */
val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

val RU: Locale = Locale.forLanguageTag("ru")
val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun zone(): ZoneId = ZoneId.systemDefault()

fun fmtTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(zone()).toLocalTime().format(TIME_FMT)

fun dayStart(d: LocalDate): Long =
    d.atStartOfDay(zone()).toInstant().toEpochMilli()

fun dateOf(ms: Long): LocalDate =
    Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()
