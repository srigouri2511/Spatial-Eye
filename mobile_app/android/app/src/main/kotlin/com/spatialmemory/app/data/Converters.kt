package com.spatialmemory.app.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Room TypeConverters for mapping complex types, lists, timestamps, and enums into SQLite-compatible types.
 */
class Converters {

    // ==========================================
    // WalkCorridor pathPoints (List<Pair<Float, Float>>) JSON Converters
    // ==========================================

    /**
     * Converts a list of 2D coordinate pairs `(X, Z)` to a JSON-formatted String.
     *
     * @param points List of Pair<Float, Float> representing floor-plane coordinates.
     * @return JSON Array String representation e.g. `[[1.2,3.4],[5.6,7.8]]`, or null.
     */
    @TypeConverter
    fun fromPathPoints(points: List<Pair<Float, Float>>?): String? {
        if (points == null) return null
        val jsonArray = JSONArray()
        for (point in points) {
            val pairArray = JSONArray()
            pairArray.put(point.first.toDouble())
            pairArray.put(point.second.toDouble())
            jsonArray.put(pairArray)
        }
        return jsonArray.toString()
    }

    /**
     * Parses a JSON-formatted String back to a list of 2D coordinate pairs `(X, Z)`.
     *
     * @param json String containing JSON Array representation.
     * @return List of Pair<Float, Float> coordinates, or null.
     */
    @TypeConverter
    fun toPathPoints(json: String?): List<Pair<Float, Float>>? {
        if (json.isNullOrBlank()) return null
        return try {
            val pointsList = mutableListOf<Pair<Float, Float>>()
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val pairArray = jsonArray.getJSONArray(i)
                val x = pairArray.getDouble(0).toFloat()
                val z = pairArray.getDouble(1).toFloat()
                pointsList.add(Pair(x, z))
            }
            pointsList
        } catch (e: Exception) {
            null
        }
    }

    // ==========================================
    // Timestamp Converters
    // ==========================================

    /**
     * Converts epoch timestamp in milliseconds (Long) to a Java [Date].
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /**
     * Converts a Java [Date] to an epoch timestamp in milliseconds (Long).
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    // ==========================================
    // Enum Converters
    // ==========================================

    /**
     * Converts [PermanenceClass] enum to String.
     */
    @TypeConverter
    fun fromPermanenceClass(value: PermanenceClass?): String? {
        return value?.name
    }

    /**
     * Converts String to [PermanenceClass] enum.
     */
    @TypeConverter
    fun toPermanenceClass(value: String?): PermanenceClass? {
        return value?.let {
            try {
                PermanenceClass.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Converts [ChangeEventType] enum to String.
     */
    @TypeConverter
    fun fromChangeEventType(value: ChangeEventType?): String? {
        return value?.name
    }

    /**
     * Converts String to [ChangeEventType] enum.
     */
    @TypeConverter
    fun toChangeEventType(value: String?): ChangeEventType? {
        return value?.let {
            try {
                ChangeEventType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Converts [UserFeedback] enum to String.
     */
    @TypeConverter
    fun fromUserFeedback(value: UserFeedback?): String? {
        return value?.name
    }

    /**
     * Converts String to [UserFeedback] enum.
     */
    @TypeConverter
    fun toUserFeedback(value: String?): UserFeedback? {
        return value?.let {
            try {
                UserFeedback.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
