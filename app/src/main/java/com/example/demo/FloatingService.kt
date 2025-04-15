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
    private val TAG = "KindleNav_FloatingService" // Tag for logging

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun listAllInstalledPackages() {
        Log.i(TAG, "--- LISTING ALL INSTALLED PACKAGES ---")
        val pm = packageManager
        val packages = pm.getInstalledPackages(0)

        packages.forEach { packageInfo ->
            val packageName = packageInfo.packageName
            try {
                // Check if applicationInfo is not null before using it
                val appInfo = packageInfo.applicationInfo
                if (appInfo != null) {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    Log.i(TAG, "App: $appName, Package: $packageName")
                } else {
                    Log.i(TAG, "Package: $packageName (No application info available)")
                }
            } catch (e: Exception) {
                Log.i(TAG, "Package: $packageName (Error: ${e.message})")
            }
        }
        Log.i(TAG, "--- END OF PACKAGE LIST ---")
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Floating Service onCreate started")

        listAllInstalledPackages()
        // Create a foreground notification to keep service running
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = createNotificationChannel()
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Kindle Navigator")
                .setContentText("Tap to navigate to Library")
                .setSmallIcon(R.drawable.ic_notification)
                .build()

            startForeground(1002, notification)
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
        Log.i(TAG, "Floating Service created successfully")

        // For testing only - remove in production
        Handler(Looper.getMainLooper()).postDelayed({
            Log.i(TAG, "Auto-triggering performLibraryClick for testing")
            performLibraryClick()
        }, 3000) // Wait 3 seconds then trigger
    }

    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "kindle_navigator_channel"
            val channelName = "Kindle Navigator Service"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            return channelId
        }
        return ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        if (intent?.action == "STOP_SERVICE") {
            Log.i(TAG, "Stop service action received")
            stopSelf()
        }
        return START_STICKY
    }

    private fun createFloatingButton() {
        Log.d(TAG, "Creating floating button")
        val metrics = resources.displayMetrics
        // Position in the middle of the screen
        val defaultX = metrics.widthPixels / 2 - 100
        val defaultY = metrics.heightPixels / 3

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

        // Set long press listener on the button itself
        buttonView.setOnLongClickListener {
            Log.i(TAG, "Long press detected on floating button")
            showOptionsPopup()
            true
        }

        // Set touch listener on the entire floating view for better dragging
        floatingButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d(TAG, "Touch DOWN on floating button")
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

                    // Lower threshold for better responsiveness (or remove threshold entirely)
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        if (!isMoving) {
                            Log.d(TAG, "Started dragging button")
                        }
                        isMoving = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(floatingButton, params)
                        } catch (e: IllegalArgumentException) {
                            Log.e(TAG, "Error updating button position", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isMoving) {
                        Log.d(TAG, "Finished dragging button. New position: x=${params.x}, y=${params.y}")
                    } else {
                        Log.i(TAG, "Button clicked (not moved)")
                        // Only handle click if it wasn't a move operation
                        handler.removeCallbacks(runnable)
                        handler.postDelayed(runnable, 100)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(floatingButton, params)
            Log.i(TAG, "Floating button added to window successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating button", e)
        }
    }

    private fun showOptionsPopup() {
        Log.i(TAG, "Showing options popup menu")
        val popupMenu = PopupMenu(this, floatingButton)
        popupMenu.menuInflater.inflate(R.menu.floating_button_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_close -> {
                    Log.i(TAG, "User selected 'Close' option from menu")
                    stopSelf()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun performLibraryClick() {
        Log.i(TAG, "performLibraryClick started")
        if (!isAccessibilityServiceRunning()) {
            Log.w(TAG, "Accessibility service not enabled")
            Toast.makeText(this, "Accessibility service not enabled", Toast.LENGTH_SHORT).show()
            // Show notification to help user enable the accessibility service
            showEnableAccessibilityNotification()
            //return
        }

        Log.i(TAG, "Check if Kindle is installed")
        // Check if Kindle is installed
        // Check if Kindle is installed or use test app in emulator
        val packageManager = packageManager
        try {
            val isEmulator = Build.FINGERPRINT.contains("generic") ||
                    Build.MODEL.contains("google_sdk")
            val targetPackage = if (isEmulator) {
                "com.android.settings" // Use settings app for testing on emulator
            } else {
                "com.amazon.kindle" // Use real Kindle app on physical devices
            }

            Log.d(TAG, "Trying to work with package: $targetPackage")

            packageManager.getPackageInfo(targetPackage, 0)

            // Check if app is running
            if (!isAppRunning(targetPackage)) {
                // Launch the app
                val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                launchIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                    Toast.makeText(this, "Opening $targetPackage...", Toast.LENGTH_SHORT).show()
                }
            } else {
                // App is already running, signal accessibility service
                Toast.makeText(this, "Navigating within $targetPackage...", Toast.LENGTH_SHORT).show()
                val intent = Intent("com.yourapp.ACTION_NAVIGATE_LIBRARY")
                sendBroadcast(intent)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Target app not installed", e)
            Toast.makeText(this, "Required app not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAppRunning(packageName: String): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val processes = activityManager.runningAppProcesses ?: return false
        return processes.any { it.processName == packageName }
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
            val isEnabled = serviceString?.contains("${packageName}/.KindleAccessibilityService") == true
            Log.d(TAG, "Accessibility service enabled: $isEnabled")
            return isEnabled
        }
        Log.d(TAG, "Accessibility services are not enabled on the device")
        return false
    }

    private fun showEnableAccessibilityNotification() {
        Log.i(TAG, "Showing accessibility notification")
        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "accessibility_channel",
                "Accessibility Service Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
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
        Log.i(TAG, "Accessibility notification displayed")
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy called, removing floating button")
        super.onDestroy()
        if (::floatingButton.isInitialized) {
            try {
                windowManager.removeView(floatingButton)
                Log.i(TAG, "Floating button removed successfully")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Error removing view", e)
            }
        }

        // Notify user that service has stopped
        Toast.makeText(this, "Floating button service stopped", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Floating service stopped")
    }
}