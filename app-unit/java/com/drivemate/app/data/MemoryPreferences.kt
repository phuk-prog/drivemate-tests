package com.drivemate.app.data

import android.content.SharedPreferences

/** In-memory storage for behavior tests; no Android runtime or location provider. */
class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String?, value: String?) = apply { changes[key!!] = value }
        override fun putStringSet(key: String?, value: MutableSet<String>?) = apply { changes[key!!] = value?.toSet() }
        override fun putInt(key: String?, value: Int) = apply { changes[key!!] = value }
        override fun putLong(key: String?, value: Long) = apply { changes[key!!] = value }
        override fun putFloat(key: String?, value: Float) = apply { changes[key!!] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { changes[key!!] = value }
        override fun remove(key: String?) = apply { changes[key!!] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            changes.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
        }
    }
}
