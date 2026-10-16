package app.kalenderwidgets

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Einrichtung: Kalenderzugriff erlauben und globale Standardwerte. */
class MainActivity : Activity() {

    private lateinit var permissionStatus: TextView
    private lateinit var permissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionStatus = findViewById(R.id.permission_status)
        permissionButton = findViewById(R.id.btn_permission)
        permissionButton.setOnClickListener { requestCalendarPermission() }

        val rgAppearance = findViewById<RadioGroup>(R.id.rg_default_appearance)
        rgAppearance.check(when (WidgetPrefs.defaultAppearance(this)) {
            Appearance.DARK -> R.id.rb_default_dark
            Appearance.LIGHT -> R.id.rb_default_light
            Appearance.AUTO -> R.id.rb_default_auto
        })
        rgAppearance.setOnCheckedChangeListener { _, id ->
            val a = when (id) {
                R.id.rb_default_light -> Appearance.LIGHT
                R.id.rb_default_auto -> Appearance.AUTO
                else -> Appearance.DARK
            }
            WidgetPrefs.setDefaultAppearance(this, a)
            WidgetUpdater.updateAll(this)
        }

        val adaptive = findViewById<Switch>(R.id.switch_adaptive)
        adaptive.isChecked = WidgetPrefs.adaptiveColors(this)
        adaptive.setOnCheckedChangeListener { _, checked ->
            WidgetPrefs.setAdaptiveColors(this, checked)
            WidgetUpdater.updateAll(this)
        }

        val glass = findViewById<Switch>(R.id.switch_glass)
        glass.isChecked = WidgetPrefs.glass(this)
        glass.setOnCheckedChangeListener { _, checked ->
            WidgetPrefs.setGlass(this, checked)
            WidgetUpdater.updateAll(this)
        }

        findViewById<Button>(R.id.btn_refresh).setOnClickListener {
            WidgetUpdater.updateAll(this)
            Toast.makeText(this, R.string.refreshed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
        WidgetUpdater.updateAll(this)
    }

    private fun updatePermissionUi() {
        if (CalendarRepository.hasPermission(this)) {
            permissionStatus.setText(R.string.permission_granted)
            permissionButton.visibility = View.GONE
        } else {
            permissionStatus.setText(R.string.permission_missing)
            permissionButton.visibility = View.VISIBLE
        }
    }

    private fun requestCalendarPermission() {
        val state = getSharedPreferences("app_state", MODE_PRIVATE)
        val askedBefore = state.getBoolean("asked_calendar", false)
        if (askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        } else {
            state.edit().putBoolean("asked_calendar", true).apply()
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_CALENDAR)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CALENDAR) return
        updatePermissionUi()
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            CalendarChangeJob.schedule(this)
            WidgetUpdater.updateAll(this)
        }
    }

    companion object {
        private const val REQUEST_CALENDAR = 1
    }
}
