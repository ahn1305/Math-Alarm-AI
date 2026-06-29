package com.example.alarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        private const val CHANNEL_ID = "alarm_service_channel"
        private const val NOTIFICATION_ID = 1001

        private val _ringingState = MutableStateFlow<RingingState>(RingingState.Idle)
        val ringingState: StateFlow<RingingState> = _ringingState.asStateFlow()

        fun stopAlarm(context: Context) {
            val intent = Intent(context, AlarmService::class.java)
            context.stopService(intent)
            _ringingState.value = RingingState.Idle
        }
    }

    sealed interface RingingState {
        object Idle : RingingState
        data class Ringing(val alarmId: Int, val label: String) : RingingState
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        val label = intent?.getStringExtra("ALARM_LABEL") ?: "Alarm"
        val toneUri = intent?.getStringExtra("ALARM_TONE_URI")
        
        Log.d("AlarmService", "AlarmService starting for alarmId=$alarmId, label=$label, toneUri=$toneUri")
        
        _ringingState.value = RingingState.Ringing(alarmId, label)

        // Create notification that opens MainActivity when clicked
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("RINGING_ALARM_ID", alarmId)
            putExtra("RINGING_ALARM_LABEL", label)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Math Alarm Ringing!")
            .setContentText("Solve the math problem to dismiss: $label")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(pendingIntent, true) // High priority full-screen
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Automatically launch MainActivity to prompt user
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("RINGING_ALARM_ID", alarmId)
            putExtra("RINGING_ALARM_LABEL", label)
        }
        startActivity(launchIntent)

        startRinging(toneUri)
        startVibrating()

        return START_STICKY
    }

    private fun startRinging(toneUriStr: String?) {
        try {
            val alarmUri: Uri = if (!toneUriStr.isNullOrEmpty()) {
                Uri.parse(toneUriStr)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Failed to start media player, trying fallback default alarm sound", e)
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmService, fallbackUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (fallbackEx: Exception) {
                Log.e("AlarmService", "Fallback media player also failed", fallbackEx)
            }
        }
    }

    private fun startVibrating() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 500), 0) // Loop forever
        } catch (e: Exception) {
            Log.e("AlarmService", "Failed to start vibration", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Math Alarm Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Plays alarm sound and keeps the notification until solved."
                setSound(null, null) // Sound is played by MediaPlayer
                enableVibration(false) // Vibration is handled by Service
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AlarmService", "AlarmService destroyed")
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        @Suppress("DEPRECATION")
        vibrator?.cancel()
        vibrator = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
