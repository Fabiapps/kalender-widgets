package app.kalenderwidgets

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract

/**
 * Läuft, sobald sich im Kalender etwas ändert (neuer, verschobener oder gelöschter Termin),
 * damit das Widget nicht bis zum nächsten geplanten Update veraltet ist.
 */
class CalendarChangeJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        WidgetUpdater.updateAll(applicationContext)
        // Content-Trigger feuern nur einmal, deshalb direkt wieder anmelden.
        schedule(applicationContext)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 4711

        fun schedule(context: Context) {
            if (!CalendarRepository.hasPermission(context)) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CalendarChangeJob::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        CalendarContract.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                    )
                )
                .setTriggerContentUpdateDelay(2_000)
                .setTriggerContentMaxDelay(10_000)
                .build()
            context.getSystemService(JobScheduler::class.java)?.schedule(job)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
