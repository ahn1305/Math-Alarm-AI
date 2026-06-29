package com.example.alarm.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.alarm.data.Alarm
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(alarm: Alarm) {
        if (alarmManager == null) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_LABEL", alarm.label)
            putExtra("ALARM_TONE_URI", alarm.toneUri)
        }

        // Use unique request code per alarm
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = calculateTriggerTime(alarm)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Scheduled alarm ${alarm.id} for $triggerTime")
        } catch (e: SecurityException) {
            // Gracefully fallback
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.w("AlarmScheduler", "Fallback scheduled alarm ${alarm.id} due to security exception", e)
        }
    }

    fun cancel(alarm: Alarm) {
        if (alarmManager == null) return

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmScheduler", "Canceled alarm ${alarm.id}")
        }
    }

    fun scheduleTestAlarm(delaySeconds: Int, alarmId: Int, toneUri: String = "") {
        if (alarmManager == null) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", "Quick Test Alarm")
            putExtra("ALARM_TONE_URI", toneUri)
            putExtra("IS_TEST", true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999, // Unique test request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Scheduled quick test alarm in $delaySeconds seconds")
        } catch (e: Exception) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    private fun calculateTriggerTime(alarm: Alarm): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = System.currentTimeMillis()

        if (alarm.daysSelected.isEmpty()) {
            // Once-off alarm: if time is already past, set for tomorrow
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }

        // For repeating alarms on specific days, find the nearest upcoming day of week
        val selectedDaysList = alarm.daysSelected.split(",").filter { it.isNotEmpty() }
        val daysOfWeekMap = mapOf(
            "Sun" to Calendar.SUNDAY,
            "Mon" to Calendar.MONDAY,
            "Tue" to Calendar.TUESDAY,
            "Wed" to Calendar.WEDNESDAY,
            "Thu" to Calendar.THURSDAY,
            "Fri" to Calendar.FRIDAY,
            "Sat" to Calendar.SATURDAY
        )

        val targetDays = selectedDaysList.mapNotNull { daysOfWeekMap[it] }.sorted()

        if (targetDays.isEmpty()) {
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }

        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // Find if any scheduled day is today (and time is in future) or later this week
        for (day in targetDays) {
            if (day == currentDayOfWeek && calendar.timeInMillis > now) {
                return calendar.timeInMillis
            } else if (day > currentDayOfWeek) {
                val diff = day - currentDayOfWeek
                calendar.add(Calendar.DAY_OF_YEAR, diff)
                return calendar.timeInMillis
            }
        }

        // If none found later this week, pick the first scheduled day next week
        val firstDay = targetDays.first()
        val diff = (7 - currentDayOfWeek) + firstDay
        calendar.add(Calendar.DAY_OF_YEAR, diff)
        return calendar.timeInMillis
    }
}
