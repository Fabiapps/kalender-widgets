package app.kalenderwidgets

import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/**
 * Baut Widget D (Satz) zusammen: der Tag als kurzer Text in vier Zeilen mit Pillen, z. B.
 *
 *     SA [24] (OKT) 14:02
 *     JETZT [VALENCIA ALTSTADT]
 *     NOCH ⌊47 MIN
 *     DANN (TEST) [→] 16:30
 *
 * Gefüllte Pille = Hauptsache der Zeile, umrandete Pille = Nebensache (Monat, späterer Termin,
 * ganztägiger Termin). Zeitangaben sind ab 90 Minuten vorher minutengenau, davor absolut („UM 18:00“).
 */
object SentenceUpdater {

    private const val MINUTE_MS = 60_000L
    /** Darunter Restzeit in Minuten („IN 47 MIN“), darüber absolute Uhrzeit („UM 18:00“). */
    private const val COUNTDOWN_MS = 90 * MINUTE_MS
    /** Darunter „STARTET IN“ mit roter Zeitangabe. */
    private const val SOON_MS = 15 * MINUTE_MS

    private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)

    /** Inhalt einer Satzzeile; leere Felder werden ausgeblendet. */
    private class Line(
        val lead: String? = null,
        val fill: String? = null,
        val outline: String? = null,
        val arrow: Boolean = false,
        val trail: String? = null,
        val bracket: String? = null,
        val urgent: Boolean = false,
        val click: PendingIntent? = null,
    )

    private class LineIds(
        val row: Int, val lead: Int, val fill: Int, val outline: Int, val arrow: Int, val trail: Int, val bracket: Int,
    )

    private val LINE_IDS = arrayOf(
        LineIds(R.id.s_l2, R.id.s_l2_lead, R.id.s_l2_fill, R.id.s_l2_outline, R.id.s_l2_arrow, R.id.s_l2_trail, R.id.s_l2_bracket),
        LineIds(R.id.s_l3, R.id.s_l3_lead, R.id.s_l3_fill, R.id.s_l3_outline, R.id.s_l3_arrow, R.id.s_l3_trail, R.id.s_l3_bracket),
        LineIds(R.id.s_l4, R.id.s_l4_lead, R.id.s_l4_fill, R.id.s_l4_outline, R.id.s_l4_arrow, R.id.s_l4_trail, R.id.s_l4_bracket),
    )

    internal fun buildViews(context: Context, widgetId: Int, s: Snapshot): RemoteViews {
        val glass = WidgetPrefs.widgetGlass(context, widgetId)
        val p = WidgetUpdater.palette(context, widgetId)
        val base = widgetId * 100 + 80
        val v = RemoteViews(context.packageName, R.layout.widget_sentence)

        v.setColorStateList(R.id.s_bg_fill, "setImageTintList", p.background)
        v.setViewVisibility(R.id.s_bg_frost, if (glass) View.VISIBLE else View.GONE)
        v.setImageViewResource(R.id.s_bg_frost, p.frost)

        // Zeile 1: Wochentag, Tag, Monat, Uhrzeit (TextClock läuft selbst; das Datum aktualisiert
        // der Mitternachts-Refresh)
        v.setTextViewText(R.id.s_wd, shortDay(s.today))
        v.setTextViewText(R.id.s_day, s.today.dayOfMonth.toString())
        v.setTextViewText(R.id.s_month, MONTH.format(s.today).trimEnd('.').uppercase(Locale.GERMAN))
        v.setColorStateList(R.id.s_wd, "setTextColor", p.text)
        v.setColorStateList(R.id.s_clock, "setTextColor", p.text)
        styleFilled(v, R.id.s_day, p)
        styleOutline(v, R.id.s_month, p)

        val lines = if (s.hasPermission) {
            v.setOnClickPendingIntent(android.R.id.background, WidgetUpdater.calendarIntent(context, s.now, base))
            compose(context, s, base)
        } else {
            v.setOnClickPendingIntent(android.R.id.background, WidgetUpdater.settingsIntent(context, base + 1))
            listOf(Line(lead = "KALENDER"), Line(lead = "ZUGRIFF FEHLT"), Line(lead = "TIPPEN", arrow = true))
        }
        LINE_IDS.forEachIndexed { i, ids -> applyLine(v, ids, lines.getOrNull(i), p) }
        return v
    }

    /** Stellt die Zeilen 2–4 je nach Lage zusammen (höchstens drei). */
    private fun compose(context: Context, s: Snapshot, base: Int): List<Line> {
        val allDay = s.allDayToday.firstOrNull()
        val allDayLine = allDay?.let {
            val more = s.allDayToday.size - 1
            Line(
                outline = it.title,
                trail = (if (more > 0) "+$more · " else "") + WidgetUpdater.allDayTag(it, s.today, s.zone),
                click = WidgetUpdater.eventIntent(context, it, base + 2),
            )
        }
        // Nächster Termin: ganztägig, falls er vor dem nächsten mit Uhrzeit liegt, sonst mit Uhrzeit
        val next = (if (s.allDayIsNext) s.nextAllDay.firstOrNull() else null) ?: s.nextTimed
        val nextClick = next?.let { WidgetUpdater.eventIntent(context, it, base + 3) }

        val current = s.current
        if (current != null) {
            val currentClick = WidgetUpdater.eventIntent(context, current, base + 4)
            val until = if (current.end - s.now < COUNTDOWN_MS) {
                Line(lead = "NOCH", bracket = "${minutesUntil(current.end, s.now)} MIN", click = currentClick)
            } else {
                Line(lead = "BIS", bracket = timeLabel(current.end, s), click = currentClick)
            }
            val last = when {
                next != null -> Line(lead = "DANN", outline = next.title, arrow = true, trail = shortWhen(next, s), click = nextClick)
                allDayLine != null -> allDayLine
                else -> Line(lead = "DANACH FREI")
            }
            return listOf(Line(lead = "JETZT", fill = current.title, click = currentClick), until, last)
        }

        if (next == null || nextClick == null) {
            return listOfNotNull(allDayLine, Line(lead = "FREI"), Line(lead = "NICHTS GEPLANT")).take(3)
        }
        val countdown = countdownLine(next, s, nextClick)
        return if (allDayLine != null) {
            listOf(allDayLine, Line(lead = "FREI · DANN", fill = next.title, arrow = true, click = nextClick), countdown)
        } else {
            listOf(Line(lead = "FREI · DANN"), Line(fill = next.title, arrow = true, click = nextClick), countdown)
        }
    }

    /** „STARTET IN ⌊3 MIN“ (rot), „IN ⌊47 MIN“, „UM ⌊18:00“, „MORGEN UM ⌊9:00“ oder „MORGEN ⌊GANZTÄGIG“. */
    private fun countdownLine(next: CalEvent, s: Snapshot, click: PendingIntent): Line {
        if (next.allDay) return Line(lead = dayLead(next.firstDay(s.zone), s.today), bracket = "GANZTÄGIG", click = click)
        val left = next.begin - s.now
        return when {
            left < SOON_MS -> Line(lead = "STARTET IN", bracket = "${minutesUntil(next.begin, s.now)} MIN", urgent = true, click = click)
            left < COUNTDOWN_MS -> Line(lead = "IN", bracket = "${minutesUntil(next.begin, s.now)} MIN", click = click)
            else -> {
                val day = next.firstDay(s.zone)
                Line(lead = if (day == s.today) "UM" else "${dayLead(day, s.today)} UM", bracket = WidgetUpdater.clock(next.begin), click = click)
            }
        }
    }

    private fun applyLine(v: RemoteViews, ids: LineIds, line: Line?, p: WidgetUpdater.Palette) {
        if (line == null) {
            v.setViewVisibility(ids.row, View.GONE)
            return
        }
        v.setViewVisibility(ids.row, View.VISIBLE)
        setText(v, ids.lead, line.lead)
        setText(v, ids.fill, line.fill)
        setText(v, ids.outline, line.outline)
        setText(v, ids.trail, line.trail)
        setText(v, ids.bracket, line.bracket)
        v.setViewVisibility(ids.arrow, if (line.arrow) View.VISIBLE else View.GONE)

        v.setColorStateList(ids.lead, "setTextColor", p.text)
        v.setColorStateList(ids.trail, "setTextColor", p.text)
        styleFilled(v, ids.fill, p)
        styleOutline(v, ids.outline, p)
        v.setColorStateList(ids.arrow, "setBackgroundTintList", p.text)
        v.setColorStateList(ids.arrow, "setImageTintList", p.inverse)
        val bracketColor = if (line.urgent) R.color.nothing_red else p.text
        v.setColorStateList(ids.bracket, "setTextColor", bracketColor)
        v.setColorStateList(ids.bracket, "setBackgroundTintList", bracketColor)
        line.click?.let { v.setOnClickPendingIntent(ids.row, it) }
    }

    private fun setText(v: RemoteViews, id: Int, text: String?) {
        if (text.isNullOrEmpty()) {
            v.setViewVisibility(id, View.GONE)
        } else {
            v.setViewVisibility(id, View.VISIBLE)
            v.setTextViewText(id, text)
        }
    }

    private fun styleFilled(v: RemoteViews, id: Int, p: WidgetUpdater.Palette) {
        v.setColorStateList(id, "setBackgroundTintList", p.text)
        v.setColorStateList(id, "setTextColor", p.inverse)
    }

    private fun styleOutline(v: RemoteViews, id: Int, p: WidgetUpdater.Palette) {
        v.setColorStateList(id, "setBackgroundTintList", p.text)
        v.setColorStateList(id, "setTextColor", p.text)
    }

    private fun minutesUntil(time: Long, now: Long): Long =
        ceil((time - now) / MINUTE_MS.toDouble()).toLong().coerceAtLeast(1)

    /** „SA“ */
    private fun shortDay(day: LocalDate): String =
        WidgetUpdater.WEEKDAY.format(day).trimEnd('.').uppercase(Locale.GERMAN)

    /** „MORGEN“ für morgen, sonst der Wochentag („MO“). */
    private fun dayLead(day: LocalDate, today: LocalDate): String =
        if (day == today.plusDays(1)) "MORGEN" else shortDay(day)

    /** Kurzangabe hinter dem späteren Termin: Uhrzeit heute, sonst „MORGEN“ bzw. Wochentag. */
    private fun shortWhen(e: CalEvent, s: Snapshot): String {
        val day = e.firstDay(s.zone)
        return when {
            e.allDay && day == s.today -> "HEUTE"
            !e.allDay && day == s.today -> WidgetUpdater.clock(e.begin)
            else -> dayLead(day, s.today)
        }
    }

    /** Ende eines Termins: „20:00“ heute, sonst mit Tag („MORGEN 10:00“). */
    private fun timeLabel(time: Long, s: Snapshot): String {
        val day = Instant.ofEpochMilli(time).atZone(s.zone).toLocalDate()
        val clock = WidgetUpdater.clock(time)
        return if (day == s.today) clock else "${dayLead(day, s.today)} $clock"
    }
}
