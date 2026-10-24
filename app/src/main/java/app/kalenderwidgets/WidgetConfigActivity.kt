package app.kalenderwidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch

/**
 * Wird beim Hinzufügen eines Widgets aufgerufen (android:configure)
 * und über „Widget bearbeiten" (widgetFeatures reconfigurable).
 */
class WidgetConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_widget_config)

        val radioGroup = findViewById<RadioGroup>(R.id.rg_appearance)
        val switchAdaptive = findViewById<Switch>(R.id.cfg_switch_adaptive)
        val rgBackdrop = findViewById<RadioGroup>(R.id.rg_backdrop)
        val btnSave = findViewById<Button>(R.id.btn_save_config)

        radioGroup.check(when (WidgetPrefs.appearance(this, appWidgetId)) {
            Appearance.DARK -> R.id.rb_dark
            Appearance.LIGHT -> R.id.rb_light
            Appearance.AUTO -> R.id.rb_auto
        })
        switchAdaptive.isChecked = WidgetPrefs.widgetAdaptive(this, appWidgetId)
        rgBackdrop.check(when (WidgetPrefs.widgetBackdrop(this, appWidgetId)) {
            Backdrop.SOLID -> R.id.rb_backdrop_solid
            Backdrop.GLASS -> R.id.rb_backdrop_glass
            Backdrop.CLEAR -> R.id.rb_backdrop_clear
        })

        btnSave.setOnClickListener {
            val chosen = when (radioGroup.checkedRadioButtonId) {
                R.id.rb_light -> Appearance.LIGHT
                R.id.rb_auto -> Appearance.AUTO
                else -> Appearance.DARK
            }
            val backdrop = when (rgBackdrop.checkedRadioButtonId) {
                R.id.rb_backdrop_solid -> Backdrop.SOLID
                R.id.rb_backdrop_clear -> Backdrop.CLEAR
                else -> Backdrop.GLASS
            }
            WidgetPrefs.saveWidgetConfig(this, appWidgetId, chosen, switchAdaptive.isChecked, backdrop)
            WidgetUpdater.updateAll(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
