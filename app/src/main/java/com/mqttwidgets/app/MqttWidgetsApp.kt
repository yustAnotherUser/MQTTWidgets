package com.mqttwidgets.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class MqttWidgetsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "MQTT Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "MQTT connection status"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "mqtt_service"
    }
}
