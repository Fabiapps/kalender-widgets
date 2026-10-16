package app.kalenderwidgets

import android.content.Context

/** Die beiden Schalter aus der App: Wallpaper-Farben und Transparenz. */
object WidgetPrefs {
    private const val FILE = "widget_settings"
    private const val KEY_ADAPTIVE = "adaptive_colors"
    private const val KEY_GLASS = "glass"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun adaptiveColors(context: Context): Boolean = prefs(context).getBoolean(KEY_ADAPTIVE, true)

    fun glass(context: Context): Boolean = prefs(context).getBoolean(KEY_GLASS, true)

    fun setAdaptiveColors(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADAPTIVE, value).apply()
    }

    fun setGlass(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLASS, value).apply()
    }
}
