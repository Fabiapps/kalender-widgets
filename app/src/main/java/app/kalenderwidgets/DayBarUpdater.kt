package app.kalenderwidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Baut Widget B (Tagesleiste) zusammen. */
object DayBarUpdater {

    private const val HOUR_MS = 60 * 60_000L
    private const val LEAD_MS = 1 * HOUR_MS
    private const val AHEAD_MS = 12 * HOUR_MS
    private const val LABEL_STEP_HOURS = 3
    // Fußzeile: Abstand zwischen den Blöcken, Reserve für Messungenauigkeit, seitlicher Rand der Layouts
    private const val FOOTER_GAP_DP = 16f
    private const val TEXT_RESERVE_DP = 4f
    private const val CONTENT_PADDING_DP = 16
    // Muss zur Höhe von db_timeline in den Layouts passen
    private const val TIMELINE_HEIGHT_DP = 34

    internal fun buildViews(context: Context, widgetId: Int, s: Snapshot, options: Bundle): RemoteViews {
        val glass = WidgetPrefs.widgetGlass(context, widgetId)
        val p = WidgetUpdater.palette(context, widgetId)
        val base = widgetId * 100 + 50
        val timeline = if (s.hasPermission) drawTimeline(context, s, options, p)
                       else emptyTimelineBitmap(context, options, p)

        fun applyAll(v: RemoteViews) {
            // Hintergrund
            v.setColorStateList(R.id.db_bg_fill, "setImageTintList", p.background)
            v.setViewVisibility(R.id.db_bg_stroke, if (glass) View.VISIBLE else View.GONE)
            // Farben – alle Views upfront, vor jedem möglichen frühzeitigen Return
            for (id in intArrayOf(R.id.db_clock, R.id.db_allday_title, R.id.db_current_title, R.id.db_next_title)) {
                v.setColorStateList(id, "setTextColor", p.text)
            }
            for (id in intArrayOf(R.id.db_weekday, R.id.db_date, R.id.db_allday_detail, R.id.db_current_label, R.id.db_next_label)) {
                v.setColorStateList(id, "setTextColor", p.muted)
            }
            v.setColorStateList(R.id.db_allday_row, "setBackgroundTintList", p.line)
            v.setOnClickPendingIntent(android.R.id.background, WidgetUpdater.calendarIntent(context, s.now, base + 3))
            v.setImageViewBitmap(R.id.db_timeline, timeline)

            if (!s.hasPermission) {
                setAllDayVisible(v, View.GONE)
                v.setTextViewText(R.id.db_current_label, "KALENDER")
                v.setTextViewText(R.id.db_current_title, "Zugriff fehlt")
                v.setTextViewText(R.id.db_next_label, "")
                v.setTextViewText(R.id.db_next_title, "Tippen zum Einrichten")
                v.setOnClickPendingIntent(android.R.id.background, WidgetUpdater.settingsIntent(context, base + 4))
                return
            }

            // Ganztägige Termine
            val firstAllDay = s.allDayToday.firstOrNull()
            if (firstAllDay != null) {
                setAllDayVisible(v, View.VISIBLE)
                v.setTextViewText(R.id.db_allday_title, firstAllDay.title)
                val detail = allDayDetail(s.allDayToday, s)
                v.setTextViewText(R.id.db_allday_detail, detail)
                v.setViewVisibility(R.id.db_allday_detail, if (detail.isEmpty()) View.GONE else View.VISIBLE)
                v.setOnClickPendingIntent(R.id.db_allday_row, WidgetUpdater.eventIntent(context, firstAllDay, base + 5))
            } else {
                setAllDayVisible(v, View.GONE)
            }

            // Fußzeile links: laufender Termin
            val current = s.current
            val curLabel: String
            val curTitle: String
            if (current != null) {
                curLabel = "BIS ${WidgetUpdater.clock(current.end)}"
                curTitle = current.title
                v.setOnClickPendingIntent(R.id.db_footer_left, WidgetUpdater.eventIntent(context, current, base + 1))
            } else {
                curLabel = "JETZT"
                curTitle = "Frei"
            }

            // Fußzeile rechts: nächster Termin; anchor = Beginn, falls er in der Zeitleiste liegt
            val nextLabel: String
            val nextTitle: String
            var anchor: Long? = null
            when {
                s.allDayIsNext -> {
                    val first = s.nextAllDay.first()
                    nextLabel = WidgetUpdater.dayLabel(first.firstDay(s.zone), s.today)
                    nextTitle = first.title
                    v.setOnClickPendingIntent(R.id.db_footer_right, WidgetUpdater.eventIntent(context, first, base + 2))
                }
                s.nextTimed != null -> {
                    nextLabel = nextStartLabel(s.nextTimed, s)
                    nextTitle = s.nextTimed.title
                    if (s.nextTimed.begin < s.now + AHEAD_MS) anchor = s.nextTimed.begin
                    v.setOnClickPendingIntent(R.id.db_footer_right, WidgetUpdater.eventIntent(context, s.nextTimed, base + 2))
                }
                else -> {
                    nextLabel = "AB"
                    nextTitle = "Nichts mehr heute"
                }
            }
            v.setTextViewText(R.id.db_current_label, curLabel)
            v.setTextViewText(R.id.db_current_title, curTitle)
            v.setTextViewText(R.id.db_next_label, nextLabel)
            v.setTextViewText(R.id.db_next_title, nextTitle)
            layoutFooter(v, context, options, s, curLabel, curTitle, nextLabel, nextTitle, anchor)
        }

        val vC = RemoteViews(context.packageName, R.layout.widget_day_bar_compact)
        applyAll(vC)

        val vF = RemoteViews(context.packageName, R.layout.widget_day_bar)
        applyAll(vF)

        return RemoteViews(mapOf(
            SizeF(100f, 50f) to vC,
            SizeF(100f, 180f) to vF,
        ))
    }

