package com.pillreminder.app

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

val Green = Color(0xFF2E7D32)
val Red = Color(0xFFC62828)
val GreyDot = Color(0xFF9E9E9E)

fun statusColor(status: Int): Color = when (status) {
    Status.TAKEN -> Green
    Status.MISSED -> Red
    else -> GreyDot
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF81C784), onPrimary = Color(0xFF00390F))
    } else {
        lightColorScheme(primary = Color(0xFF2E7D32), onPrimary = Color.White)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------------------------------------------------------------- главный экран

@Composable
fun MainApp() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Reminders.run(ctx) // на всякий случай пересчитать будильник
    }

    if (editing) {
        BackHandler { editing = false }
        EditScreen(editId) { editing = false }
    } else {
        val labels = listOf("Сегодня", "График", "Лекарства")
        val icons = listOf("📅", "📊", "💊")
        Scaffold(
            bottomBar = {
                NavigationBar {
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(icons[i], fontSize = 20.sp) },
                            label = { Text(label) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab != 1) {
                    FloatingActionButton(onClick = {
                        editId = null
                        editing = true
                    }) {
                        Text("+", fontSize = 28.sp)
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> TodayScreen()
                    1 -> ChartScreen()
                    else -> MedsScreen(onEdit = { id ->
                        editId = id
                        editing = true
                    })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- общие элементы

fun statusText(v: DoseView): String = when (v.dose.status) {
    Status.TAKEN -> "✔ Принят" + (v.dose.takenAt?.let { " в ${fmtTime(it)}" } ?: "")
    Status.MISSED -> "✖ Пропущен"
    else -> "Ожидает"
}

@Composable
fun DoseRow(v: DoseView, onClick: () -> Unit, onTake: (() -> Unit)?) {
    val col = statusColor(v.dose.status)
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(col)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Text(
                    "${fmtTime(v.dose.scheduledAt)}  ${v.medicineName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (v.regimen.isNotBlank()) {
                    Text(v.regimen, style = MaterialTheme.typography.bodySmall)
                }
                Text(statusText(v), color = col, fontWeight = FontWeight.Medium)
            }
            if (onTake != null) {
                Button(
                    onClick = onTake,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Text("Принял")
                }
            }
        }
    }
}

@Composable
fun DoseActionDialog(v: DoseView, onDismiss: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = appScope
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(v.medicineName) },
        text = {
            Text(
                "Приём в ${fmtTime(v.dose.scheduledAt)} (${dateOf(v.dose.scheduledAt).format(DateTimeFormatter.ofPattern("d MMMM", RU))}).\n" +
                    "Отметить вручную:"
            )
        },
        confirmButton = {
            Button(onClick = {
                scope.launch { Reminders.setStatus(ctx, v.dose.id, Status.TAKEN) }
                onDismiss()
            }) { Text("Принят") }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                scope.launch { Reminders.setStatus(ctx, v.dose.id, Status.MISSED) }
                onDismiss()
            }) { Text("Пропущен") }
        }
    )
}

@Composable
fun SummaryLine(doses: List<DoseView>) {
    val taken = doses.count { it.dose.status == Status.TAKEN }
    val missed = doses.count { it.dose.status == Status.MISSED }
    val pending = doses.count { it.dose.status == Status.PENDING }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("✔ Принято: $taken", color = Green, fontWeight = FontWeight.Medium)
        Text("✖ Пропущено: $missed", color = Red, fontWeight = FontWeight.Medium)
        Text("Ожидает: $pending", color = GreyDot)
    }
}

// ---------------------------------------------------------------- «Сегодня»

@Composable
fun TodayScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val flow = remember(today) {
        AppDb.get(ctx).dao().observeDoses(dayStart(today), dayStart(today.plusDays(1)))
    }
    val doses by flow.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<DoseView?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Сегодня, " + today.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", RU)),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        SummaryLine(doses)
        Spacer(Modifier.height(8.dp))
        if (doses.isEmpty()) {
            Text("На сегодня приёмов нет. Добавьте лекарство кнопкой «+».")
        }
        LazyColumn(Modifier.weight(1f)) {
            items(doses, key = { it.dose.id }) { v ->
                val onTake: () -> Unit = {
                    appScope.launch { Reminders.setStatus(ctx.applicationContext, v.dose.id, Status.TAKEN) }
                    Unit
                }
                DoseRow(
                    v,
                    onClick = { selected = v },
                    onTake = if (v.dose.status == Status.TAKEN) null else onTake
                )
            }
        }
    }
    selected?.let { DoseActionDialog(it) { selected = null } }
}

