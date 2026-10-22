package app.kalenderwidgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

/** Alles, was das Widget zu einem Zeitpunkt anzeigen muss. */
internal class Snapshot(
    val now: Long,
    val today: LocalDate,
    val zone: ZoneId,
    val hasPermission: Boolean,
    val current: CalEvent?,
    val nextTimed: CalEvent?,
    val allDayToday: List<CalEvent>,
    val nextAllDay: List<CalEvent>,
    val allEvents: List<CalEvent>,
) {
    val allDayIsNext: Boolean
        get() {
            val allDayDate = nextAllDay.firstOrNull()?.firstDay(zone) ?: return false
            val timedDate = nextTimed?.firstDay(zone) ?: return true
            return !allDayDate.isAfter(timedDate)
        }
}

/** Baut Widget A (Jetzt & Danach) zusammen und plant das nächste Update. */
object WidgetUpdater {

    const val ACTION_REFRESH = "app.kalenderwidgets.action.REFRESH"

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val MAX_INTERVAL = 15 * MINUTE
    private const val COUNTDOWN_WINDOW = 90 * MINUTE

    val DOT_IDS = intArrayOf(
        R.id.dot_0, R.id.dot_1, R.id.dot_2, R.id.dot_3, R.id.dot_4,
        R.id.dot_5, R.id.dot_6, R.id.dot_7, R.id.dot_8, R.id.dot_9,
        R.id.dot_10, R.id.dot_11, R.id.dot_12, R.id.dot_13, R.id.dot_14,
        R.id.dot_15, R.id.dot_16, R.id.dot_17, R.id.dot_18, R.id.dot_19,
    )

    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)
    val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)
    val WEEKDAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.GERMAN)

    data class Palette(
        val background: Int,
        val text: Int,
        val muted: Int,
        val accent: Int,
        val dotOff: Int,
        val line: Int,
        /** Milchglas-Ebene (Glanz + Körnung) und Lichtkante, nur im Transparenz-Modus sichtbar. */
        val frost: Int,
        val edge: Int,
    )

    fun palette(context: Context, widgetId: Int): Palette {
        val appearance = WidgetPrefs.appearance(context, widgetId)
        val adaptive = WidgetPrefs.widgetAdaptive(context, widgetId)
        val glass = WidgetPrefs.widgetGlass(context, widgetId)
        return palette(appearance, adaptive, glass)
    }

    fun palette(appearance: Appearance, adaptive: Boolean, glass: Boolean): Palette =
        when (appearance) {
            Appearance.DARK -> darkPalette(adaptive, glass)
            Appearance.LIGHT -> lightPalette(adaptive, glass)
            Appearance.AUTO -> autoPalette(adaptive, glass)
        }

    private fun darkPalette(adaptive: Boolean, glass: Boolean): Palette =
        if (adaptive) {
            Palette(
                background = if (glass) R.color.adaptive_bg_glass else R.color.adaptive_bg_solid,
                text = R.color.adaptive_text,
                muted = R.color.adaptive_muted,
                accent = R.color.adaptive_accent,
                dotOff = R.color.adaptive_dot_off,
                line = R.color.adaptive_line,
                frost = R.drawable.widget_frost_dark,
                edge = R.color.glass_edge_dark,
            )
        } else {
            Palette(
                background = if (glass) R.color.mono_bg_glass else R.color.mono_bg_solid,
                text = R.color.mono_text,
                muted = if (glass) R.color.mono_muted_glass else R.color.mono_muted_solid,
                accent = R.color.mono_accent,
                dotOff = if (glass) R.color.mono_dot_off_glass else R.color.mono_dot_off_solid,
                line = if (glass) R.color.mono_line_glass else R.color.mono_line_solid,
                frost = R.drawable.widget_frost_dark,
                edge = R.color.glass_edge_dark,
            )
        }

    private fun lightPalette(adaptive: Boolean, glass: Boolean): Palette =
        if (adaptive) {
            Palette(
                background = if (glass) R.color.adaptive_light_bg_glass else R.color.adaptive_light_bg_solid,
                text = R.color.adaptive_light_text,
                muted = R.color.adaptive_light_muted,
                accent = R.color.adaptive_light_accent,
                dotOff = R.color.adaptive_light_dot_off,
                line = R.color.adaptive_light_line,
                frost = R.drawable.widget_frost_light,
                edge = R.color.glass_edge_light,
            )
        } else {
            Palette(
                background = if (glass) R.color.light_bg_glass else R.color.light_bg_solid,
                text = R.color.light_text,
                muted = if (glass) R.color.light_muted_glass else R.color.light_muted_solid,
                accent = R.color.light_accent,
                dotOff = if (glass) R.color.light_dot_off_glass else R.color.light_dot_off_solid,
                line = if (glass) R.color.light_line_glass else R.color.light_line_solid,
                frost = R.drawable.widget_frost_light,
                edge = R.color.glass_edge_light,
            )
        }

    private fun autoPalette(adaptive: Boolean, glass: Boolean): Palette =
        if (adaptive) {
            Palette(
                background = if (glass) R.color.auto_adaptive_bg_glass else R.color.auto_adaptive_bg_solid,
                text = R.color.auto_adaptive_text,
                muted = R.color.auto_adaptive_muted,
                accent = R.color.auto_adaptive_accent,
                dotOff = R.color.auto_adaptive_dot_off,
                line = R.color.auto_adaptive_line,
                frost = R.drawable.widget_frost,
                edge = R.color.glass_edge,
            )
        } else {
            Palette(
                background = if (glass) R.color.auto_bg_glass else R.color.auto_bg_solid,
                text = R.color.auto_text,
                muted = if (glass) R.color.auto_muted_glass else R.color.auto_muted_solid,
                accent = R.color.auto_accent,
                dotOff = if (glass) R.color.auto_dot_off_glass else R.color.auto_dot_off_solid,
                line = if (glass) R.color.auto_line_glass else R.color.auto_line_solid,
                frost = R.drawable.widget_frost,
                edge = R.color.glass_edge,
            )
        }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val nowNextIds = manager.getAppWidgetIds(ComponentName(context, NowNextWidget::class.java))
        val dayBarIds = manager.getAppWidgetIds(ComponentName(context, DayBarWidget::class.java))
        if (nowNextIds.isEmpty() && dayBarIds.isEmpty()) {
            cancelRefresh(context)
            return
        }
        val snapshot = loadSnapshot(context, System.currentTimeMillis())
        for (id in nowNextIds) {
            manager.updateAppWidget(id, buildNowNextViews(context, id, snapshot))
        }
        for (id in dayBarIds) {
            val options = manager.getAppWidgetOptions(id)
            manager.updateAppWidget(id, DayBarUpdater.buildViews(context, id, snapshot, options))
        }
        scheduleRefresh(context, nextRefreshTime(snapshot))
    }

    fun updateNowNext(context: Context, widgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val snapshot = loadSnapshot(context, System.currentTimeMillis())
        manager.updateAppWidget(widgetId, buildNowNextViews(context, widgetId, snapshot))
    }

    fun updateDayBar(context: Context, widgetId: Int, options: Bundle) {
        val manager = AppWidgetManager.getInstance(context)
        val snapshot = loadSnapshot(context, System.currentTimeMillis())
        manager.updateAppWidget(widgetId, DayBarUpdater.buildViews(context, widgetId, snapshot, options))
    }

    internal fun loadSnapshot(context: Context, now: Long): Snapshot {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val hasPermission = CalendarRepository.hasPermission(context)
        val events = if (hasPermission) {
            CalendarRepository.loadEvents(context, now - DAY, now + 8 * DAY)
        } else {
            emptyList()
        }
        val (allDay, timed) = events.partition { it.allDay }
        val current = timed.filter { it.begin <= now && now < it.end }.maxByOrNull { it.begin }
        val nextTimed = timed.firstOrNull { it.begin > now }
        val allDayToday = allDay
            .filter { !it.firstDay(zone).isAfter(today) && !it.lastDay(zone).isBefore(today) }
            .sortedWith(compareByDescending<CalEvent> { it.firstDay(zone) }.thenBy { it.title })
        val nextAllDayDate = allDay.map { it.firstDay(zone) }.filter { it.isAfter(today) }.minOrNull()
        val nextAllDay = if (nextAllDayDate == null) emptyList()
        else allDay.filter { it.firstDay(zone) == nextAllDayDate }.sortedBy { it.title }
        return Snapshot(now, today, zone, hasPermission, current, nextTimed, allDayToday, nextAllDay, events)
    }

    private fun buildNowNextViews(context: Context, widgetId: Int, s: Snapshot): RemoteViews {
        val glass = WidgetPrefs.widgetGlass(context, widgetId)
        val p = palette(context, widgetId)
        val base = widgetId * 100
        val v = RemoteViews(context.packageName, R.layout.widget_now_next)

        v.setColorStateList(R.id.bg_fill, "setImageTintList", p.background)
        v.setViewVisibility(R.id.bg_stroke, if (glass) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.bg_frost, if (glass) View.VISIBLE else View.GONE)
        v.setImageViewResource(R.id.bg_frost, p.frost)
        v.setColorStateList(R.id.bg_stroke, "setImageTintList", p.edge)
        for (id in intArrayOf(
            R.id.clock_hour, R.id.clock_minute, R.id.now_title, R.id.next_title, R.id.allday_title,
        )) {
            v.setColorStateList(id, "setTextColor", p.text)
        }
        for (id in intArrayOf(
            R.id.date, R.id.now_label, R.id.now_time, R.id.next_label, R.id.next_time, R.id.allday_count,
            R.id.allday_tag,
        )) {
            v.setColorStateList(id, "setTextColor", p.muted)
        }
        v.setColorStateList(R.id.divider, "setImageTintList", p.line)

        v.setOnClickPendingIntent(android.R.id.background, calendarIntent(context, s.now, base + 3))

        if (!s.hasPermission) {
            v.setViewVisibility(R.id.allday_row, View.GONE)
            v.setTextViewText(R.id.now_label, "KALENDER")
            v.setTextViewText(R.id.now_title, "Zugriff fehlt")
            v.setViewVisibility(R.id.now_time, View.GONE)
            v.setViewVisibility(R.id.dots, View.GONE)
            // Hinweis unten statt rechts in der Label-Zeile (dort stehen sonst Uhrzeiten)
            v.setViewVisibility(R.id.next_label, View.GONE)
            v.setViewVisibility(R.id.next_time, View.GONE)
            v.setTextViewText(R.id.next_title, "Tippen zum Einrichten")
            v.setOnClickPendingIntent(android.R.id.background, settingsIntent(context, base + 4))
            return v
        }

        val firstAllDay = s.allDayToday.firstOrNull()
        if (firstAllDay != null) {
            v.setViewVisibility(R.id.allday_row, View.VISIBLE)
            v.setTextViewText(R.id.allday_title, firstAllDay.title)
            v.setTextViewText(R.id.allday_tag, allDayTag(firstAllDay, s.today, s.zone))
            val more = s.allDayToday.size - 1
            if (more > 0) {
                v.setTextViewText(R.id.allday_count, "+$more ·")
                v.setViewVisibility(R.id.allday_count, View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.allday_count, View.GONE)
            }
            v.setOnClickPendingIntent(R.id.allday_row, eventIntent(context, firstAllDay, base + 5))
        } else {
            v.setViewVisibility(R.id.allday_row, View.GONE)
        }

        v.setTextViewText(R.id.now_label, "JETZT")
        val current = s.current
        if (current != null) {
            v.setTextViewText(R.id.now_title, current.title)
            v.setTextViewText(R.id.now_time, timeRange(current))
            v.setViewVisibility(R.id.now_time, View.VISIBLE)
            v.setViewVisibility(R.id.dots, View.VISIBLE)
            val filled = filledDots(current, s.now)
            DOT_IDS.forEachIndexed { index, id ->
                v.setColorStateList(id, "setImageTintList", if (index < filled) p.accent else p.dotOff)
            }
            v.setOnClickPendingIntent(R.id.now_block, eventIntent(context, current, base + 1))
        } else {
            v.setTextViewText(R.id.now_title, "Frei")
            v.setViewVisibility(R.id.now_time, View.GONE)
            v.setViewVisibility(R.id.dots, View.GONE)
        }

        v.setViewVisibility(R.id.divider, View.VISIBLE)
        v.setViewVisibility(R.id.next_block, View.VISIBLE)
        val nextTimed = s.nextTimed
        when {
            s.allDayIsNext -> {
                val first = s.nextAllDay.first()
                v.setViewVisibility(R.id.next_label, View.VISIBLE)
                v.setTextViewText(R.id.next_label, dayLabel(first.firstDay(s.zone), s.today))
                v.setTextViewText(R.id.next_title, first.title)
                val more = s.nextAllDay.size - 1
                val tag = allDayTag(first, first.firstDay(s.zone), s.zone)
                v.setTextViewText(R.id.next_time, if (more > 0) "+$more · $tag" else tag)
                v.setViewVisibility(R.id.next_time, View.VISIBLE)
                v.setOnClickPendingIntent(R.id.next_block, eventIntent(context, first, base + 2))
            }
            nextTimed != null -> {
                v.setViewVisibility(R.id.next_label, View.VISIBLE)
                v.setTextViewText(R.id.next_label, relativeLabel(nextTimed.begin, s.now, s.zone))
                v.setTextViewText(R.id.next_title, nextTimed.title)
                v.setTextViewText(R.id.next_time, timeRange(nextTimed))
                v.setViewVisibility(R.id.next_time, View.VISIBLE)
                v.setOnClickPendingIntent(R.id.next_block, eventIntent(context, nextTimed, base + 2))
            }
            else -> {
                v.setViewVisibility(R.id.next_label, View.GONE)
                v.setTextViewText(R.id.next_title, "Nichts geplant")
                v.setViewVisibility(R.id.next_time, View.GONE)
            }
        }
        return v
    }

    // ---------- Texthilfen (intern + für DayBarUpdater zugänglich) ----------

    fun filledDots(event: CalEvent, now: Long): Int {
        val duration = (event.end - event.begin).coerceAtLeast(1)
        return ((now - event.begin) * DOT_IDS.size / duration).toInt().coerceIn(0, DOT_IDS.size)
    }

    fun relativeLabel(begin: Long, now: Long, zone: ZoneId): String {
        val minutes = ceil((begin - now) / MINUTE.toDouble()).toLong().coerceAtLeast(1)
        if (minutes * MINUTE < COUNTDOWN_WINDOW) return "IN $minutes MIN"
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
        return if (day == today) "IN ${minutes / 60} STD" else dayLabel(day, today)
    }

    fun dayLabel(day: LocalDate, today: LocalDate): String =
        if (day == today.plusDays(1)) "MORGEN" else WEEKDAY_DATE.format(day).uppercase(Locale.GERMAN)

    /**
     * Wann-Angabe eines ganztägigen Termins, gezählt ab [from]: „GANZTÄGIG“, wenn er an diesem Tag
     * endet, sonst „BIS SO.“ (Ende in < 7 Tagen) bzw. „BIS 02.11.“.
     */
    fun allDayTag(event: CalEvent, from: LocalDate, zone: ZoneId): String {
        val last = event.lastDay(zone)
        if (!last.isAfter(from)) return "GANZTÄGIG"
        val end = if (ChronoUnit.DAYS.between(from, last) < 7) WEEKDAY.format(last) else DAY_MONTH.format(last)
        return "BIS ${end.uppercase(Locale.GERMAN)}"
    }

    fun clock(time: Long): String =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(TIME)

    fun timeRange(event: CalEvent): String = "${clock(event.begin)} – ${clock(event.end)}"

    // ---------- Updates planen ----------

    internal fun nextRefreshTime(s: Snapshot): Long {
        val now = s.now
        var t = now + MAX_INTERVAL
        s.current?.let { current ->
            t = minOf(t, current.end)
            val step = (current.end - current.begin) / DOT_IDS.size
            if (step > 0) {
                val k = (now - current.begin) / step + 1
                t = minOf(t, current.begin + k * step)
            }
        }
        s.nextTimed?.let { next ->
            t = minOf(t, next.begin)
            if (next.begin - now < COUNTDOWN_WINDOW) {
                t = minOf(t, (now / MINUTE + 1) * MINUTE)
            }
        }
        t = minOf(t, s.today.plusDays(1).atStartOfDay(s.zone).toInstant().toEpochMilli())
        return maxOf(t, now + 20_000L) + 1_000L
    }

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, NowNextWidget::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun scheduleRefresh(context: Context, at: Long) {
        context.getSystemService(AlarmManager::class.java)
            ?.set(AlarmManager.RTC, at, refreshIntent(context))
    }

    fun cancelRefresh(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(refreshIntent(context))
    }

    // ---------- Antippen ----------

    fun activityIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun calendarIntent(context: Context, now: Long, requestCode: Int): PendingIntent {
        val zone = ZoneId.systemDefault()
        val startOfToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time").appendPath(startOfToday.toString()).build()
        return activityIntent(context, requestCode, Intent(Intent.ACTION_VIEW, uri))
    }

    fun eventIntent(context: Context, event: CalEvent, requestCode: Int): PendingIntent {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
        if (event.allDay) intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        return activityIntent(context, requestCode, intent)
    }

    fun settingsIntent(context: Context, requestCode: Int): PendingIntent =
        activityIntent(context, requestCode, Intent(context, MainActivity::class.java))
}
