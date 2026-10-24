package app.kalenderwidgets

import android.content.Context
import java.time.LocalDate

enum class Appearance { DARK, LIGHT, AUTO }

/** Hintergrund der Widgets: deckend (hell bzw. dunkel), Milchglas oder ganz transparent. */
enum class Backdrop { SOLID, GLASS, CLEAR }

/** Globale Standardwerte und pro Widget-Instanz gespeicherte Einstellungen. */
object WidgetPrefs {
    private const val FILE = "widget_settings"

    // Globale Defaults (bisherige Schlüssel bleiben für Rückwärtskompatibilität)
    private const val KEY_DEFAULT_APPEARANCE = "default_appearance"
    private const val KEY_ADAPTIVE = "adaptive_colors"
    private const val KEY_BACKDROP = "backdrop"
    /** Früherer Schalter „Transparenz“ (Milchglas an/aus); wird gelesen, solange KEY_BACKDROP fehlt. */
    private const val KEY_GLASS = "glass"

    // Pro-Widget-Schlüssel (Suffix: appWidgetId)
    private const val KEY_APPEARANCE = "appearance_"
    private const val KEY_WIDGET_ADAPTIVE = "widget_adaptive_"
    private const val KEY_WIDGET_BACKDROP = "widget_backdrop_"
    private const val KEY_WIDGET_GLASS = "widget_glass_"
    private const val KEY_WIDGET_CONFIGURED = "widget_configured_"
    private const val KEY_MONTH_SELECTED = "month_selected_"
    private const val KEY_MONTH_SELECTED_ON = "month_selected_on_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------- Globale Defaults ----------

    fun defaultAppearance(context: Context): Appearance =
        Appearance.valueOf(prefs(context).getString(KEY_DEFAULT_APPEARANCE, Appearance.DARK.name)!!)

    fun adaptiveColors(context: Context): Boolean = prefs(context).getBoolean(KEY_ADAPTIVE, true)

    fun backdrop(context: Context): Backdrop {
        val p = prefs(context)
        return backdropOf(p.getString(KEY_BACKDROP, null))
            ?: if (p.getBoolean(KEY_GLASS, true)) Backdrop.GLASS else Backdrop.SOLID
    }

    fun setDefaultAppearance(context: Context, v: Appearance) {
        prefs(context).edit().putString(KEY_DEFAULT_APPEARANCE, v.name).apply()
    }

    /** Gilt als Standard für neue Widgets und wird auf alle vorhandenen Widgets übertragen. */
    fun setAdaptiveColors(context: Context, value: Boolean) {
        setGlobalAndAllWidgets(context, KEY_ADAPTIVE, KEY_WIDGET_ADAPTIVE, value)
    }

    /** Gilt als Standard für neue Widgets und wird auf alle vorhandenen Widgets übertragen. */
    fun setBackdrop(context: Context, value: Backdrop) {
        val p = prefs(context)
        val edit = p.edit().putString(KEY_BACKDROP, value.name)
        p.all.keys.filter { it.startsWith(KEY_WIDGET_CONFIGURED) }.forEach {
            edit.putString(KEY_WIDGET_BACKDROP + it.removePrefix(KEY_WIDGET_CONFIGURED), value.name)
        }
        edit.apply()
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

    fun widgetBackdrop(context: Context, id: Int): Backdrop {
        if (!isConfigured(context, id)) return backdrop(context)
        val p = prefs(context)
        return backdropOf(p.getString(KEY_WIDGET_BACKDROP + id, null))
            ?: if (p.getBoolean(KEY_WIDGET_GLASS + id, true)) Backdrop.GLASS else Backdrop.SOLID
    }

    fun saveWidgetConfig(context: Context, id: Int, appearance: Appearance, adaptive: Boolean, backdrop: Backdrop) {
        prefs(context).edit()
            .putBoolean(KEY_WIDGET_CONFIGURED + id, true)
            .putString(KEY_APPEARANCE + id, appearance.name)
            .putBoolean(KEY_WIDGET_ADAPTIVE + id, adaptive)
            .putString(KEY_WIDGET_BACKDROP + id, backdrop.name)
            .remove(KEY_WIDGET_GLASS + id)
            .apply()
    }

    fun removeWidgetConfig(context: Context, id: Int) {
        prefs(context).edit()
            .remove(KEY_WIDGET_CONFIGURED + id)
            .remove(KEY_APPEARANCE + id)
            .remove(KEY_WIDGET_ADAPTIVE + id)
            .remove(KEY_WIDGET_BACKDROP + id)
            .remove(KEY_WIDGET_GLASS + id)
            .remove(KEY_MONTH_SELECTED + id)
            .remove(KEY_MONTH_SELECTED_ON + id)
            .apply()
    }

    private fun backdropOf(name: String?): Backdrop? = Backdrop.entries.firstOrNull { it.name == name }

    // ---------- Widget C: gewählter Tag ----------

    /** Im Monats-Widget gewählter Tag; gilt nur am Tag der Auswahl, danach wieder „heute“ (null). */
    fun monthSelectedDay(context: Context, id: Int, today: LocalDate): LocalDate? {
        val p = prefs(context)
        if (p.getLong(KEY_MONTH_SELECTED_ON + id, Long.MIN_VALUE) != today.toEpochDay()) return null
        return LocalDate.ofEpochDay(p.getLong(KEY_MONTH_SELECTED + id, today.toEpochDay()))
    }

    fun setMonthSelectedDay(context: Context, id: Int, day: LocalDate, today: LocalDate) {
        prefs(context).edit()
            .putLong(KEY_MONTH_SELECTED + id, day.toEpochDay())
            .putLong(KEY_MONTH_SELECTED_ON + id, today.toEpochDay())
            .apply()
    }
}
