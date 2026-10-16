package app.kalenderwidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId

/** Baut Widget B (Tagesleiste) zusammen. */
object DayBarUpdater {

    internal fun buildViews(context: Context, widgetId: Int, s: Snapshot, options: Bundle): RemoteViews {
        val glass = WidgetPrefs.widgetGlass(context, widgetId)
        val p = WidgetUpdater.palette(context, widgetId)
        val base = widgetId * 100 + 50
        val v = RemoteViews(context.packageName, R.layout.widget_day_bar)

        v.setColorStateList(R.id.db_bg_fill, "setImageTintList", p.background)
        v.setViewVisibility(R.id.db_bg_stroke, if (glass) View.VISIBLE else View.GONE)

        for (id in intArrayOf(R.id.db_clock, R.id.db_allday_title, R.id.db_current_title, R.id.db_next_title)) {
            v.setColorStateList(id, "setTextColor", p.text)
        }
        for (id in intArrayOf(
            R.id.db_weekday, R.id.db_date, R.id.db_allday_count,
            R.id.db_current_label, R.id.db_next_label,
        )) {
            v.setColorStateList(id, "setTextColor", p.muted)
        }
        v.setColorStateList(R.id.db_allday_ring, "setImageTintList", p.muted)
        v.setColorStateList(R.id.db_footer_divider, "setImageTintList", p.line)

        v.setOnClickPendingIntent(android.R.id.background, WidgetUpdater.calendarIntent(context, s.now, base + 3))

        if (!s.hasPermission) {
            v.setViewVisibility(R.id.db_allday_row, View.GONE)
            v.setColorStateList(R.id.db_current_dot, "setImageTintList", p.dotOff)
            v.setTextViewText(R.id.db_current_label, "KALENDER")
            v.setTextViewText(R.id.db_current_title, "Zugriff fehlt")
            v.setTextViewText(R.id.db_next_label, "")
            v.setTextViewText(R.id.db_next_title, "Tippen zum Einrichten")
            v.setOnClickPendingIntent(android.R.id.background, WidgetUpdater.settingsIntent(context, base + 4))
            v.setImageViewBitmap(R.id.db_timeline, emptyTimelineBitmap(context, options, p))
            return v
        }

        // Ganztägige Termine heute
        val firstAllDay = s.allDayToday.firstOrNull()
        if (firstAllDay != null) {
            v.setViewVisibility(R.id.db_allday_row, View.VISIBLE)
            v.setTextViewText(R.id.db_allday_title, firstAllDay.title)
            val more = s.allDayToday.size - 1
            if (more > 0) {
                v.setTextViewText(R.id.db_allday_count, "+$more")
                v.setViewVisibility(R.id.db_allday_count, View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.db_allday_count, View.GONE)
            }
            v.setOnClickPendingIntent(R.id.db_allday_row, WidgetUpdater.eventIntent(context, firstAllDay, base + 5))
        } else {
            v.setViewVisibility(R.id.db_allday_row, View.GONE)
        }

        // Timeline zeichnen
        v.setImageViewBitmap(R.id.db_timeline, drawTimeline(context, s, options, p))

        // Fußzeile links: laufender Termin
        val current = s.current
        if (current != null) {
            v.setColorStateList(R.id.db_current_dot, "setImageTintList", p.accent)
            v.setTextViewText(R.id.db_current_label, "BIS ${WidgetUpdater.clock(current.end)}")
            v.setTextViewText(R.id.db_current_title, current.title)
            v.setOnClickPendingIntent(R.id.db_footer_left, WidgetUpdater.eventIntent(context, current, base + 1))
        } else {
            v.setColorStateList(R.id.db_current_dot, "setImageTintList", p.dotOff)
            v.setTextViewText(R.id.db_current_label, "JETZT")
            val freeUntil = if (s.nextTimed != null && s.nextTimed.firstDay(s.zone) == s.today)
                "bis ${WidgetUpdater.clock(s.nextTimed.begin)}" else "für heute"
            v.setTextViewText(R.id.db_current_title, "Frei · $freeUntil")
        }

        // Fußzeile rechts: nächster Termin
        when {
            s.allDayIsNext -> {
                val first = s.nextAllDay.first()
                v.setTextViewText(R.id.db_next_label, WidgetUpdater.dayLabel(first.firstDay(s.zone), s.today))
                v.setTextViewText(R.id.db_next_title, first.title)
                v.setOnClickPendingIntent(R.id.db_footer_right, WidgetUpdater.eventIntent(context, first, base + 2))
            }
            s.nextTimed != null -> {
                v.setTextViewText(R.id.db_next_label, "AB ${WidgetUpdater.clock(s.nextTimed.begin)}")
                v.setTextViewText(R.id.db_next_title, s.nextTimed.title)
                v.setOnClickPendingIntent(R.id.db_footer_right, WidgetUpdater.eventIntent(context, s.nextTimed, base + 2))
            }
            else -> {
                v.setTextViewText(R.id.db_next_label, "AB")
                v.setTextViewText(R.id.db_next_title, "Nichts mehr heute")
            }
        }
        return v
    }

