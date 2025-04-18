package com.example.imageloader.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.imageloader.MainActivity
import com.example.imageloader.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ImageLoaderService : Service() {
    private val CHANNEL_ID = "ImageLoaderChannel"
    private val NOTIFICATION_ID = 1
    private val handler = Handler(Looper.getMainLooper())
    private var updateCount = 0 // Track notification updates
    private val notificationRunnable = object : Runnable {
        override fun run() {
            showNotification()
            // Schedule next notification after 10 seconds
            handler.postDelayed(this, TimeUnit.SECONDS.toMillis(3))
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        Log.d("ImageLoaderService", "Service onCreate called")
        createNotificationChannel()
        val notification = createNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d("ImageLoaderService", "Foreground service started with notification ID: $NOTIFICATION_ID")
        } catch (e: Exception) {
            Log.e("ImageLoaderService", "Failed to start foreground service", e)
        }

        // Schedule periodic notifications
        handler.post(notificationRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ImageLoaderService", "onStartCommand called with intent: $intent")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d("ImageLoaderService", "onBind called")
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ImageLoaderService", "Service onDestroy called")
        handler.removeCallbacks(notificationRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Image Loader Service"
            val descriptionText = "Notifications from Image Loader app"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("ImageLoaderService", "Notification channel created: $CHANNEL_ID with importance: $importance")

            // Verify channel status
            val createdChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            Log.d("ImageLoaderService", "Channel enabled: ${createdChannel?.importance != NotificationManager.IMPORTANCE_NONE}")
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Use a default icon if R.drawable.ic_notification is missing
        val iconRes = try {
            R.drawable.ic_notification
        } catch (e: Exception) {
            Log.w("ImageLoaderService", "Custom icon missing, using default Android icon")
            android.R.drawable.ic_dialog_info
        }

        // Format current time to real-time string
        updateCount++
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Image Loader Service")
            .setContentText("Service running. Update #$updateCount at $currentTime")
            .setSmallIcon(iconRes)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun showNotification() {
        Log.d("ImageLoaderService", "Sending notification #$updateCount at ${System.currentTimeMillis()}")
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e("ImageLoaderService", "Failed to send notification", e)
        }
    }
}