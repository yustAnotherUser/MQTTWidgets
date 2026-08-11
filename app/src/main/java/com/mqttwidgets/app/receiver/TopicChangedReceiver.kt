package com.mqttwidgets.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mqttwidgets.app.data.AppDatabase
import com.mqttwidgets.app.service.MqttService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TopicChangedReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TOPICS_CHANGED) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                if (isServiceRunning(context)) {
                    val resubscribeIntent = MqttService.createResubscribeIntent(context)
                    context.startService(resubscribeIntent)
                } else {
                    val db = AppDatabase.getDatabase(context)
                    val cards = db.cardDao().getAllCardsSync()
                    if (cards.isNotEmpty()) {
                        val serviceIntent = Intent(context, MqttService::class.java)
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isServiceRunning(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (MqttService::class.java.name == service.service.className) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val ACTION_TOPICS_CHANGED = "com.mqttwidgets.TOPICS_CHANGED"
    }
}