    /**
     * Positioniert die Fußzeile: links der laufende Termin (so breit wie sein Text, höchstens die
     * halbe Breite), rechts der nächste Termin unter dem Beginn seines Balkens – mindestens
     * FOOTER_GAP_DP hinter dem linken Text und nie über den rechten Rand hinaus. Liegt der
     * nächste Termin nicht in der Zeitleiste, steht er rechtsbündig. Zu lange Namen werden gekürzt.
     */
    private fun layoutFooter(
        v: RemoteViews, context: Context, options: Bundle, s: Snapshot,
        curLabel: String, curTitle: String, nextLabel: String, nextTitle: String, anchor: Long?,
    ) {
        val footerW = (widgetWidthDp(options) - 2 * CONTENT_PADDING_DP).toFloat()
        val maxBlock = footerW / 2f - FOOTER_GAP_DP / 2f
        val curW = blockWidthDp(context, curLabel, curTitle).coerceAtMost(maxBlock)
        val nextW = blockWidthDp(context, nextLabel, nextTitle).coerceAtMost(maxBlock)

        val windowStart = s.now - LEAD_MS
        val want = if (anchor != null) (anchor - windowStart).toFloat() / (LEAD_MS + AHEAD_MS) * footerW
                   else footerW - nextW
        val pos = want.coerceAtMost(footerW - nextW).coerceAtLeast(curW + FOOTER_GAP_DP)

        val dip = TypedValue.COMPLEX_UNIT_DIP
        v.setViewLayoutWidth(R.id.db_footer_left, curW, dip)
        v.setViewLayoutWidth(R.id.db_footer_right, nextW, dip)
        v.setViewLayoutMargin(R.id.db_footer_right, RemoteViews.MARGIN_START, pos - curW, dip)
    }

