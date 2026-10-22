package app.kalenderwidgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Baut Widget C (Monat) zusammen: Monatsraster mit Terminpunkten, darunter die Termine des gewählten Tags. */
object MonthUpdater {

    const val ACTION_SELECT_DAY = "app.kalenderwidgets.action.MONTH_SELECT_DAY"
    const val ACTION_TODAY = "app.kalenderwidgets.action.MONTH_TODAY"
    const val EXTRA_EPOCH_DAY = "epoch_day"

    private const val CELLS = 42
    private const val MAX_DOTS = 3

    private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL", Locale.GERMAN)

    private val CELL_IDS = intArrayOf(
        R.id.m_cell_0, R.id.m_cell_1, R.id.m_cell_2, R.id.m_cell_3, R.id.m_cell_4, R.id.m_cell_5,
        R.id.m_cell_6, R.id.m_cell_7, R.id.m_cell_8, R.id.m_cell_9, R.id.m_cell_10, R.id.m_cell_11,
        R.id.m_cell_12, R.id.m_cell_13, R.id.m_cell_14, R.id.m_cell_15, R.id.m_cell_16, R.id.m_cell_17,
        R.id.m_cell_18, R.id.m_cell_19, R.id.m_cell_20, R.id.m_cell_21, R.id.m_cell_22, R.id.m_cell_23,
        R.id.m_cell_24, R.id.m_cell_25, R.id.m_cell_26, R.id.m_cell_27, R.id.m_cell_28, R.id.m_cell_29,
        R.id.m_cell_30, R.id.m_cell_31, R.id.m_cell_32, R.id.m_cell_33, R.id.m_cell_34, R.id.m_cell_35,
        R.id.m_cell_36, R.id.m_cell_37, R.id.m_cell_38, R.id.m_cell_39, R.id.m_cell_40, R.id.m_cell_41,
    )
    private val DAY_IDS = intArrayOf(
        R.id.m_day_0, R.id.m_day_1, R.id.m_day_2, R.id.m_day_3, R.id.m_day_4, R.id.m_day_5,
        R.id.m_day_6, R.id.m_day_7, R.id.m_day_8, R.id.m_day_9, R.id.m_day_10, R.id.m_day_11,
        R.id.m_day_12, R.id.m_day_13, R.id.m_day_14, R.id.m_day_15, R.id.m_day_16, R.id.m_day_17,
        R.id.m_day_18, R.id.m_day_19, R.id.m_day_20, R.id.m_day_21, R.id.m_day_22, R.id.m_day_23,
        R.id.m_day_24, R.id.m_day_25, R.id.m_day_26, R.id.m_day_27, R.id.m_day_28, R.id.m_day_29,
        R.id.m_day_30, R.id.m_day_31, R.id.m_day_32, R.id.m_day_33, R.id.m_day_34, R.id.m_day_35,
        R.id.m_day_36, R.id.m_day_37, R.id.m_day_38, R.id.m_day_39, R.id.m_day_40, R.id.m_day_41,
    )
    private val DOTS_IDS = intArrayOf(
        R.id.m_dots_0, R.id.m_dots_1, R.id.m_dots_2, R.id.m_dots_3, R.id.m_dots_4, R.id.m_dots_5,
        R.id.m_dots_6, R.id.m_dots_7, R.id.m_dots_8, R.id.m_dots_9, R.id.m_dots_10, R.id.m_dots_11,
        R.id.m_dots_12, R.id.m_dots_13, R.id.m_dots_14, R.id.m_dots_15, R.id.m_dots_16, R.id.m_dots_17,
        R.id.m_dots_18, R.id.m_dots_19, R.id.m_dots_20, R.id.m_dots_21, R.id.m_dots_22, R.id.m_dots_23,
        R.id.m_dots_24, R.id.m_dots_25, R.id.m_dots_26, R.id.m_dots_27, R.id.m_dots_28, R.id.m_dots_29,
        R.id.m_dots_30, R.id.m_dots_31, R.id.m_dots_32, R.id.m_dots_33, R.id.m_dots_34, R.id.m_dots_35,
        R.id.m_dots_36, R.id.m_dots_37, R.id.m_dots_38, R.id.m_dots_39, R.id.m_dots_40, R.id.m_dots_41,
    )
    private val ROW_IDS = intArrayOf(R.id.m_ev0_row, R.id.m_ev1_row, R.id.m_ev2_row)
    private val TIME_IDS = intArrayOf(R.id.m_ev0_time, R.id.m_ev1_time, R.id.m_ev2_time)
    private val TITLE_IDS = intArrayOf(R.id.m_ev0_title, R.id.m_ev1_title, R.id.m_ev2_title)
    private val TAG_IDS = intArrayOf(R.id.m_ev0_tag, R.id.m_ev1_tag, R.id.m_ev2_tag)

