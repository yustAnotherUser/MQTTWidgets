package com.mqttwidgets.app.util

import com.mqttwidgets.app.data.Card
import com.mqttwidgets.app.data.CardFormat
import org.json.JSONObject
import java.util.Locale

object PayloadParser {

    fun parse(value: String, card: Card): String {
        return when (card.format) {
            CardFormat.RAW -> value
            CardFormat.NUMBER -> parseNumber(value, card)
            CardFormat.JSON -> parseJson(value, card)
        }
    }

    private fun parseNumber(value: String, card: Card): String {
        return try {
            val number = value.toDouble()
            val formatted = String.format(Locale.US, "%.${card.decimals}f", number)
            if (card.unit.isNotBlank()) "$formatted ${card.unit}" else formatted
        } catch (_: NumberFormatException) {
            value
        }
    }

    private fun parseJson(value: String, card: Card): String {
        if (card.jsonPath.isBlank()) return value
        return try {
            val json = JSONObject(value)
            val keys = card.jsonPath.split(".")
            var current: Any = json
            for (key in keys) {
                current = when (current) {
                    is JSONObject -> current.get(key)
                    else -> return value
                }
            }
            when (current) {
                is Double -> String.format(Locale.US, "%.${card.decimals}f", current)
                is Number -> current.toString()
                else -> current.toString()
            }.let { formatted ->
                if (card.unit.isNotBlank()) "$formatted ${card.unit}" else formatted
            }
        } catch (_: Exception) {
            value
        }
    }

    fun extractNumber(value: String, card: Card): Double? {
        return when (card.format) {
            CardFormat.NUMBER -> value.trim().toDoubleOrNull()
            CardFormat.JSON -> {
                if (card.jsonPath.isBlank()) return null
                try {
                    val json = JSONObject(value)
                    var current: Any = json
                    for (key in card.jsonPath.split(".")) {
                        current = when (current) {
                            is JSONObject -> current.get(key)
                            else -> return null
                        }
                    }
                    when (current) {
                        is Double -> current
                        is Number -> current.toDouble()
                        else -> current.toString().trim().toDoubleOrNull()
                    }
                } catch (_: Exception) {
                    null
                }
            }
            CardFormat.RAW -> null
        }
    }

    fun resolveColor(value: String, card: Card): Int {
        val number = extractNumber(value, card) ?: return card.normalColor
        return when {
            number > card.highThreshold -> card.highColor
            number < card.lowThreshold -> card.lowColor
            else -> card.normalColor
        }
    }

    fun extractJsonKeys(jsonString: String): List<Pair<String, String>> {
        return try {
            val json = JSONObject(jsonString)
            mutableListOf<Pair<String, String>>().also { flatten("", json, it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun flatten(prefix: String, obj: JSONObject, result: MutableList<Pair<String, String>>) {
        for (key in obj.keys()) {
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = obj.get(key)
            if (value is JSONObject) {
                flatten(path, value, result)
            } else {
                result.add(path to value.toString())
            }
        }
    }
}
