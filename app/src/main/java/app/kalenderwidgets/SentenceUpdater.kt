package app.kalenderwidgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

/**
 * Baut Widget D (Satz) zusammen: der Tag als kurzer Text in bis zu fünf Zeilen mit Pillen, z. B.
 *
 *     SA [24] (OKT) 18:54
 *     JETZT [VALENCIA ALTSTADT]
 *     NOCH ⌊66 MIN · BIS 20:00
 *     DANN 21:00 [→] (TISCH IM TINNTORERA)
 *     (URLAUB VALENCIA) BIS SO.
 *
 * Gefüllte Pille = Hauptsache der Zeile, umrandete Pille = Nebensache (Monat, späterer Termin,
 * ganztägiger Termin), grau = Zusatzinfo. Farben wie in den anderen Widgets: der heutige Tag ist
 * eine rote Pille (wie in Widget C), der laufende Termin eine gelbe (wie sein Balken in Widget B).
 * Zeitangaben sind ab 90 Minuten vorher minutengenau, davor absolut („UM 18:00“).
 *
 * Terminnamen bekommen den Platz, der in ihrer Zeile übrig bleibt. Dafür werden die Texte für jede
 * Widget-Größe, die der Launcher meldet, mit Paint gemessen (wie die Fußzeile von Widget B). Reicht
 * der Platz nicht, fallen zuerst Pfeil und graue Zusatzinfo weg, erst dann wird der Name gekürzt.
 */
object SentenceUpdater {

    private const val MINUTE_MS = 60_000L
    /** Darunter Restzeit in Minuten („IN 47 MIN“), darüber absolute Uhrzeit („UM 18:00“). */
    private const val COUNTDOWN_MS = 90 * MINUTE_MS
    /** Darunter „STARTET IN“ mit roter Zeitangabe. */
    private const val SOON_MS = 15 * MINUTE_MS

    // Maße aus widget_sentence.xml (dort mitändern)
    private const val TEXT_SP = 16f
    private const val LETTER_SPACING = 0.02f
    private const val PADDING_H_DP = 18f + 16f
    private const val GAP_DP = 6f
    private const val PILL_PADDING_DP = 8f + 8f
    private const val BRACKET_PADDING_DP = 5f + 4f
    private const val ARROW_DP = 32f
    /** Ab dieser Höhe passen fünf Zeilen (5 × 24dp + 4 × 3dp Abstand + 2 × 8dp Rand), sonst vier. */
    private const val FIVE_ROWS_MIN_HEIGHT_DP = 148f

    /** Puffer gegen Rundungsunterschiede zwischen Messung und TextView. */
    private const val SAFETY_DP = 4f
    private const val TITLE_RESERVE_DP = 3f
    /** Bliebe dem Namen weniger Platz, fallen Pfeil und Zusatzinfo weg, auch wenn er gekürzt bleibt. */
    private const val MIN_TITLE_DP = 96f
    private const val MIN_PILL_DP = 40f

    /** Mehr Fassungen nimmt RemoteViews nicht an. */
    private const val MAX_SIZES = 16
    /** Falls der Launcher keine Größe meldet: minWidth × minHeight aus widget_sentence_info.xml. */
    private val DEFAULT_SIZE = SizeF(250f, 140f)