    fun buildViews(context: Context, widgetId: Int, now: Long): RemoteViews {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val selected = WidgetPrefs.monthSelectedDay(context, widgetId, today) ?: today
        val glass = WidgetPrefs.widgetGlass(context, widgetId)
        val p = WidgetUpdater.palette(context, widgetId)
        val base = widgetId * 100 + 60
        val v = RemoteViews(context.packageName, R.layout.widget_month)

        // Hintergrund und feste Farben
        v.setColorStateList(R.id.m_bg_fill, "setImageTintList", p.background)
        v.setViewVisibility(R.id.m_bg_frost, if (glass) View.VISIBLE else View.GONE)
        v.setImageViewResource(R.id.m_bg_frost, p.frost)
        v.setColorStateList(R.id.m_divider, "setImageTintList", p.line)
        for (id in intArrayOf(R.id.m_month) + TITLE_IDS) v.setColorStateList(id, "setTextColor", p.text)
        for (id in intArrayOf(
            R.id.m_year, R.id.m_sel_label, R.id.m_sel_more, R.id.m_empty,
            R.id.m_wd_mo, R.id.m_wd_di, R.id.m_wd_mi, R.id.m_wd_do, R.id.m_wd_fr, R.id.m_wd_sa, R.id.m_wd_so,
        ) + TIME_IDS + TAG_IDS) v.setColorStateList(id, "setTextColor", p.muted)

        // Angezeigter Monat = Monat des gewählten Tags; Raster beginnt am Montag davor
        val first = selected.withDayOfMonth(1)
        val gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        v.setTextViewText(R.id.m_month, MONTH.format(first).uppercase(Locale.GERMAN))
        v.setTextViewText(R.id.m_year, first.year.toString())
        v.setOnClickPendingIntent(R.id.m_header, broadcast(context, widgetId, ACTION_TODAY, null, base + 50))

        // Termine des ganzen Rasters, mit einem Tag Puffer (ganztägige Termine liegen in UTC)
        val from = gridStart.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = gridStart.plusDays(CELLS + 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = CalendarRepository.loadEvents(context, from, to)

        for (i in 0 until CELLS) {
            val day = gridStart.plusDays(i.toLong())
            val inMonth = day.month == first.month
            v.setTextViewText(DAY_IDS[i], day.dayOfMonth.toString())
            when {
                day == today -> {
                    v.setInt(DAY_IDS[i], "setBackgroundResource", R.drawable.day_pill)
                    v.setColorStateList(DAY_IDS[i], "setBackgroundTintList", p.text)
                    v.setColorStateList(DAY_IDS[i], "setTextColor", p.onText)
                }
                day == selected -> {
                    v.setInt(DAY_IDS[i], "setBackgroundResource", R.drawable.day_pill)
                    v.setColorStateList(DAY_IDS[i], "setBackgroundTintList", p.dotOff)
                    v.setColorStateList(DAY_IDS[i], "setTextColor", p.text)
                }
                else -> {
                    v.setInt(DAY_IDS[i], "setBackgroundResource", 0)
                    v.setColorStateList(DAY_IDS[i], "setTextColor", if (inMonth) p.text else p.dotOff)
                }
            }
            val count = eventsOn(events, day, zone).size.coerceAtMost(MAX_DOTS)
            v.setTextViewText(DOTS_IDS[i], "•".repeat(count))
            v.setColorStateList(DOTS_IDS[i], "setTextColor", if (inMonth) p.text else p.dotOff)
            v.setOnClickPendingIntent(CELL_IDS[i], broadcast(context, widgetId, ACTION_SELECT_DAY, day, base + i))
        }

        // Unten: Termine des gewählten Tags
        val dayStartMs = selected.atStartOfDay(zone).toInstant().toEpochMilli()
        v.setOnClickPendingIntent(R.id.m_day_block, WidgetUpdater.calendarIntent(context, dayStartMs, base + 42))
        v.setOnClickPendingIntent(android.R.id.background, WidgetUpdater.calendarIntent(context, dayStartMs, base + 43))
        v.setTextViewText(R.id.m_sel_label, dayLabel(selected, today))

        if (!CalendarRepository.hasPermission(context)) {
            ROW_IDS.forEach { v.setViewVisibility(it, View.GONE) }
            v.setViewVisibility(R.id.m_sel_more, View.GONE)
            v.setViewVisibility(R.id.m_empty, View.VISIBLE)
            v.setTextViewText(R.id.m_empty, "Kalenderzugriff fehlt – tippen zum Einrichten")
            v.setOnClickPendingIntent(R.id.m_day_block, WidgetUpdater.settingsIntent(context, base + 44))
            return v
        }

        val dayEvents = eventsOn(events, selected, zone)
            .sortedWith(compareByDescending<CalEvent> { it.allDay }.thenBy { it.begin }.thenBy { it.title })
        v.setViewVisibility(R.id.m_empty, if (dayEvents.isEmpty()) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.m_empty, "Keine Termine")
        val more = dayEvents.size - ROW_IDS.size
        v.setViewVisibility(R.id.m_sel_more, if (more > 0) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.m_sel_more, "+$more")

        val dayEndMs = selected.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        ROW_IDS.forEachIndexed { k, rowId ->
            val e = dayEvents.getOrNull(k)
            if (e == null) {
                v.setViewVisibility(rowId, View.GONE)
                return@forEachIndexed
            }
            v.setViewVisibility(rowId, View.VISIBLE)
            v.setTextViewText(TITLE_IDS[k], e.title)
            when {
                e.allDay -> {
                    v.setTextViewText(TIME_IDS[k], "")
                    v.setTextViewText(TAG_IDS[k], WidgetUpdater.allDayTag(e, selected, zone))
                }
                e.begin >= dayStartMs -> {
                    v.setTextViewText(TIME_IDS[k], WidgetUpdater.clock(e.begin))
                    v.setTextViewText(TAG_IDS[k], "")
                }
                else -> { // hat am Vortag begonnen
                    v.setTextViewText(TIME_IDS[k], "")
                    v.setTextViewText(TAG_IDS[k], if (e.end < dayEndMs) "BIS ${WidgetUpdater.clock(e.end)}" else "")
                }
            }
            v.setOnClickPendingIntent(rowId, WidgetUpdater.eventIntent(context, e, base + 45 + k))
        }
        return v
    }

    /** Alle Termine, die den Tag berühren (ganztägige über ihre Kalendertage). */
    private fun eventsOn(events: List<CalEvent>, day: LocalDate, zone: ZoneId): List<CalEvent> {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return events.filter {
            if (it.allDay) !it.firstDay(zone).isAfter(day) && !it.lastDay(zone).isBefore(day)
            else it.begin < end && it.end > start
        }
    }

    private fun dayLabel(day: LocalDate, today: LocalDate): String {
        val date = WidgetUpdater.WEEKDAY_DATE.format(day).uppercase(Locale.GERMAN)
        return when (day) {
            today -> "HEUTE · $date"
            today.plusDays(1) -> "MORGEN · $date"
            else -> date
        }
    }

    private fun broadcast(context: Context, widgetId: Int, action: String, day: LocalDate?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MonthWidget::class.java)
            .setAction(action)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // eindeutige URI, damit jede Zelle ihr eigenes PendingIntent bekommt
            .setData(Uri.parse("kalenderwidgets://month/$widgetId/${day?.toEpochDay() ?: "today"}"))
        if (day != null) intent.putExtra(EXTRA_EPOCH_DAY, day.toEpochDay())
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
