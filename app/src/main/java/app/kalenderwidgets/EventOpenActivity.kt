package app.kalenderwidgets

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle

/**
 * Unsichtbarer Zwischenschritt für Klicks in der Terminliste von Widget C.
 *
 * Einträge einer Widget-Liste können nur über eine gemeinsame, veränderbare PendingIntent-Vorlage
 * reagieren, und veränderbare PendingIntents müssen ab Android 14 ein festes Ziel haben. Diese
 * Activity ist dieses Ziel: Sie bekommt den Termin über die Extras und öffnet ihn im Kalender.
 */
class EventOpenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.hasExtra(EXTRA_EVENT_ID)) {
            val view = WidgetUpdater.eventViewIntent(
                eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0),
                begin = intent.getLongExtra(EXTRA_BEGIN, 0),
                end = intent.getLongExtra(EXTRA_END, 0),
                allDay = intent.getBooleanExtra(EXTRA_ALL_DAY, false),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(view)
            } catch (e: ActivityNotFoundException) {
                // keine Kalender-App installiert: nichts tun
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_BEGIN = "begin"
        const val EXTRA_END = "end"
        const val EXTRA_ALL_DAY = "all_day"
    }
}