    private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)

    private enum class Pill { FILLED, RUNNING, OUTLINE }

    /**
     * Inhalt einer Satzzeile in fester Reihenfolge: Text, Winkel, Pfeil, Titel-Pille, graue
     * Zusatzinfo. Leere Felder werden ausgeblendet; Pfeil und Zusatzinfo sind verzichtbar.
     */
    private class Line(
        val lead: String? = null,
        val bracket: String? = null,
        val arrow: Boolean = false,
        val title: String? = null,
        val pill: Pill = Pill.OUTLINE,
        val trail: String? = null,
        /** Winkel rot: der Termin beginnt gleich. */
        val urgent: Boolean = false,
        val click: PendingIntent? = null,
    )

    private class LineIds(
        val row: Int, val lead: Int, val bracket: Int, val arrow: Int, val fill: Int, val outline: Int, val trail: Int,
    )

    private val LINE_IDS = arrayOf(
        LineIds(R.id.s_l2, R.id.s_l2_lead, R.id.s_l2_bracket, R.id.s_l2_arrow, R.id.s_l2_fill, R.id.s_l2_outline, R.id.s_l2_trail),
        LineIds(R.id.s_l3, R.id.s_l3_lead, R.id.s_l3_bracket, R.id.s_l3_arrow, R.id.s_l3_fill, R.id.s_l3_outline, R.id.s_l3_trail),
        LineIds(R.id.s_l4, R.id.s_l4_lead, R.id.s_l4_bracket, R.id.s_l4_arrow, R.id.s_l4_fill, R.id.s_l4_outline, R.id.s_l4_trail),
        LineIds(R.id.s_l5, R.id.s_l5_lead, R.id.s_l5_bracket, R.id.s_l5_arrow, R.id.s_l5_fill, R.id.s_l5_outline, R.id.s_l5_trail),
    )

    internal fun buildViews(context: Context, widgetId: Int, s: Snapshot, options: Bundle): RemoteViews {
        val glass = WidgetPrefs.widgetBackdrop(context, widgetId) == Backdrop.GLASS
        val p = WidgetUpdater.palette(context, widgetId)
        val base = widgetId * 100 + 80
        val (lines, background) = if (s.hasPermission) {
            compose(context, s, base) to WidgetUpdater.calendarIntent(context, s.now, base)
        } else {
            listOf(Line(lead = "KALENDER"), Line(lead = "ZUGRIFF FEHLT"), Line(lead = "TIPPEN", arrow = true)) to
                WidgetUpdater.settingsIntent(context, base + 1)
        }
        val measure = Measure(context)

        fun views(size: SizeF): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_sentence)
            v.setColorStateList(R.id.s_bg_fill, "setImageTintList", p.background)
            v.setViewVisibility(R.id.s_bg_frost, if (glass) View.VISIBLE else View.GONE)
            v.setImageViewResource(R.id.s_bg_frost, p.frost)
            v.setOnClickPendingIntent(android.R.id.background, background)

            // Zeile 1: Wochentag, Tag, Monat, Uhrzeit (TextClock läuft selbst; das Datum aktualisiert
            // der Mitternachts-Refresh)
            v.setTextViewText(R.id.s_wd, shortDay(s.today))
            v.setTextViewText(R.id.s_day, s.today.dayOfMonth.toString())
            v.setTextViewText(R.id.s_month, MONTH.format(s.today).trimEnd('.').uppercase(Locale.GERMAN))
            v.setColorStateList(R.id.s_wd, "setTextColor", p.text)
            v.setColorStateList(R.id.s_clock, "setTextColor", p.text)
            // Heute: rote Pille mit weißer Zahl (wie der heutige Tag in Widget C)
            v.setColorStateList(R.id.s_day, "setBackgroundTintList", R.color.nothing_red)
            v.setColorStateList(R.id.s_day, "setTextColor", R.color.month_today_text)
            styleOutline(v, R.id.s_month, p)

            // Ist das Widget zu niedrig für fünf Zeilen, entfällt die letzte (ganztägig bzw. danach)
            val maxLines = if (size.height >= FIVE_ROWS_MIN_HEIGHT_DP) LINE_IDS.size else LINE_IDS.size - 1
            val contentDp = size.width - PADDING_H_DP
            LINE_IDS.forEachIndexed { i, ids ->
                applyLine(v, ids, lines.getOrNull(i)?.takeIf { i < maxLines }, contentDp, measure, p, background)
            }
            return v
        }

        // Eine Fassung je Größe, die der Launcher meldet (z. B. hochkant und quer); er zeigt die passende
        val sizes = widgetSizes(options)
        return if (sizes.size == 1) views(sizes[0]) else RemoteViews(sizes.associateWith { views(it) })
    }

    /** Stellt die Satzzeilen je nach Lage zusammen (höchstens vier). */
    private fun compose(context: Context, s: Snapshot, base: Int): List<Line> {
        val allDayLine = s.allDayToday.firstOrNull()?.let {
            val more = s.allDayToday.size - 1
            Line(
                title = caps(it.title),
                trail = (if (more > 0) "+$more · " else "") + WidgetUpdater.allDayTag(it, s.today, s.zone),
                click = WidgetUpdater.eventIntent(context, it, base + 2),
            )
        }
        val upcoming = upcoming(s)
        val next = upcoming.getOrNull(0)
        val nextClick = next?.let { WidgetUpdater.eventIntent(context, it, base + 3) }
        // Übernächster Termin, falls kein ganztägiger die letzte Zeile braucht
        val afterNextLine = upcoming.getOrNull(1)?.let {
            Line(lead = "DANACH ${startLabel(it, s)}", title = caps(it.title), click = WidgetUpdater.eventIntent(context, it, base + 5))
        }

        val current = s.current
        if (current != null) {
            val currentClick = WidgetUpdater.eventIntent(context, current, base + 4)
            val until = if (current.end - s.now < COUNTDOWN_MS) {
                Line(
                    lead = "NOCH", bracket = "${minutesUntil(current.end, s.now)} MIN",
                    trail = "· BIS ${untilLabel(current.end, s)}", click = currentClick,
                )
            } else {
                Line(
                    lead = "BIS", bracket = untilLabel(current.end, s),
                    trail = "· SEIT ${sinceLabel(current.begin, s)}", click = currentClick,
                )
            }
            val then = if (next != null) {
                Line(lead = "DANN ${startLabel(next, s)}", arrow = true, title = caps(next.title), click = nextClick)
            } else {
                Line(lead = "DANACH FREI")
            }
            return listOfNotNull(
                Line(lead = "JETZT", title = caps(current.title), pill = Pill.RUNNING, click = currentClick),
                until,
                then,
                allDayLine ?: afterNextLine,
            )
        }

        if (next == null || nextClick == null) {
            return listOfNotNull(Line(lead = "JETZT FREI"), Line(lead = "NICHTS GEPLANT"), allDayLine)
        }
        // „FREI BIS 21:00“ nur zusammen mit der Restzeit; sonst stünde die Uhrzeit doppelt da
        val soon = !next.allDay && next.begin - s.now < COUNTDOWN_MS
        return listOfNotNull(
            Line(lead = if (soon) "FREI BIS ${WidgetUpdater.clock(next.begin)}" else "JETZT FREI", click = nextClick),
            Line(lead = "DANN", arrow = true, title = caps(next.title), pill = Pill.FILLED, click = nextClick),
            countdownLine(next, s, nextClick),
            allDayLine ?: afterNextLine,
        )
    }

    /**
     * Wann der nächste Termin beginnt und bis wann er geht: „STARTET IN ⌊3 MIN“ (rot), „IN ⌊47 MIN“,
     * „UM ⌊18:00“ bzw. „MORGEN UM ⌊09:00“, jeweils mit „· BIS 19:30“; ganztägig „MORGEN ⌊GANZTÄGIG“.
     */
    private fun countdownLine(next: CalEvent, s: Snapshot, click: PendingIntent): Line {
        val day = next.firstDay(s.zone)
        if (next.allDay) {
            val tag = WidgetUpdater.allDayTag(next, day, s.zone).takeIf { it != "GANZTÄGIG" }
            return Line(lead = dayLead(day, s.today), bracket = "GANZTÄGIG", trail = tag?.let { "· $it" }, click = click)
        }
        val end = "· BIS ${endLabel(next, s)}"
        val left = next.begin - s.now
        return when {
            left < SOON_MS -> Line(
                lead = "STARTET IN", bracket = "${minutesUntil(next.begin, s.now)} MIN", trail = end,
                urgent = true, click = click,
            )
            left < COUNTDOWN_MS -> Line(lead = "IN", bracket = "${minutesUntil(next.begin, s.now)} MIN", trail = end, click = click)
            else -> Line(
                lead = if (day == s.today) "UM" else "${dayLead(day, s.today)} UM", bracket = WidgetUpdater.clock(next.begin),
                trail = end, click = click,
            )
        }
    }

    /**
     * Kommende Termine nach Beginn: mit Uhrzeit ab jetzt, ganztägige ab morgen (sie zählen ab
     * Mitternacht, liegen also vor Terminen mit Uhrzeit am selben Tag).
     */
    private fun upcoming(s: Snapshot): List<CalEvent> {
        fun start(e: CalEvent): Long =
            if (e.allDay) e.firstDay(s.zone).atStartOfDay(s.zone).toInstant().toEpochMilli() else e.begin
        return s.allEvents
            .filter { if (it.allDay) it.firstDay(s.zone).isAfter(s.today) else it.begin > s.now }
            .sortedWith(compareBy<CalEvent>({ start(it) }, { it.title }))
    }

    private fun applyLine(
        v: RemoteViews, ids: LineIds, line: Line?, contentDp: Float, m: Measure, p: WidgetUpdater.Palette,
        background: PendingIntent,
    ) {
        if (line == null) {
            v.setViewVisibility(ids.row, View.GONE)
            return
        }
        val fit = fit(line, contentDp, m)
        val pillId = if (line.pill == Pill.OUTLINE) ids.outline else ids.fill
        v.setViewVisibility(ids.row, View.VISIBLE)
        setText(v, ids.lead, line.lead)
        setText(v, ids.bracket, line.bracket)
        v.setViewVisibility(ids.arrow, if (fit.arrow) View.VISIBLE else View.GONE)
        setText(v, ids.fill, line.title.takeIf { pillId == ids.fill })
        setText(v, ids.outline, line.title.takeIf { pillId == ids.outline })
        if (line.title != null) v.setViewLayoutWidth(pillId, fit.titleDp, TypedValue.COMPLEX_UNIT_DIP)
        setText(v, ids.trail, line.trail.takeIf { fit.trail })

        v.setColorStateList(ids.lead, "setTextColor", p.text)
        v.setColorStateList(ids.trail, "setTextColor", p.muted)
        val bracketColor = if (line.urgent) R.color.nothing_red else p.text
        v.setColorStateList(ids.bracket, "setTextColor", bracketColor)
        v.setColorStateList(ids.bracket, "setBackgroundTintList", bracketColor)
        v.setColorStateList(ids.arrow, "setBackgroundTintList", p.text)
        v.setColorStateList(ids.arrow, "setImageTintList", p.inverse)
        if (line.pill == Pill.RUNNING) {
            // Laufender Termin: gelbe Pille mit dunkler Schrift (wie sein Balken in Widget B)
            v.setColorStateList(ids.fill, "setBackgroundTintList", R.color.nothing_yellow)
            v.setColorStateList(ids.fill, "setTextColor", R.color.month_selected_text)
        } else {
            v.setColorStateList(ids.fill, "setBackgroundTintList", p.text)
            v.setColorStateList(ids.fill, "setTextColor", p.inverse)
        }
        styleOutline(v, ids.outline, p)
        // Immer setzen: Der Launcher wendet neue RemoteViews auf die bestehenden Views an, ein
        // früherer Termin bliebe sonst an der Zeile hängen
        v.setOnClickPendingIntent(ids.row, line.click ?: background)
    }

    /** Was von einer Zeile sichtbar ist und wie breit die Titel-Pille wird (dp). */
    private class Fit(val arrow: Boolean, val trail: Boolean, val titleDp: Float)

    /**
     * Verteilt die Zeilenbreite: Der Name soll ganz sichtbar sein. Der Pfeil ist nur Schmuck und
     * bleibt nur, wenn der Name trotzdem ganz passt. Die graue Zusatzinfo weicht nur, wenn der Name
     * dadurch ganz passt oder ihm sonst weniger als MIN_TITLE_DP blieben. Was dann noch fehlt,
     * kürzt die Pille mit „…“.
     */
    private fun fit(line: Line, contentDp: Float, m: Measure): Fit {
        val limit = contentDp - SAFETY_DP
        // Breite aller sichtbaren Teile außer dem Namen, samt Abständen (auch dem zum Namen)
        fun others(arrow: Boolean, trail: Boolean): Float {
            val widths = listOfNotNull(
                line.lead?.let { m.dp(it) },
                line.bracket?.let { m.dp(it) + BRACKET_PADDING_DP },
                if (arrow) ARROW_DP else null,
                if (trail) line.trail?.let { m.dp(it) } else null,
            )
            val parts = widths.size + (if (line.title != null) 1 else 0)
            return widths.sum() + GAP_DP * (parts - 1).coerceAtLeast(0)
        }
        val hasTrail = line.trail != null
        val title = line.title ?: return Fit(line.arrow, hasTrail && others(line.arrow, true) <= limit, 0f)

        val need = m.dp(title) + PILL_PADDING_DP
        fun room(arrow: Boolean, trail: Boolean) = limit - others(arrow, trail)
        val arrow = line.arrow && room(true, hasTrail) >= need
        val trail = hasTrail &&
            (room(arrow, true) >= need || (room(arrow, false) < need && room(arrow, true) >= MIN_TITLE_DP))
        val width = minOf(need + TITLE_RESERVE_DP, room(arrow, trail)).coerceAtLeast(MIN_PILL_DP)
        return Fit(arrow, trail, width)
    }

    /** Misst Texte wie die TextViews im Layout (16sp Monospace, leicht gesperrt), Ergebnis in dp. */
    private class Measure(context: Context) {
        private val metrics = context.resources.displayMetrics
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            letterSpacing = LETTER_SPACING
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP, metrics)
        }

        fun dp(text: String): Float = paint.measureText(text) / metrics.density
    }

    /** Größen, in denen der Launcher das Widget zeigt (z. B. hochkant und quer), in dp. */
    private fun widgetSizes(options: Bundle): List<SizeF> {
        val sizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        } else {
            @Suppress("DEPRECATION")
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        }
        if (!sizes.isNullOrEmpty()) return sizes.distinct().take(MAX_SIZES)
        // Ohne Liste: hochkant ist das Widget MIN_WIDTH breit und MAX_HEIGHT hoch
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return listOf(if (width > 0 && height > 0) SizeF(width.toFloat(), height.toFloat()) else DEFAULT_SIZE)
    }

    private fun setText(v: RemoteViews, id: Int, text: String?) {
        if (text.isNullOrEmpty()) {
            v.setViewVisibility(id, View.GONE)
        } else {
            v.setViewVisibility(id, View.VISIBLE)
            v.setTextViewText(id, text)
        }
    }

    private fun styleOutline(v: RemoteViews, id: Int, p: WidgetUpdater.Palette) {
        v.setColorStateList(id, "setBackgroundTintList", p.text)
        v.setColorStateList(id, "setTextColor", p.text)
    }

    /** Großgeschrieben wie im Widget (textAllCaps), damit die Messung zur Anzeige passt. */
    private fun caps(text: String): String = text.uppercase(Locale.GERMAN)

    private fun minutesUntil(time: Long, now: Long): Long =
        ceil((time - now) / MINUTE_MS.toDouble()).toLong().coerceAtLeast(1)

    /** „SA“ */
    private fun shortDay(day: LocalDate): String =
        WidgetUpdater.WEEKDAY.format(day).trimEnd('.').uppercase(Locale.GERMAN)

    /** „MORGEN“ für morgen, sonst der Wochentag („MO“), ab einer Woche das Datum („31.10.“). */
    private fun dayLead(day: LocalDate, today: LocalDate): String = when {
        day == today.plusDays(1) -> "MORGEN"
        ChronoUnit.DAYS.between(today, day) in 2..6 -> shortDay(day)
        else -> WidgetUpdater.DAY_MONTH.format(day)
    }

    /** Beginn eines kommenden Termins: „21:00“ heute, sonst mit Tag („MORGEN 09:00“); ganztägig nur der Tag. */
    private fun startLabel(e: CalEvent, s: Snapshot): String {
        val day = e.firstDay(s.zone)
        if (e.allDay) return dayLead(day, s.today)
        val clock = WidgetUpdater.clock(e.begin)
        return if (day == s.today) clock else "${dayLead(day, s.today)} $clock"
    }

    /** Ende eines kommenden Termins: nur die Uhrzeit, wenn er am selben Tag endet, sonst mit Tag. */
    private fun endLabel(e: CalEvent, s: Snapshot): String {
        val clock = WidgetUpdater.clock(e.end)
        val lastDay = e.lastDay(s.zone)
        return if (lastDay == e.firstDay(s.zone)) clock else "${dayLead(lastDay, s.today)} $clock"
    }

    /**
     * Ende des laufenden Termins: „20:00“ heute, sonst mit Tag („MORGEN 10:00“). Ein Ende um
     * Mitternacht zählt noch zu heute („BIS 00:00“ statt „BIS MORGEN 00:00“).
     */
    private fun untilLabel(end: Long, s: Snapshot): String {
        val day = Instant.ofEpochMilli(end - 1).atZone(s.zone).toLocalDate()
        val clock = WidgetUpdater.clock(end)
        return if (day == s.today) clock else "${dayLead(day, s.today)} $clock"
    }

    /** Beginn des laufenden Termins: „14:00“ heute, sonst mit Tag („GESTERN 22:00“, „MO 22:00“, „17.10. 22:00“). */
    private fun sinceLabel(time: Long, s: Snapshot): String {
        val day = Instant.ofEpochMilli(time).atZone(s.zone).toLocalDate()
        val clock = WidgetUpdater.clock(time)
        return when (ChronoUnit.DAYS.between(day, s.today)) {
            0L -> clock
            1L -> "GESTERN $clock"
            in 2L..6L -> "${shortDay(day)} $clock"
            else -> "${WidgetUpdater.DAY_MONTH.format(day)} $clock"
        }
    }
}
