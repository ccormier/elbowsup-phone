package com.keejii.elbowsup.storage

import android.content.SharedPreferences

/** A plain in-memory SharedPreferences, so storage logic can run on the JVM. */
class FakePreferences : SharedPreferences {
    private val values = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(values)
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(
        l: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        l: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removed = HashSet<String>()
        private var clearAll = false

        private fun put(key: String, value: Any?) = apply { pending[key] = value }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean {
            if (clearAll) values.clear()
            removed.forEach { values.remove(it) }
            values.putAll(pending)
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