    /** Breite eines Fußzeilen-Blocks (Label 10sp Monospace, Titel 13sp) in dp, mit etwas Reserve. */
    private fun blockWidthDp(context: Context, label: String, title: String): Float {
        val dm = context.resources.displayMetrics
        val paint = Paint().apply { isAntiAlias = true }
        paint.typeface = Typeface.MONOSPACE
        paint.letterSpacing = 0.06f
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, dm)
        val labelW = paint.measureText(label)
        paint.typeface = Typeface.DEFAULT
        paint.letterSpacing = 0f
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, dm)
        val titleW = paint.measureText(title)
        return maxOf(labelW, titleW) / dm.density + TEXT_RESERVE_DP
    }

    /** Widget-Breite in dp, wie sie auch für die Zeitleiste verwendet wird. */
    private fun widgetWidthDp(options: Bundle): Int =
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            .coerceAtLeast(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250))
            .coerceAtLeast(200)

    private fun drawTimeline(context: Context, s: Snapshot, options: Bundle, p: WidgetUpdater.Palette): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthPx = (widgetWidthDp(options) * density).toInt()
        return drawTimelineBitmap(context, s, widthPx, density, p)
    }

    private fun emptyTimelineBitmap(context: Context, options: Bundle, p: WidgetUpdater.Palette): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250).coerceAtLeast(200)
        val widthPx = (widthDp * density).toInt()
        val heightPx = (TIMELINE_HEIGHT_DP * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val linePaint = Paint().apply { color = context.getColor(p.line); strokeWidth = density; isAntiAlias = true }
        canvas.drawLine(0f, 9 * density, widthPx.toFloat(), 9 * density, linePaint)
        return bmp
    }

    /** Kapsel und der Zwischenraum darüber werden immer gemeinsam ein-/ausgeblendet. */
    private fun setAllDayVisible(v: RemoteViews, visibility: Int) {
        v.setViewVisibility(R.id.db_allday_row, visibility)
        v.setViewVisibility(R.id.db_allday_gap, visibility)
    }

    /** Zusatz in der Kapsel: „ · bis So.“ (mehrtägig, < 7 Tage), „ · bis 02.11.“, „ · +1“ (weitere). */
    private fun allDayDetail(today: List<CalEvent>, s: Snapshot): String {
        val last = today.first().lastDay(s.zone)
        return buildString {
            if (last.isAfter(s.today)) {
                append(" · bis ")
                append(
                    if (ChronoUnit.DAYS.between(s.today, last) < 7) WidgetUpdater.WEEKDAY.format(last)
                    else WidgetUpdater.DAY_MONTH.format(last)
                )
            }
            if (today.size > 1) append(" · +${today.size - 1}")
        }
    }

    /** „AB 18:00“ heute, „MORGEN AB 18:00“, innerhalb einer Woche „MO. AB 18:00“, sonst mit Datum. */
    private fun nextStartLabel(event: CalEvent, s: Snapshot): String {
        val day = event.firstDay(s.zone)
        val time = "AB ${WidgetUpdater.clock(event.begin)}"
        val prefix = when {
            day == s.today -> return time
            day == s.today.plusDays(1) -> "MORGEN"
            ChronoUnit.DAYS.between(s.today, day) < 7 -> WidgetUpdater.WEEKDAY.format(day)
            else -> WidgetUpdater.WEEKDAY_DATE.format(day)
        }
        return "${prefix.uppercase(Locale.GERMAN)} $time"
    }

    private fun drawTimelineBitmap(
        context: Context, s: Snapshot, widthPx: Int, density: Float, p: WidgetUpdater.Palette,
    ): Bitmap {
        val zone = s.zone

        // Mitlaufendes Fenster: 1 Stunde vor jetzt bis 12 Stunden nach jetzt (auch über Mitternacht)
        val windowStart = s.now - LEAD_MS
        val windowEnd = s.now + AHEAD_MS
        val visible = s.allEvents.filter { !it.allDay && it.end > windowStart && it.begin < windowEnd }

        val heightPx = (TIMELINE_HEIGHT_DP * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val textColor = context.getColor(p.text)
        val muteColor = context.getColor(p.muted)
        val accentColor = context.getColor(p.accent)
        val currentColor = context.getColor(R.color.nothing_yellow)
        val lineColor = context.getColor(p.line)

        val lineY = 9 * density
        val barH = 6 * density
        val labelY = 29 * density
        val totalMs = (windowEnd - windowStart).toFloat()

        // Basislinie
        val linePaint = Paint().apply { color = lineColor; strokeWidth = density; isAntiAlias = true }
        canvas.drawLine(0f, lineY, widthPx.toFloat(), lineY, linePaint)

        fun timeToX(ms: Long): Float =
            ((ms - windowStart) / totalMs * widthPx).coerceIn(0f, widthPx.toFloat())

        // Terminbalken
        val barPaint = Paint().apply { isAntiAlias = true }
        visible.forEach { event ->
            val x1 = timeToX(event.begin)
            val x2 = timeToX(event.end).coerceAtLeast(x1 + 3 * density)
            barPaint.color = when {
                event.end <= s.now -> muteColor
                event.begin <= s.now && s.now < event.end -> currentColor
                else -> textColor
            }
            canvas.drawRoundRect(x1, lineY - barH / 2f, x2, lineY + barH / 2f, 2 * density, 2 * density, barPaint)
        }

        // „Jetzt"-Markierung: 14dp hoch (±7dp von der Basislinie)
        val nowX = timeToX(s.now)
        val nowPaint = Paint().apply { color = accentColor; strokeWidth = 2 * density; isAntiAlias = true }
        canvas.drawLine(nowX, lineY - 7 * density, nowX, lineY + 7 * density, nowPaint)

        // Stundenlabels an vollen Stunden (alle 3 Stunden), zentriert, am Rand nach innen geschoben
        val labelPaint = Paint().apply {
            color = muteColor
            textSize = 10 * density
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val halfLabel = labelPaint.measureText("00") / 2f
        var hour = Instant.ofEpochMilli(windowStart).atZone(zone).truncatedTo(ChronoUnit.HOURS)
        while (hour.toInstant().toEpochMilli() <= windowEnd) {
            val ms = hour.toInstant().toEpochMilli()
            if (ms >= windowStart && hour.hour % LABEL_STEP_HOURS == 0) {
                val x = timeToX(ms).coerceIn(halfLabel, widthPx - halfLabel)
                canvas.drawText("%02d".format(hour.hour), x, labelY, labelPaint)
            }
            hour = hour.plusHours(1)
        }

        return bmp
    }
}
