package com.example.domain

import java.util.Calendar

object QuietHoursEvaluator {
    fun isWithinQuietHours(
        enabled: Boolean,
        startHour: Int,
        endHour: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!enabled) return false
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return isHourInQuietWindow(hour, startHour.coerceIn(0, 23), endHour.coerceIn(0, 23))
    }

    fun isHourInQuietWindow(currentHour: Int, startHour: Int, endHour: Int): Boolean {
        if (startHour == endHour) return false
        return if (startHour < endHour) {
            currentHour in startHour until endHour
        } else {
            // Overnight window, e.g., 23 -> 7
            currentHour >= startHour || currentHour < endHour
        }
    }
}
