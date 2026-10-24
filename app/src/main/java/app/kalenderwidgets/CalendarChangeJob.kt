package app.kalenderwidgets

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract

class CalendarChangeJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        WidgetUpdater.updateAll(applicationContext)
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

        /** Bricht den Job nur ab, wenn kein Widget (A, B, C oder D) mehr vorhanden ist. */
        fun cancelIfNoWidgets(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val hasA = mgr.getAppWidgetIds(ComponentName(context, NowNextWidget::class.java)).isNotEmpty()
            val hasB = mgr.getAppWidgetIds(ComponentName(context, DayBarWidget::class.java)).isNotEmpty()
            val hasC = mgr.getAppWidgetIds(ComponentName(context, MonthWidget::class.java)).isNotEmpty()
            val hasD = mgr.getAppWidgetIds(ComponentName(context, SentenceWidget::class.java)).isNotEmpty()
            if (!hasA && !hasB && !hasC && !hasD) cancel(context)
        }
    }
}
