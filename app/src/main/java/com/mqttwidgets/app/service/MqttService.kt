package com.mqttwidgets.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.mqttwidgets.app.R
import com.mqttwidgets.app.data.AppDatabase
import com.mqttwidgets.app.data.Card
import com.mqttwidgets.app.data.PrefsManager
import com.mqttwidgets.app.util.PayloadParser
import com.mqttwidgets.app.util.WidgetRenderer
import com.mqttwidgets.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): MqttService = this@MqttService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val topicManager = TopicManager()
    private var mqttClient: MqttClient? = null
    private var notificationManager: NotificationManager? = null
    private var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private var reconnectJob: Job? = null
    private var connected = false
    private var lastCards: List<Card> = emptyList()
    private val connectMutex = Mutex()
    private val messageMutex = Mutex()

    private fun topicVariants(topic: String): List<String> {
        val t = topic.trim()
        if (t.isEmpty()) return emptyList()
        val variant = if (t.startsWith("/")) t.removePrefix("/") else "/$t"
        return if (variant == t) listOf(t) else listOf(t, variant)
    }

    private fun normalizeTopic(topic: String): String = topic.trim().removePrefix("/")

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Disconnected"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA_ACTION)
        when (action) {
            ACTION_RESUBSCRIBE -> {
                scope.launch { resubscribeAll() }
            }
            else -> {
                reconnectJob?.cancel()
                scope.launch { initializeConnection() }
            }
        }
        return START_STICKY
    }

    private suspend fun initializeConnection() {
        val db = AppDatabase.getDatabase(this@MqttService)
        val cards = db.cardDao().getAllCardsSync()
        if (cards.isEmpty()) {
            updateNotification("Disconnected")
            stopSelf()
            return
        }
        topicManager.reset()
        cards.forEach { topicManager.incrementRef(it.topic) }
        lastCards = emptyList()
        connect()
    }

    private suspend fun connect() = connectMutex.withLock {
        val prefs = PrefsManager(this@MqttService)
        if (!prefs.isConfigured()) {
            updateNotification("Not configured")
            return@withLock
        }

        if (connected && mqttClient?.isConnected == true) {
            subscribeAll()
            return@withLock
        }

        try { mqttClient?.disconnect() } catch (_: Exception) {}
        try { mqttClient?.close() } catch (_: Exception) {}

        val host = prefs.getBrokerHost()
        val port = prefs.getBrokerPort()
        val username = prefs.getUsername()
        val password = prefs.getPassword()
        val uri = "tcp://$host:$port"

        try {
            val client = MqttClient(uri, MqttClient.generateClientId(), MemoryPersistence())
            client.setCallback(mqttCallback)
            mqttClient = client

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = CONNECT_TIMEOUT_SEC
                keepAliveInterval = KEEPALIVE_SEC
                maxInflight = 100
                if (username.isNotBlank()) userName = username
                if (password.isNotBlank()) this.password = password.toCharArray()
            }

            client.connect(options)
            connected = true
            reconnectDelay = INITIAL_RECONNECT_DELAY_MS
            lastCards = emptyList()
            subscribeAll()
            updateNotification("Connected")
        } catch (e: Exception) {
            connected = false
            updateNotification("Disconnected: ${e.message?.take(40)}")
            scheduleReconnect()
        }
    }

    private val mqttCallback = object : MqttCallback {
        override fun connectionLost(cause: Throwable?) {
            connected = false
            updateNotification("Disconnected")
            scheduleReconnect()
        }

        override fun messageArrived(topic: String, message: MqttMessage?) {
            message ?: return
            val payload = String(message.payload)
            scope.launch { messageMutex.withLock { handleIncomingMessage(topic, payload) } }
        }

        override fun deliveryComplete(token: org.eclipse.paho.client.mqttv3.IMqttDeliveryToken?) {}
    }

    private suspend fun handleIncomingMessage(topic: String, payload: String) {
        topicValues.update { map ->
            map.toMutableMap().apply { put(topic, payload) }
        }

        val db = AppDatabase.getDatabase(this@MqttService)
        val allCards = db.cardDao().getAllCardsSync()
        val normTopic = normalizeTopic(topic)
        val cards = allCards.filter { normalizeTopic(it.topic) == normTopic }
        val now = System.currentTimeMillis()

        for (card in cards) {
            val formattedValue = PayloadParser.parse(payload, card)
            db.cardDao().updateCardValue(
                cardId = card.cardId,
                lastValue = payload,
                lastFormattedValue = formattedValue,
                lastUpdated = now,
                consecutiveFailures = 0
            )
            val updatedCard = card.copy(
                lastValue = payload,
                lastFormattedValue = formattedValue,
                lastUpdated = now,
                consecutiveFailures = 0
            )
            updateWidgetsForCard(updatedCard)
        }
    }

    private fun updateWidgetsForCard(card: Card) {
        val appWidgetManager = AppWidgetManager.getInstance(this) ?: return
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        val normTopic = normalizeTopic(card.topic)

        val providers = listOf(
            Triple(ComponentName(this, "com.mqttwidgets.app.widget.WidgetProviderSmall"), R.layout.widget_small, "SMALL"),
            Triple(ComponentName(this, "com.mqttwidgets.app.widget.WidgetProviderCompact"), R.layout.widget_compact, "COMPACT"),
            Triple(ComponentName(this, "com.mqttwidgets.app.widget.WidgetProviderWide"), R.layout.widget_wide, "WIDE")
        )

        val pinnedIds = card.pinnedWidgetIds
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
            .toSet()

        val content = WidgetRenderer.render(card)
        var updatedCount = 0

        for ((provider, layoutRes, size) in providers) {
            val hosted = try {
                appWidgetManager.getAppWidgetIds(provider) ?: intArrayOf()
            } catch (_: Exception) {
                intArrayOf()
            }

            for (widgetId in hosted) {
                val storedTopic = prefs.getString("widget_${widgetId}_topic", null)
                val matches = widgetId in pinnedIds ||
                    storedTopic == null ||
                    storedTopic.isBlank() ||
                    normalizeTopic(storedTopic) == normTopic

                if (!matches) continue

                try {
                    val views = RemoteViews(packageName, layoutRes)
                    views.setTextViewText(R.id.widget_value, content.displayText)
                    views.setImageViewBitmap(R.id.widget_bg, WidgetUpdater.roundedBackgroundBitmap(content.backgroundColor, resources.displayMetrics.density))
                    val fontSize = prefs.getFloat("widget_${widgetId}_fontSize", 0f)
                    if (fontSize > 0f) {
                        views.setTextViewTextSize(R.id.widget_value, android.util.TypedValue.COMPLEX_UNIT_SP, fontSize)
                    }
                    try { views.setTextViewText(R.id.widget_label, content.labelText) } catch (_: Exception) {}
                    try { views.setTextViewText(R.id.widget_time, content.timeText) } catch (_: Exception) {}
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    launchIntent?.let {
                        val pi = android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                        views.setOnClickPendingIntent(R.id.widget_container, pi)
                    }
                    WidgetUpdater.saveWidgetConfig(this, widgetId, card)
                    appWidgetManager.updateAppWidget(widgetId, views)
                    updatedCount++
                } catch (e: Exception) {
                    updatedCount = 0
                    e.printStackTrace()
                }
            }
            if (updatedCount > 0) break
        }
    }

    private suspend fun subscribeAll() {
        val db = AppDatabase.getDatabase(this@MqttService)
        val newCards = db.cardDao().getAllCardsSync()
        val changes = topicManager.computeTopicChanges(lastCards, newCards)

        if (!connected) {
            lastCards = newCards
            return
        }

        try {
            for (topic in changes.toUnsubscribe) {
                mqttClient?.unsubscribe(topic)
            }
            if (changes.toSubscribe.isNotEmpty()) {
                val topics = mutableListOf<String>()
                for (t in changes.toSubscribe) topics.addAll(topicVariants(t))
                if (topics.isNotEmpty()) {
                    val qosArray = IntArray(topics.size) { 1 }
                    mqttClient?.subscribe(topics.toTypedArray(), qosArray)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        topicManager.reset()
        newCards.forEach { topicManager.incrementRef(it.topic) }
        lastCards = newCards
    }

    fun addTopic(topic: String) {
        val isFirst = topicManager.incrementRef(topic)
        if (isFirst && connected) {
            scope.launch {
                try {
                    mqttClient?.subscribe(topic, 1)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun removeTopic(topic: String) {
        if (topicManager.decrementRef(topic) && connected) {
            scope.launch {
                try {
                    mqttClient?.unsubscribe(topic)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun unsubscribeTopic(topic: String) {
        topicManager.resetRef(topic)
        if (connected) {
            scope.launch {
                try {
                    mqttClient?.unsubscribe(topic)
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun resubscribeAll() {
        reconnectJob?.cancel()
        reconnectDelay = INITIAL_RECONNECT_DELAY_MS
        if (connected) {
            subscribeAll()
        } else {
            initializeConnection()
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            updateNotification("Reconnecting...")
            connect()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MQTT Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MQTT connection status"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val pendingIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MQTT Widgets")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        reconnectJob?.cancel()
        scope.cancel()
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
        } catch (_: Exception) {
        }
        connected = false
        super.onDestroy()
    }

    companion object {
        val topicValues = MutableStateFlow<Map<String, String>>(emptyMap())

        private const val CHANNEL_ID = "mqtt_service"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_ACTION = "action"
        private const val ACTION_RESUBSCRIBE = "resubscribe"
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 60000L
        private const val CONNECT_TIMEOUT_SEC = 10
        private const val KEEPALIVE_SEC = 60

        fun createResubscribeIntent(context: Context): Intent {
            return Intent(context, MqttService::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_RESUBSCRIBE)
            }
        }
    }
}
