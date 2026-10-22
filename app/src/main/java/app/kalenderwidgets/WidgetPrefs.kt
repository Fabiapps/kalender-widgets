package app.kalenderwidgets

import android.content.Context

enum class Appearance { DARK, LIGHT, AUTO }

/** Globale Standardwerte und pro Widget-Instanz gespeicherte Einstellungen. */
object WidgetPrefs {
    private const val FILE = "widget_settings"

    // Globale Defaults (bisherige Schlüssel bleiben für Rückwärtskompatibilität)
    private const val KEY_DEFAULT_APPEARANCE = "default_appearance"
    private const val KEY_ADAPTIVE = "adaptive_colors"
    private const val KEY_GLASS = "glass"

    // Pro-Widget-Schlüssel (Suffix: appWidgetId)
    private const val KEY_APPEARANCE = "appearance_"
    private const val KEY_WIDGET_ADAPTIVE = "widget_adaptive_"
    private const val KEY_WIDGET_GLASS = "widget_glass_"
    private const val KEY_WIDGET_CONFIGURED = "widget_configured_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------- Globale Defaults ----------

    fun defaultAppearance(context: Context): Appearance =
        Appearance.valueOf(prefs(context).getString(KEY_DEFAULT_APPEARANCE, Appearance.DARK.name)!!)

    fun adaptiveColors(context: Context): Boolean = prefs(context).getBoolean(KEY_ADAPTIVE, true)

    fun glass(context: Context): Boolean = prefs(context).getBoolean(KEY_GLASS, true)

    fun setDefaultAppearance(context: Context, v: Appearance) {
        prefs(context).edit().putString(KEY_DEFAULT_APPEARANCE, v.name).apply()
    }

    /** Gilt als Standard für neue Widgets und wird auf alle vorhandenen Widgets übertragen. */
    fun setAdaptiveColors(context: Context, value: Boolean) {
        setGlobalAndAllWidgets(context, KEY_ADAPTIVE, KEY_WIDGET_ADAPTIVE, value)
    }

    /** Gilt als Standard für neue Widgets und wird auf alle vorhandenen Widgets übertragen. */
    fun setGlass(context: Context, value: Boolean) {
        setGlobalAndAllWidgets(context, KEY_GLASS, KEY_WIDGET_GLASS, value)
    }

    private fun setGlobalAndAllWidgets(context: Context, globalKey: String, widgetPrefix: String, value: Boolean) {
        val p = prefs(context)
        val edit = p.edit().putBoolean(globalKey, value)
        p.all.keys.filter { it.startsWith(widgetPrefix) }.forEach { edit.putBoolean(it, value) }
        edit.apply()
    }

    // ---------- Pro-Widget ----------

    fun isConfigured(context: Context, id: Int): Boolean =
        prefs(context).getBoolean(KEY_WIDGET_CONFIGURED + id, false)

    fun appearance(context: Context, id: Int): Appearance =
        if (isConfigured(context, id))
            Appearance.valueOf(prefs(context).getString(KEY_APPEARANCE + id, Appearance.DARK.name)!!)
        else
            defaultAppearance(context)

    fun widgetAdaptive(context: Context, id: Int): Boolean =
        if (isConfigured(context, id))
            prefs(context).getBoolean(KEY_WIDGET_ADAPTIVE + id, true)
        else
            adaptiveColors(context)

    fun widgetGlass(context: Context, id: Int): Boolean =
        if (isConfigured(context, id))
            prefs(context).getBoolean(KEY_WIDGET_GLASS + id, true)
        else
            glass(context)

    fun saveWidgetConfig(context: Context, id: Int, appearance: Appearance, adaptive: Boolean, glass: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_WIDGET_CONFIGURED + id, true)
            .putString(KEY_APPEARANCE + id, appearance.name)
            .putBoolean(KEY_WIDGET_ADAPTIVE + id, adaptive)
            .putBoolean(KEY_WIDGET_GLASS + id, glass)
            .apply()
    }

    fun removeWidgetConfig(context: Context, id: Int) {
        prefs(context).edit()
            .remove(KEY_WIDGET_CONFIGURED + id)
            .remove(KEY_APPEARANCE + id)
            .remove(KEY_WIDGET_ADAPTIVE + id)
            .remove(KEY_WIDGET_GLASS + id)
            .apply()
    }
}
