package app.kalenderwidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.time.Instant

/** Baut Widget B (Tagesleiste) zusammen. */
object DayBarUpdater {

    internal fun buildViews(context: Context, widgetId: Int, s: Snapshot, options: Bundle): RemoteViews {
        val glass = WidgetPrefs.widgetGlass(context, widgetId)
        val p = WidgetUpdater.palette(context, widgetId)
        val base = widgetId * 100 + 50

        fun makeBase(layoutId: Int): RemoteViews {
            val v = RemoteViews(context.packageName, layoutId)
            v.setColorStateList(R.id.db_bg_fill, "setImageTintList", p.background)
            v.setViewVisibility(R.id.db_bg_stroke, if (glass) View.VISIBLE else View.GONE)
            for (id in intArrayOf(R.id.db_clock, R.id.db_allday_title)) {
                v.setColorStateList(id, "setTextColor", p.text)
            }
            for (id in intArrayOf(R.id.db_weekday, R.id.db_date, R.id.db_allday_count)) {
                v.setColorStateList(id, "setTextColor", p.muted)
            }
            v.setColorStateList(R.id.db_allday_ring, "setImageTintList", p.muted)
            v.setOnClickPendingIntent(android.R.id.background, WidgetUpdater.calendarIntent(context, s.now, base + 3))
            return v
        }

        if (!s.hasPermission) {
            val v = makeBase(R.layout.widget_day_bar)
            for (id in intArrayOf(R.id.db_current_title, R.id.db_next_title)) {
                v.setColorStateList(id, "setTextColor", p.text)
            }
            for (id in intArrayOf(R.id.db_current_label, R.id.db_next_label)) {
                v.setColorStateList(id, "setTextColor", p.muted)
            }
            v.setViewVisibility(R.id.db_allday_row, View.GONE)
            v.setTextViewText(R.id.db_current_label, "KALENDER")
            v.setTextViewText(R.id.db_current_title, "Zugriff fehlt")
            v.setTextViewText(R.id.db_next_label, "")
            v.setTextViewText(R.id.db_next_title, "Tippen zum Einrichten")
            v.setOnClickPendingIntent(android.R.id.background, WidgetUpdater.settingsIntent(context, base + 4))
            v.setImageViewBitmap(R.id.db_timeline, emptyTimelineBitmap(context, options, p))
            return v
        }

        val timeline = drawTimeline(context, s, options, p)

        fun applyCommon(v: RemoteViews) {
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
            v.setImageViewBitmap(R.id.db_timeline, timeline)
        }

        val vC = makeBase(R.layout.widget_day_bar_compact)
        applyCommon(vC)

        val vF = makeBase(R.layout.widget_day_bar)
        applyCommon(vF)
        for (id in intArrayOf(R.id.db_current_title, R.id.db_next_title)) {
            vF.setColorStateList(id, "setTextColor", p.text)
        }
        for (id in intArrayOf(R.id.db_current_label, R.id.db_next_label)) {
            vF.setColorStateList(id, "setTextColor", p.muted)
        }

        val current = s.current
        if (current != null) {
            vF.setTextViewText(R.id.db_current_label, "BIS ${WidgetUpdater.clock(current.end)}")
            vF.setTextViewText(R.id.db_current_title, current.title)
            vF.setOnClickPendingIntent(R.id.db_footer_left, WidgetUpdater.eventIntent(context, current, base + 1))
        } else {
            vF.setTextViewText(R.id.db_current_label, "JETZT")
            val freeUntil = if (s.nextTimed != null && s.nextTimed.firstDay(s.zone) == s.today)
                "bis ${WidgetUpdater.clock(s.nextTimed.begin)}" else "für heute"
            vF.setTextViewText(R.id.db_current_title, "Frei · $freeUntil")
        }

        when {
            s.allDayIsNext -> {
                val first = s.nextAllDay.first()
                vF.setTextViewText(R.id.db_next_label, WidgetUpdater.dayLabel(first.firstDay(s.zone), s.today))
                vF.setTextViewText(R.id.db_next_title, first.title)
                vF.setOnClickPendingIntent(R.id.db_footer_right, WidgetUpdater.eventIntent(context, first, base + 2))
            }
            s.nextTimed != null -> {
                vF.setTextViewText(R.id.db_next_label, "AB ${WidgetUpdater.clock(s.nextTimed.begin)}")
                vF.setTextViewText(R.id.db_next_title, s.nextTimed.title)
                vF.setOnClickPendingIntent(R.id.db_footer_right, WidgetUpdater.eventIntent(context, s.nextTimed, base + 2))
            }
            else -> {
                vF.setTextViewText(R.id.db_next_label, "AB")
                vF.setTextViewText(R.id.db_next_title, "Nichts mehr heute")
            }
        }

        return RemoteViews(mapOf(
            SizeF(100f, 50f) to vC,
            SizeF(100f, 140f) to vF,
        ))
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
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250).coerceAtLeast(200)
        val widthPx = (widthDp * density).toInt()
        val heightPx = (56 * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val linePaint = Paint().apply { color = context.getColor(p.line); strokeWidth = density; isAntiAlias = true }
        canvas.drawLine(0f, 20 * density, widthPx.toFloat(), 20 * density, linePaint)
        return bmp
    }

