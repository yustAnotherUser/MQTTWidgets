package com.mqttwidgets.app.service

import com.mqttwidgets.app.data.Card

class TopicManager {

    data class TopicChanges(
        val toSubscribe: Set<String>,
        val toUnsubscribe: Set<String>
    )

    private val refCounts = mutableMapOf<String, Int>()

    fun computeTopicChanges(oldCards: List<Card>, newCards: List<Card>): TopicChanges {
        val oldCounts = oldCards.groupingBy { it.topic }.eachCount()
        val newCounts = newCards.groupingBy { it.topic }.eachCount()
        val toSubscribe = mutableSetOf<String>()
        val toUnsubscribe = mutableSetOf<String>()

        for (topic in (oldCounts.keys + newCounts.keys).distinct()) {
            val oldCount = oldCounts[topic] ?: 0
            val newCount = newCounts[topic] ?: 0
            if (oldCount == 0 && newCount > 0) toSubscribe.add(topic)
            if (oldCount > 0 && newCount == 0) toUnsubscribe.add(topic)
        }

        return TopicChanges(toSubscribe, toUnsubscribe)
    }

    fun incrementRef(topic: String): Boolean {
        val newCount = (refCounts[topic] ?: 0) + 1
        refCounts[topic] = newCount
        return newCount == 1
    }

    fun decrementRef(topic: String): Boolean {
        val count = (refCounts[topic] ?: 0) - 1
        if (count <= 0) {
            refCounts.remove(topic)
            return true
        }
        refCounts[topic] = count
        return false
    }

    fun getRefCount(topic: String): Int = refCounts[topic] ?: 0

    fun currentTopics(): Set<String> = refCounts.keys.toSet()

    fun resetRef(topic: String) {
        refCounts.remove(topic)
    }

    fun reset() = refCounts.clear()
}