    private fun drawTimeline(context: Context, s: Snapshot, options: Bundle, p: WidgetUpdater.Palette): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            .coerceAtLeast(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250))
            .coerceAtLeast(200)
        val widthPx = (widthDp * density).toInt()
        return drawTimelineBitmap(context, s, widthPx, density, p)
    }

    private fun emptyTimelineBitmap(context: Context, options: Bundle, p: WidgetUpdater.Palette): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250)
            .coerceAtLeast(200)
        val widthPx = (widthDp * density).toInt()
        val heightPx = (56 * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val lineColor = context.getColor(p.line)
        val linePaint = Paint().apply { color = lineColor; strokeWidth = density; isAntiAlias = true }
        val lineY = 20 * density
        canvas.drawLine(0f, lineY, widthPx.toFloat(), lineY, linePaint)
        return bmp
    }

    private fun drawTimelineBitmap(
        context: Context, s: Snapshot, widthPx: Int, density: Float, p: WidgetUpdater.Palette,
    ): Bitmap {
        val zone = s.zone
        val timedToday = s.allEvents.filter { !it.allDay && it.firstDay(zone) == s.today }

        // Zeitraum bestimmen: Standard 08–20, erweitern auf heutige Termine (volle Stunden)
        var startHour = 8
        var endHour = 20
        timedToday.forEach { event ->
            val bZdt = Instant.ofEpochMilli(event.begin).atZone(zone)
            val eZdt = Instant.ofEpochMilli(event.end).atZone(zone)
            if (bZdt.toLocalDate() == s.today) startHour = minOf(startHour, bZdt.hour)
            if (eZdt.toLocalDate() == s.today) endHour = maxOf(endHour, eZdt.hour + (if (eZdt.minute > 0) 1 else 0))
        }
        endHour = maxOf(endHour, startHour + 4)

        val heightPx = (56 * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val textColor = context.getColor(p.text)
        val muteColor = context.getColor(p.muted)
        val accentColor = context.getColor(p.accent)
        val lineColor = context.getColor(p.line)

        val lineY = 22 * density
        val barH = 9 * density
        val labelY = 48 * density

        // Basislinie
        val linePaint = Paint().apply { color = lineColor; strokeWidth = density; isAntiAlias = true }
        canvas.drawLine(0f, lineY, widthPx.toFloat(), lineY, linePaint)

        // Terminbalken
        val barPaint = Paint().apply { isAntiAlias = true }
        val totalMin = (endHour - startHour) * 60f

        fun timeToX(ms: Long): Float {
            val zdt = Instant.ofEpochMilli(ms).atZone(zone)
            val min = (zdt.hour - startHour) * 60f + zdt.minute + zdt.second / 60f
            return (min / totalMin * widthPx).coerceIn(0f, widthPx.toFloat())
        }

        timedToday.forEach { event ->
            val x1 = timeToX(event.begin)
            val x2 = timeToX(event.end).coerceAtLeast(x1 + 3 * density)
            barPaint.color = when {
                event.end <= s.now -> muteColor
                event.begin <= s.now && s.now < event.end -> accentColor
                else -> textColor
            }
            val top = lineY - barH / 2f
            val bottom = lineY + barH / 2f
            canvas.drawRoundRect(x1, top, x2, bottom, 2 * density, 2 * density, barPaint)
        }

        // „Jetzt"-Markierung
        val nowX = timeToX(s.now)
        val nowPaint = Paint().apply { color = accentColor; strokeWidth = 2 * density; isAntiAlias = true }
        canvas.drawLine(nowX, lineY - 7 * density, nowX, lineY + 7 * density, nowPaint)

        // 5 Stundenlabels
        val labelPaint = Paint().apply {
            color = muteColor
            textSize = 10 * density
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val span = endHour - startHour
        for (i in 0..4) {
            val hour = startHour + i * span / 4
            val x = i * widthPx / 4f
            canvas.drawText("%02d".format(hour), x, labelY, labelPaint)
        }

        return bmp
    }
}
