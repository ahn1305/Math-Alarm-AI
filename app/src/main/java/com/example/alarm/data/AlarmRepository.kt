package com.example.alarm.data

import com.example.alarm.alarm.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler
) {
    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()

    suspend fun getAlarmById(id: Int): Alarm? = alarmDao.getAlarmById(id)

    suspend fun insert(alarm: Alarm): Long {
        val id = alarmDao.insertAlarm(alarm)
        val insertedAlarm = alarm.copy(id = id.toInt())
        if (insertedAlarm.isEnabled) {
            alarmScheduler.schedule(insertedAlarm)
        }
        return id
    }

    suspend fun update(alarm: Alarm) {
        alarmDao.updateAlarm(alarm)
        if (alarm.isEnabled) {
            alarmScheduler.schedule(alarm)
        } else {
            alarmScheduler.cancel(alarm)
        }
    }

    suspend fun toggleEnabled(alarm: Alarm) {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        alarmDao.updateAlarm(updated)
        if (updated.isEnabled) {
            alarmScheduler.schedule(updated)
        } else {
            alarmScheduler.cancel(updated)
        }
    }

    suspend fun delete(alarm: Alarm) {
        alarmScheduler.cancel(alarm)
        alarmDao.deleteAlarm(alarm)
    }

    fun scheduleQuickTestAlarm(delaySeconds: Int, alarmId: Int, toneUri: String = "") {
        alarmScheduler.scheduleTestAlarm(delaySeconds, alarmId, toneUri)
    }
}
