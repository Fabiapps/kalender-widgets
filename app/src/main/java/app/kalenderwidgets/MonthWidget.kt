package app.kalenderwidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

/** Widget C: Monatsansicht. Tippen auf einen Tag zeigt dessen Termine im Widget. */
class MonthWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.updateAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) WidgetPrefs.removeWidgetConfig(context, id)
    }

    override fun onEnabled(context: Context) {
        CalendarChangeJob.schedule(context)
    }

    override fun onDisabled(context: Context) {
        CalendarChangeJob.cancelIfNoWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            MonthUpdater.ACTION_SELECT_DAY, MonthUpdater.ACTION_TODAY -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val today = LocalDate.now(ZoneId.systemDefault())
                val day = if (intent.action == MonthUpdater.ACTION_SELECT_DAY && intent.hasExtra(MonthUpdater.EXTRA_EPOCH_DAY))
                    LocalDate.ofEpochDay(intent.getLongExtra(MonthUpdater.EXTRA_EPOCH_DAY, today.toEpochDay()))
                else today
                WidgetPrefs.setMonthSelectedDay(context, id, day, today)
                AppWidgetManager.getInstance(context)
                    .updateAppWidget(id, MonthUpdater.buildViews(context, id, System.currentTimeMillis()))
            }
            WidgetUpdater.ACTION_REFRESH -> WidgetUpdater.updateAll(context)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WidgetUpdater.updateAll(context)
                CalendarChangeJob.schedule(context)
            }
        }
    }
}