    private fun drawTimelineBitmap(
        context: Context, s: Snapshot, widthPx: Int, density: Float, p: WidgetUpdater.Palette,
    ): Bitmap {
        val zone = s.zone
        val timedToday = s.allEvents.filter { !it.allDay && it.firstDay(zone) == s.today }

        // Zeitraum: Standard 08–20, immer aktuelle Uhrzeit enthalten, auf heutige Termine erweitern
        val nowZdt = Instant.ofEpochMilli(s.now).atZone(zone)
        var startHour = minOf(8, nowZdt.hour)
        var endHour = maxOf(20, nowZdt.hour + 1)
        timedToday.forEach { event ->
            val bZdt = Instant.ofEpochMilli(event.begin).atZone(zone)
            val eZdt = Instant.ofEpochMilli(event.end).atZone(zone)
            if (bZdt.toLocalDate() == s.today) startHour = minOf(startHour, bZdt.hour)
            if (eZdt.toLocalDate() == s.today) endHour = maxOf(endHour, eZdt.hour + (if (eZdt.minute > 0) 1 else 0))
        }
        startHour = maxOf(startHour, 0)
        endHour = minOf(endHour, 24)
        endHour = maxOf(endHour, startHour + 4)

        val heightPx = (56 * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val textColor = context.getColor(p.text)
        val muteColor = context.getColor(p.muted)
        val accentColor = context.getColor(p.accent)
        val lineColor = context.getColor(p.line)

        val lineY = 22 * density
        val barH = 6 * density
        val labelY = 48 * density
        val totalMin = (endHour - startHour) * 60f

        // Basislinie
        val linePaint = Paint().apply { color = lineColor; strokeWidth = density; isAntiAlias = true }
        canvas.drawLine(0f, lineY, widthPx.toFloat(), lineY, linePaint)

        fun timeToX(ms: Long): Float {
            val zdt = Instant.ofEpochMilli(ms).atZone(zone)
            val min = (zdt.hour - startHour) * 60f + zdt.minute + zdt.second / 60f
            return (min / totalMin * widthPx).coerceIn(0f, widthPx.toFloat())
        }

        // Terminbalken
        val barPaint = Paint().apply { isAntiAlias = true }
        timedToday.forEach { event ->
            val x1 = timeToX(event.begin)
            val x2 = timeToX(event.end).coerceAtLeast(x1 + 3 * density)
            barPaint.color = when {
                event.end <= s.now -> muteColor
                event.begin <= s.now && s.now < event.end -> accentColor
                else -> textColor
            }
            canvas.drawRoundRect(x1, lineY - barH / 2f, x2, lineY + barH / 2f, 2 * density, 2 * density, barPaint)
        }

        // „Jetzt"-Markierung: 14dp hoch (±7dp von der Basislinie)
        val nowX = timeToX(s.now)
        val nowPaint = Paint().apply { color = accentColor; strokeWidth = 2 * density; isAntiAlias = true }
        canvas.drawLine(nowX, lineY - 7 * density, nowX, lineY + 7 * density, nowPaint)

        // 5 Stundenlabels: erste linksbündig, letzte rechtsbündig, mittlere zentriert
        val labelPaint = Paint().apply {
            color = muteColor
            textSize = 10 * density
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        val span = endHour - startHour
        for (i in 0..4) {
            val hour = startHour + i * span / 4
            val x = i.toFloat() * widthPx / 4f
            labelPaint.textAlign = when (i) {
                0 -> Paint.Align.LEFT
                4 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText("%02d".format(hour), x, labelY, labelPaint)
        }

        return bmp
    }
}
