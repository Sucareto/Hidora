package app.sucareto.hidora.core.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Pref<T>(initial: T, private val save: (T) -> Unit) : ReadWriteProperty<Any?, T> {
    private var state by mutableStateOf(initial)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = state

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (value == state) return
        state = value
        save(value)
    }
}

open class PrefsStore(context: Context, name: String) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    protected fun boolean(key: String, default: Boolean) =
        Pref(prefs.getBoolean(key, default)) { prefs.edit { putBoolean(key, it) } }

    protected fun int(key: String, default: Int) =
        Pref(prefs.getInt(key, default)) { prefs.edit { putInt(key, it) } }

    protected fun string(key: String, default: String) =
        Pref(prefs.getString(key, default) ?: default) { prefs.edit { putString(key, it) } }

    protected inline fun <reified E : Enum<E>> enum(key: String, default: E) =
        Pref(enumValues<E>().firstOrNull { it.name == rawString(key) } ?: default) { putRawString(key, it.name) }

    protected fun remove(vararg keys: String) {
        prefs.edit { keys.forEach { remove(it) } }
    }

    @PublishedApi
    internal fun rawString(key: String): String? = prefs.getString(key, null)

    @PublishedApi
    internal fun putRawString(key: String, value: String) = prefs.edit { putString(key, value) }
}

class AppSettings private constructor(context: Context) : PrefsStore(context, "app_settings") {
    var stopOnLeave by boolean("stop_on_leave", false)
    var rootGranted by boolean("root_granted", false)

    companion object {
        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) { instance ?: AppSettings(context).also { instance = it } }
    }
}
