package pt.isep.agriot

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Gestor de idioma da aplicação (Português / English).
 * Persiste a preferência e atualiza a configuração de Locale do Android.
 */
object LanguageManager {
    private const val PREFS_NAME = "agriot_settings"
    private const val KEY_LANG = "language"

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, "pt") ?: "pt"
    }

    fun isEnglish(context: Context): Boolean = getLanguage(context) == "en"

    fun initApp(context: Context) {
        val lang = getLanguage(context)
        val locale = if (lang == "en") Locale.ENGLISH else Locale("pt", "PT")
        Locale.setDefault(locale)
    }

    fun setLanguage(activity: Activity, lang: String) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, lang).apply()
        applyLanguage(activity)
        activity.recreate()
    }

    fun applyLanguage(context: Context): Context {
        val lang = getLanguage(context)
        val locale = if (lang == "en") Locale.ENGLISH else Locale("pt", "PT")
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        return context.createConfigurationContext(config)
    }
}
