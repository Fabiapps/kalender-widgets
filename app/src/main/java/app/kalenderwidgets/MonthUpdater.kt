package app.kalenderwidgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Baut Widget C (Monat) zusammen: Monatsraster mit Terminpunkten (feste Höhe), darunter die
 * scrollbare Terminliste des gewählten Tags. Tippen auf einen Tag wählt ihn aus, erneutes Tippen auf
 * den gewählten Tag öffnet die Terminerstellung für diesen Tag. Pfeile wechseln den Monat.
 */
object MonthUpdater {

    const val ACTION_SELECT_DAY = "app.kalenderwidgets.action.MONTH_SELECT_DAY"
    const val ACTION_TODAY = "app.kalenderwidgets.action.MONTH_TODAY"
    const val ACTION_PREV = "app.kalenderwidgets.action.MONTH_PREV"
    const val ACTION_NEXT = "app.kalenderwidgets.action.MONTH_NEXT"
    const val EXTRA_EPOCH_DAY = "epoch_day"

    private const val CELLS = 42
    private const val MAX_DOTS = 3
    private const val HOUR_MS = 60 * 60 * 1000L
    /** Vorgeschlagene Startzeit für neue Termine an anderen Tagen als heute. */
    private const val NEW_EVENT_HOUR = 9

    // Request-Codes je Widget: Zellen 0–41, Steuerung ab 100, Activities ab 200
    private const val RC_TODAY = 100
    private const val RC_PREV = 101
    private const val RC_NEXT = 102
    private const val RC_NEW_EVENT = 200
    private const val RC_DAY_CALENDAR = 201
    private const val RC_BACKGROUND = 202
    private const val RC_LIST_TEMPLATE = 203
    private const val RC_SETTINGS = 204

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

    /** Ziel beim Monatswechsel: im aktuellen Monat heute, sonst der Erste des Monats. */
    fun selectionForMonth(month: YearMonth, today: LocalDate): LocalDate =
        if (month == YearMonth.from(today)) today else month.atDay(1)

