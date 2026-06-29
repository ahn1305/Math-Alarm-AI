package com.example.alarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "Alarm",
    val isEnabled: Boolean = true,
    val isVibrate: Boolean = true,
    val daysSelected: String = "", // Comma-separated list like "Mon,Tue,Wed,Thu,Fri" or empty for once
    val toneUri: String = "",
    val toneName: String = "Default Tone"
) {
    fun getFormattedTime(): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayedHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%02d:%02d %s", displayedHour, minute, amPm)
    }

    fun getDaysSummary(): String {
        if (daysSelected.isEmpty()) return "Once"
        val list = daysSelected.split(",").filter { it.isNotEmpty() }
        if (list.size == 7) return "Every day"
        if (list.size == 5 && !list.contains("Sat") && !list.contains("Sun")) return "Weekdays"
        if (list.size == 2 && list.contains("Sat") && list.contains("Sun")) return "Weekends"
        return list.joinToString(", ")
    }
}
