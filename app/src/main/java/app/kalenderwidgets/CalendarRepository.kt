package app.kalenderwidgets

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
) {
    /**
     * Erster Tag des Termins. Ganztägige Termine speichert Android als UTC-Mitternacht,
     * deshalb werden sie in UTC gelesen, sonst würde z. B. aus dem 14.10. der 13.10.
     */
    fun firstDay(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()

    /** Letzter Tag (einschließlich). Bei ganztägigen Terminen ist das Ende exklusiv gespeichert. */
    fun lastDay(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli((end - 1).coerceAtLeast(begin))
            .atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
}

/** Liest Termine aus der Android-Kalenderdatenbank (alle synchronisierten, sichtbaren Kalender). */
object CalendarRepository {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * Alle Termine, die den Zeitraum [from]–[to] berühren, aufsteigend nach Beginn.
     * Ausgeblendet werden abgesagte Termine und Einladungen, die du abgelehnt hast.
     * Wiederkehrende Termine sind über die Instances-Tabelle bereits aufgelöst.
     */
    fun loadEvents(context: Context, from: Long, to: Long): List<CalEvent> {
        if (!hasPermission(context)) return emptyList()

        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()

        val projection = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
        )

        val selection = buildString {
            append("${Instances.VISIBLE} = 1")
            append(" AND ${Instances.SELF_ATTENDEE_STATUS} != ${Attendees.ATTENDEE_STATUS_DECLINED}")
            append(" AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Events.STATUS_CANCELED})")
        }

        val result = mutableListOf<CalEvent>()
        try {
            context.contentResolver.query(uri, projection, selection, null, "${Instances.BEGIN} ASC")
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "(Ohne Titel)"
                        result += CalEvent(
                            id = cursor.getLong(0),
                            title = title,
                            begin = cursor.getLong(2),
                            end = cursor.getLong(3),
                            allDay = cursor.getInt(4) == 1,
                        )
                    }
                }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return result
    }
}