// ---------------------------------------------------------------- «График»

private fun rangeFor(mode: Int, a: LocalDate): Pair<LocalDate, LocalDate> = when (mode) {
    0 -> a to a.plusDays(1)
    1 -> {
        val monday = a.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        monday to monday.plusDays(7)
    }
    else -> {
        val first = a.withDayOfMonth(1)
        first to first.plusMonths(1)
    }
}

private fun shift(mode: Int, a: LocalDate, dir: Long): LocalDate = when (mode) {
    0 -> a.plusDays(dir)
    1 -> a.plusWeeks(dir)
    else -> a.plusMonths(dir)
}

private fun periodLabel(mode: Int, from: LocalDate, to: LocalDate): String = when (mode) {
    0 -> from.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", RU))
    1 -> from.format(DateTimeFormatter.ofPattern("d MMM", RU)) + " – " +
        to.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM yyyy", RU))
    else -> from.format(DateTimeFormatter.ofPattern("LLLL yyyy", RU))
}

@Composable
fun ChartScreen() {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var selDay by remember { mutableStateOf(LocalDate.now()) }
    var selected by remember { mutableStateOf<DoseView?>(null) }

    val range = remember(mode, anchor) { rangeFor(mode, anchor) }
    val from = range.first
    val to = range.second
    val flow = remember(from, to) {
        AppDb.get(ctx).dao().observeDoses(dayStart(from), dayStart(to))
    }
    val doses by flow.collectAsState(initial = emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        TabRow(selectedTabIndex = mode) {
            listOf("День", "Неделя", "Месяц").forEachIndexed { i, label ->
                Tab(
                    selected = mode == i,
                    onClick = {
                        mode = i
                        anchor = LocalDate.now()
                        selDay = LocalDate.now()
                    },
                    text = { Text(label) }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                anchor = shift(mode, anchor, -1)
                selDay = anchor
            }) { Text("◀", fontSize = 20.sp) }
            Text(
                periodLabel(mode, from, to),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = {
                anchor = shift(mode, anchor, 1)
                selDay = anchor
            }) { Text("▶", fontSize = 20.sp) }
        }
        SummaryLine(doses)
        Spacer(Modifier.height(8.dp))

        when (mode) {
            0 -> {
                if (doses.isEmpty()) Text("В этот день приёмов не было.")
                LazyColumn(Modifier.weight(1f)) {
                    items(doses, key = { it.dose.id }) { v ->
                        DoseRow(v, onClick = { selected = v }, onTake = null)
                    }
                }
            }
            1 -> {
                val byDay = doses.groupBy { dateOf(it.dose.scheduledAt) }
                LazyColumn(Modifier.weight(1f)) {
                    items((0 until 7).map { from.plusDays(it.toLong()) }, key = { it.toEpochDay() }) { day ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)),
                                fontWeight = FontWeight.Bold
                            )
                            val list = byDay[day].orEmpty()
                            if (list.isEmpty()) Text("—", color = GreyDot)
                            list.forEach { v -> DoseLine(v) { selected = v } }
                        }
                    }
                }
            }
            else -> {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    MonthGrid(from, doses, selDay) { selDay = it }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        selDay.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", RU)),
                        fontWeight = FontWeight.Bold
                    )
                    val list = doses.filter { dateOf(it.dose.scheduledAt) == selDay }
                    if (list.isEmpty()) Text("Приёмов нет.", color = GreyDot)
                    list.forEach { v -> DoseRow(v, onClick = { selected = v }, onTake = null) }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    selected?.let { DoseActionDialog(it) { selected = null } }
}

