package com.example.demo

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.lang.Math.abs

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: View
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable { performLibraryClick() }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
        }
        return START_STICKY
    }

    private fun createFloatingButton() {
        val metrics = resources.displayMetrics
        val defaultX = metrics.widthPixels / 10
        val defaultY = metrics.heightPixels / 5

        floatingButton = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = defaultX
        params.y = defaultY

        // Configure the floating button
        val buttonView = floatingButton.findViewById<Button>(R.id.floating_action)

        // Make the button movable with touch
        buttonView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // If moved more than a threshold, consider it a move operation
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isMoving = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(floatingButton, params)
                        } catch (e: IllegalArgumentException) {
                            Log.e("FloatingService", "Error updating button position", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        // Only handle click if it wasn't a move operation
                        handler.removeCallbacks(runnable)
                        handler.postDelayed(runnable, 100)
                    }
                    true
                }
                else -> false
            }
        }

        // Long press to show options menu
        buttonView.setOnLongClickListener {
            showOptionsPopup()
            true
        }

        try {
            windowManager.addView(floatingButton, params)
        } catch (e: Exception) {
            Log.e("FloatingService", "Error adding floating button", e)
        }
    }

    private fun showOptionsPopup() {
        val popupMenu = PopupMenu(this, floatingButton)
        popupMenu.menuInflater.inflate(R.menu.floating_button_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_close -> {
                    stopSelf()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun performLibraryClick() {
        if (!isAccessibilityServiceRunning()) {
            Toast.makeText(this, "Accessibility service not enabled", Toast.LENGTH_SHORT).show()
            // Show notification to help user enable the accessibility service
            showEnableAccessibilityNotification()
            return
        }

        // Check if Kindle is installed
        val packageManager = packageManager
        try {
            packageManager.getPackageInfo("com.amazon.kindle", 0)
            // Kindle is installed, check if it's running
            if (!isKindleRunning()) {
                // Launch Kindle app
                val launchIntent = packageManager.getLaunchIntentForPackage("com.amazon.kindle")
                launchIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                    // Feedback to user
                    Toast.makeText(this, "Opening Kindle...", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Kindle is already running, just signal accessibility service
                Toast.makeText(this, "Navigating to Library...", Toast.LENGTH_SHORT).show()
                val intent = Intent("com.yourapp.ACTION_NAVIGATE_LIBRARY")
                sendBroadcast(intent)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Toast.makeText(this, "Kindle app not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isKindleRunning(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val processes = activityManager.runningAppProcesses ?: return false
        return processes.any { it.processName == "com.amazon.kindle" }
    }

    private fun isAccessibilityServiceRunning(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )

        if (accessibilityEnabled == 1) {
            val serviceString = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return serviceString?.contains("${packageName}/.KindleAccessibilityService") == true
        }
        return false
    }

    private fun showEnableAccessibilityNotification() {
        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "accessibility_channel",
                "Accessibility Service Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(this, "accessibility_channel")
            .setContentTitle("Accessibility Service Required")
            .setContentText("Please enable the accessibility service to use this app")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingButton.isInitialized) {
            try {
                windowManager.removeView(floatingButton)
            } catch (e: IllegalArgumentException) {
                Log.e("FloatingService", "Error removing view", e)
            }
        }

        // Notify user that service has stopped
        Toast.makeText(this, "Floating button service stopped", Toast.LENGTH_SHORT).show()
    }

}
