package com.mqttwidgets.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class PrefsManager(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "mqtt_widgets_prefs",
        Context.MODE_PRIVATE
    )

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        "mqtt_widgets_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_BROKER_HOST = "broker_host"
        private const val KEY_BROKER_PORT = "broker_port"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_WIZARD_COMPLETED = "wizard_completed"
    }

    fun getBrokerHost(): String = prefs.getString(KEY_BROKER_HOST, "") ?: ""

    fun setBrokerHost(host: String) {
        prefs.edit().putString(KEY_BROKER_HOST, host).apply()
    }

    fun getBrokerPort(): Int = prefs.getInt(KEY_BROKER_PORT, 1883)

    fun setBrokerPort(port: Int) {
        prefs.edit().putInt(KEY_BROKER_PORT, port).apply()
    }

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""

    fun setUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun isConfigured(): Boolean {
        return getBrokerHost().isNotBlank() && getBrokerPort() > 0
    }

    fun getPassword(): String = encryptedPrefs.getString(KEY_PASSWORD, "") ?: ""

    fun setPassword(password: String) {
        encryptedPrefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    fun hasWizardCompleted(): Boolean = prefs.getBoolean(KEY_WIZARD_COMPLETED, false)

    fun setWizardCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_WIZARD_COMPLETED, completed).apply()
    }
}
