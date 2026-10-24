package app.kalenderwidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Widget A: Uhr + aktueller Termin + nächster Termin. */
class NowNextWidget : AppWidgetProvider() {

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
        val mgr = AppWidgetManager.getInstance(context)
        if (mgr.getAppWidgetIds(ComponentName(context, DayBarWidget::class.java)).isEmpty() &&
            mgr.getAppWidgetIds(ComponentName(context, MonthWidget::class.java)).isEmpty() &&
            mgr.getAppWidgetIds(ComponentName(context, SentenceWidget::class.java)).isEmpty()
        ) {
            WidgetUpdater.cancelRefresh(context)
        }
        CalendarChangeJob.cancelIfNoWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
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