@Composable
fun DoseLine(v: DoseView, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(statusColor(v.dose.status))
        )
        Text(
            "  ${fmtTime(v.dose.scheduledAt)}  ${v.medicineName}  ",
            fontSize = 15.sp
        )
        Text(
            when (v.dose.status) {
                Status.TAKEN -> "принят"
                Status.MISSED -> "пропущен"
                else -> "ожидает"
            },
            color = statusColor(v.dose.status),
            fontSize = 13.sp
        )
    }
}

@Composable
fun MonthGrid(
    month: LocalDate,
    doses: List<DoseView>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val first = month.withDayOfMonth(1)
    val offset = first.dayOfWeek.value - 1 // понедельник = 0
    val days = first.lengthOfMonth()
    val byDay = doses.groupBy { dateOf(it.dose.scheduledAt) }

    Row(Modifier.fillMaxWidth()) {
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
            Text(
                it,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
    val rows = (offset + days + 6) / 7
    for (r in 0 until rows) {
        Row(Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                val dayNum = r * 7 + c - offset + 1
                if (dayNum < 1 || dayNum > days) {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.85f)
                    )
                } else {
                    val date = first.withDayOfMonth(dayNum)
                    val ds = byDay[date].orEmpty()
                    val bg = when {
                        ds.isEmpty() -> Color.Transparent
                        ds.any { it.dose.status == Status.MISSED } -> Red.copy(alpha = 0.16f)
                        ds.all { it.dose.status == Status.TAKEN } -> Green.copy(alpha = 0.16f)
                        else -> GreyDot.copy(alpha = 0.12f)
                    }
                    val isSel = date == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.85f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .then(
                                if (isSel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .clickable { onSelect(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            dayNum.toString(),
                            fontSize = 14.sp,
                            fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal
                        )
                        ds.chunked(4).forEach { rowDots ->
                            Row {
                                rowDots.forEach { d ->
                                    Box(
                                        Modifier
                                            .padding(1.dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(statusColor(d.dose.status))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- «Лекарства»

@Composable
fun MedsScreen(onEdit: (Long) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { AppDb.get(ctx).dao().observeMedicines() }
    val meds by flow.collectAsState(initial = emptyList())
    var toDelete by remember { mutableStateOf<Medicine?>(null) }
    val today = LocalDate.now().toEpochDay()
    val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text("Мои лекарства", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            SettingsCard()
            Spacer(Modifier.height(8.dp))
            if (meds.isEmpty()) {
                Text("Пока ничего нет. Нажмите «+», чтобы добавить лекарство.")
            }
        }
        items(meds, key = { it.id }) { m ->
            val left = maxOf(0L, m.endEpochDay() - today + 1)
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(m.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    if (m.regimen.isNotBlank()) Text(m.regimen)
                    if (m.dose.isNotBlank()) Text("Доза: ${m.dose}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Время: " + m.timeList().joinToString(", ") { it.format(TIME_FMT) },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Курс: ${LocalDate.ofEpochDay(m.startEpochDay).format(dateFmt)} – " +
                            "${LocalDate.ofEpochDay(m.endEpochDay()).format(dateFmt)} (${m.durationDays} дн.)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (left > 0 && today >= m.startEpochDay) "Осталось дней: $left"
                        else if (today < m.startEpochDay) "Курс ещё не начался"
                        else "Курс завершён",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (left > 0) Green else GreyDot
                    )
                    Row {
                        TextButton(onClick = { onEdit(m.id) }) { Text("Изменить") }
                        TextButton(onClick = { toDelete = m }) { Text("Удалить", color = Red) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить «${m.name}»?") },
            text = { Text("Будут удалены курс и вся история приёмов этого лекарства.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { Repo.deleteMedicine(ctx, m.id) }
                    toDelete = null
                }) { Text("Удалить", color = Red) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
fun SettingsCard() {
    val ctx = LocalContext.current
    fun open(intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Чтобы напоминания приходили вовремя", fontWeight = FontWeight.Bold)
            Text(
                "На некоторых телефонах (Xiaomi, Huawei, Samsung и др.) система «усыпляет» приложения. " +
                    "Разрешите приложению работать без ограничений батареи.",
                style = MaterialTheme.typography.bodySmall
            )
            Row {
                TextButton(onClick = {
                    open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }) { Text("Настройки батареи") }
                TextButton(onClick = {
                    open(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                }) { Text("Уведомления") }
            }
            if (Build.VERSION.SDK_INT >= 34) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (!nm.canUseFullScreenIntent()) {
                    TextButton(onClick = {
                        open(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .setData(Uri.parse("package:" + ctx.packageName))
                        )
                    }) { Text("Разрешить окно поверх экрана") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- добавить / изменить

private val DEFAULT_TIMES: Map<Int, List<LocalTime>> = mapOf(
    1 to listOf(LocalTime.of(9, 0)),
    2 to listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)),
    3 to listOf(LocalTime.of(9, 0), LocalTime.of(15, 0), LocalTime.of(21, 0)),
    4 to listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(16, 0), LocalTime.of(20, 0))
)

@Composable
fun EditScreen(id: Long?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var regimen by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(1) }
    val times = remember { mutableStateListOf(LocalTime.of(9, 0)) }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var duration by remember { mutableStateOf("7") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id != null) {
            AppDb.get(ctx).dao().getMedicine(id)?.let { m ->
                name = m.name
                regimen = m.regimen
                dose = m.dose
                times.clear()
                times.addAll(m.timeList())
                count = times.size
                start = LocalDate.ofEpochDay(m.startEpochDay)
                duration = m.durationDays.toString()
            }
        }
    }

    fun setCount(n: Int) {
        count = n
        while (times.size > n) times.removeAt(times.lastIndex)
        val def = DEFAULT_TIMES.getValue(n)
        while (times.size < n) times.add(def[times.size])
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone) { Text("←", fontSize = 22.sp) }
            Text(
                if (id == null) "Новое лекарство" else "Изменить лекарство",
                style = MaterialTheme.typography.titleLarge
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Название (например, Нольпаза)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = regimen,
            onValueChange = { regimen = it },
            label = { Text("Режим (например, 3 раза в день после еды)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = dose,
            onValueChange = { dose = it },
            label = { Text("Доза (необязательно, например, 1 таблетка)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Сколько раз в день", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in 1..4) {
                if (count == n) {
                    Button(onClick = { setCount(n) }) { Text("$n") }
                } else {
                    OutlinedButton(onClick = { setCount(n) }) { Text("$n") }
                }
            }
        }

        Text("Время приёма", fontWeight = FontWeight.Bold)
        times.forEachIndexed { i, t ->
            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        ctx,
                        { _, h, m -> times[i] = LocalTime.of(h, m) },
                        t.hour, t.minute, true
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Приём ${i + 1}:  ${t.format(TIME_FMT)}", fontSize = 16.sp)
            }
        }

        OutlinedTextField(
            value = duration,
            onValueChange = { duration = it.filter(Char::isDigit).take(3) },
            label = { Text("Продолжительность, дней") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Начало курса", fontWeight = FontWeight.Bold)
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    ctx,
                    { _, y, m, d -> start = LocalDate.of(y, m + 1, d) },
                    start.year, start.monthValue - 1, start.dayOfMonth
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(start.format(DateTimeFormatter.ofPattern("d MMMM yyyy", RU)), fontSize = 16.sp)
        }

        error?.let { Text(it, color = Red) }

        Button(
            onClick = {
                val dur = duration.toIntOrNull()
                val sorted = times.sorted()
                when {
                    name.isBlank() -> error = "Введите название лекарства"
                    dur == null || dur < 1 || dur > 365 -> error = "Продолжительность: от 1 до 365 дней"
                    sorted.distinct().size != sorted.size -> error = "Время приёмов не должно повторяться"
                    else -> {
                        error = null
                        val med = Medicine(
                            id = id ?: 0L,
                            name = name.trim(),
                            regimen = regimen.trim(),
                            dose = dose.trim(),
                            startEpochDay = start.toEpochDay(),
                            durationDays = dur,
                            times = sorted.joinToString(",") { it.format(TIME_FMT) }
                        )
                        scope.launch {
                            Repo.saveMedicine(ctx, med)
                            onDone()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить", fontSize = 18.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