    fun buildViews(context: Context, widgetId: Int, now: Long): RemoteViews {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val selected = WidgetPrefs.monthSelectedDay(context, widgetId, today) ?: today
        val glass = WidgetPrefs.widgetBackdrop(context, widgetId) == Backdrop.GLASS
        val p = WidgetUpdater.palette(context, widgetId)
        val base = widgetId * 1000
        val v = RemoteViews(context.packageName, R.layout.widget_month)

        // Hintergrund und feste Farben
        v.setColorStateList(R.id.m_bg_fill, "setImageTintList", p.background)
        v.setViewVisibility(R.id.m_bg_frost, if (glass) View.VISIBLE else View.GONE)
        v.setImageViewResource(R.id.m_bg_frost, p.frost)
        v.setColorStateList(R.id.m_divider, "setImageTintList", p.line)
        v.setColorStateList(R.id.m_prev, "setImageTintList", p.text)
        v.setColorStateList(R.id.m_next, "setImageTintList", p.text)
        v.setColorStateList(R.id.m_month, "setTextColor", p.text)
        // „HEUTE“-Pille: dezente Fläche, rote Schrift wie der heutige Tag
        v.setColorStateList(R.id.m_today, "setBackgroundTintList", p.line)
        v.setColorStateList(R.id.m_today, "setTextColor", R.color.month_today)
        for (id in intArrayOf(
            R.id.m_year, R.id.m_sel_label, R.id.m_empty,
            R.id.m_wd_mo, R.id.m_wd_di, R.id.m_wd_mi, R.id.m_wd_do, R.id.m_wd_fr, R.id.m_wd_sa, R.id.m_wd_so,
        )) v.setColorStateList(id, "setTextColor", p.muted)

        // Kopfzeile: angezeigter Monat = Monat des gewählten Tags
        val month = YearMonth.from(selected)
        v.setTextViewText(R.id.m_month, MONTH.format(month).uppercase(Locale.GERMAN))
        v.setTextViewText(R.id.m_year, month.year.toString())
        v.setViewVisibility(R.id.m_today, if (selected != today) View.VISIBLE else View.GONE)
        v.setOnClickPendingIntent(R.id.m_month, broadcast(context, widgetId, ACTION_TODAY, null, base + RC_TODAY))
        v.setOnClickPendingIntent(R.id.m_today, broadcast(context, widgetId, ACTION_TODAY, null, base + RC_TODAY))
        v.setOnClickPendingIntent(R.id.m_prev, broadcast(context, widgetId, ACTION_PREV, null, base + RC_PREV))
        v.setOnClickPendingIntent(R.id.m_next, broadcast(context, widgetId, ACTION_NEXT, null, base + RC_NEXT))

        // Raster beginnt am Montag vor dem Ersten; Termine des ganzen Rasters mit einem Tag Puffer
        // (ganztägige Termine liegen in UTC)
        val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val from = gridStart.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = gridStart.plusDays(CELLS + 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = CalendarRepository.loadEvents(context, from, to)

        for (i in 0 until CELLS) {
            val day = gridStart.plusDays(i.toLong())
            val inMonth = YearMonth.from(day) == month
            v.setTextViewText(DAY_IDS[i], day.dayOfMonth.toString())
            when {
                day == today -> { // rot
                    v.setInt(DAY_IDS[i], "setBackgroundResource", R.drawable.day_pill)
                    v.setColorStateList(DAY_IDS[i], "setBackgroundTintList", R.color.month_today)
                    v.setColorStateList(DAY_IDS[i], "setTextColor", R.color.month_today_text)
                }
                day == selected -> { // gelb
                    v.setInt(DAY_IDS[i], "setBackgroundResource", R.drawable.day_pill)
                    v.setColorStateList(DAY_IDS[i], "setBackgroundTintList", R.color.nothing_yellow)
                    v.setColorStateList(DAY_IDS[i], "setTextColor", R.color.month_selected_text)
                }
                else -> {
                    v.setInt(DAY_IDS[i], "setBackgroundResource", 0)
                    v.setColorStateList(DAY_IDS[i], "setTextColor", if (inMonth) p.text else p.dotOff)
                }
            }
            val count = eventsOn(events, day, zone).size.coerceAtMost(MAX_DOTS)
            v.setTextViewText(DOTS_IDS[i], "•".repeat(count))
            v.setColorStateList(DOTS_IDS[i], "setTextColor", if (inMonth) p.text else p.dotOff)
            // Gewählter Tag: erneutes Tippen legt einen Termin an; andere Tage werden ausgewählt
            v.setOnClickPendingIntent(
                CELL_IDS[i],
                if (day == selected) newEventIntent(context, day, today, now, zone, base + RC_NEW_EVENT)
                else broadcast(context, widgetId, ACTION_SELECT_DAY, day, base + i),
            )
        }

        // Unten: Termine des gewählten Tags
        val dayStartMs = selected.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEndMs = selected.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        v.setTextViewText(R.id.m_sel_label, dayLabel(selected, today))
        v.setOnClickPendingIntent(R.id.m_sel_label, WidgetUpdater.calendarIntent(context, dayStartMs, base + RC_DAY_CALENDAR))
        v.setOnClickPendingIntent(android.R.id.background, WidgetUpdater.calendarIntent(context, dayStartMs, base + RC_BACKGROUND))
        v.setEmptyView(R.id.m_list, R.id.m_empty)

        val items = RemoteViews.RemoteCollectionItems.Builder()
        if (!CalendarRepository.hasPermission(context)) {
            v.setTextViewText(R.id.m_empty, "Kalenderzugriff fehlt – tippen zum Einrichten")
            v.setOnClickPendingIntent(R.id.m_empty, WidgetUpdater.settingsIntent(context, base + RC_SETTINGS))
        } else {
            v.setTextViewText(R.id.m_empty, "Keine Termine")
            v.setOnClickPendingIntent(R.id.m_empty, WidgetUpdater.calendarIntent(context, dayStartMs, base + RC_DAY_CALENDAR))
            eventsOn(events, selected, zone)
                .sortedWith(compareByDescending<CalEvent> { it.allDay }.thenBy { it.begin }.thenBy { it.title })
                .forEachIndexed { k, e -> items.addItem(k.toLong(), eventItem(context, e, selected, dayStartMs, dayEndMs, zone, p)) }
        }
        v.setRemoteAdapter(R.id.m_list, items.setViewTypeCount(1).build())
        // Listeneinträge öffnen ihren Termin über EventOpenActivity (Extras kommen per Fill-in)
        v.setPendingIntentTemplate(
            R.id.m_list,
            PendingIntent.getActivity(
                context, base + RC_LIST_TEMPLATE,
                Intent(context, EventOpenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        return v
    }

    /** Eine Zeile der Terminliste: Uhrzeit | Titel | Wann-Angabe. */
    private fun eventItem(
        context: Context, e: CalEvent, day: LocalDate, dayStartMs: Long, dayEndMs: Long,
        zone: ZoneId, p: WidgetUpdater.Palette,
    ): RemoteViews {
        val item = RemoteViews(context.packageName, R.layout.widget_month_event_item)
        item.setColorStateList(R.id.m_item_title, "setTextColor", p.text)
        item.setColorStateList(R.id.m_item_time, "setTextColor", p.muted)
        item.setColorStateList(R.id.m_item_tag, "setTextColor", p.muted)
        item.setTextViewText(R.id.m_item_title, e.title)
        when {
            e.allDay -> {
                item.setTextViewText(R.id.m_item_time, "")
                item.setTextViewText(R.id.m_item_tag, WidgetUpdater.allDayTag(e, day, zone))
            }
            e.begin >= dayStartMs -> {
                item.setTextViewText(R.id.m_item_time, WidgetUpdater.clock(e.begin))
                item.setTextViewText(R.id.m_item_tag, "")
            }
            else -> { // hat am Vortag begonnen
                item.setTextViewText(R.id.m_item_time, "")
                item.setTextViewText(R.id.m_item_tag, if (e.end < dayEndMs) "BIS ${WidgetUpdater.clock(e.end)}" else "")
            }
        }
        item.setOnClickFillInIntent(
            R.id.m_item,
            Intent()
                .putExtra(EventOpenActivity.EXTRA_EVENT_ID, e.id)
                .putExtra(EventOpenActivity.EXTRA_BEGIN, e.begin)
                .putExtra(EventOpenActivity.EXTRA_END, e.end)
                .putExtra(EventOpenActivity.EXTRA_ALL_DAY, e.allDay),
        )
        return item
    }

    /**
     * Öffnet die Terminerstellung der Kalender-App für [day]: heute ab der nächsten vollen Stunde,
     * an anderen Tagen ab [NEW_EVENT_HOUR] Uhr, jeweils eine Stunde lang.
     */
    private fun newEventIntent(
        context: Context, day: LocalDate, today: LocalDate, now: Long, zone: ZoneId, requestCode: Int,
    ): PendingIntent {
        val start = if (day == today) {
            val nextHour = Instant.ofEpochMilli(now).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
            if (nextHour.toLocalDate() == day) nextHour else day.atTime(23, 0).atZone(zone)
        } else {
            day.atTime(NEW_EVENT_HOUR, 0).atZone(zone)
        }
        val begin = start.toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + HOUR_MS)
        return WidgetUpdater.activityIntent(context, requestCode, intent)
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
        val key = day?.toEpochDay()?.toString() ?: action.substringAfterLast('_').lowercase(Locale.ROOT)
        val intent = Intent(context, MonthWidget::class.java)
            .setAction(action)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // eindeutige URI, damit jede Zelle bzw. Schaltfläche ihr eigenes PendingIntent bekommt
            .setData(Uri.parse("kalenderwidgets://month/$widgetId/$key"))
        if (day != null) intent.putExtra(EXTRA_EPOCH_DAY, day.toEpochDay())
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
