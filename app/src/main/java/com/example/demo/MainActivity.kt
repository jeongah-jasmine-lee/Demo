package com.example.demo

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    private val TAG = "KindleNav_MainActivity" // Tag for logging

    // Handler and Runnable to periodically check whether the accessibility service is enabled
    private val handler = Handler(Looper.getMainLooper())
    private val checkAccessibilityRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "Periodic check: isAccessibilityServiceEnabled() called")
            if (isAccessibilityServiceEnabled()) {
                Log.i(TAG, "Accessibility service is enabled, starting floating service")
                startFloatingService()
                // Stop further checks once service is started
                handler.removeCallbacks(this)
            } else {
                Log.d(TAG, "Accessibility service still not enabled. Check again in 100 ms.")
                handler.postDelayed(this, 100)
                Log.d(TAG, "Accessibility service re-check.")
                startFloatingService()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.i(TAG, "Application started")
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            // Request "Display over other apps" permission
            Log.i(TAG, "Requesting 'Display over other apps' permission")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        } else if (!isAccessibilityServiceEnabled()) {
            Log.i(TAG, "Overlay permission granted, requesting Accessibility Service activation")
            showAccessibilityInstructions()
        } else {
            Log.i(TAG, "All permissions granted, starting floating service")
            startFloatingService()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        Log.d(TAG, "isAccessibilityServiceEnabled started")
        val accessibilityManager =
            getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        Log.d(TAG, "Number of enabled services: ${enabledServices.size}")

        for (service in enabledServices) {
            val enabledServiceName = service.resolveInfo.serviceInfo.name
            val enabledServicePackage = service.resolveInfo.serviceInfo.packageName
            Log.d(TAG, "Enabled service: $enabledServicePackage/$enabledServiceName")
            // Check for exact package and service class name
            if (enabledServicePackage == packageName && enabledServiceName == "com.example.demo.KindleAccessibilityService") {
                Log.d(TAG, "Accessibility service detected!")
                return true
            }
        }
        Log.d(TAG, "Accessibility service not detected")
        return false
    }

    private fun showAccessibilityInstructions() {
        Log.i(TAG, "Showing Accessibility Service instructions dialog")
        AlertDialog.Builder(this)
            .setTitle("Accessibility Permission Required")
            .setMessage("This app needs accessibility permissions to interact with Kindle. Please enable the service in the following screen.")
            .setPositiveButton("Open Settings") { _, _ ->
                Log.i(TAG, "User clicked 'Open Settings' in Accessibility dialog")
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    Log.i(TAG, "Opening Accessibility Settings screen")
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening accessibility settings", e)
                    Toast.makeText(
                        this,
                        "Error opening settings: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.i(TAG, "User canceled Accessibility permission request")
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun startFloatingService() {
        Log.i(TAG, "Starting Floating Button Service")
        try {
            val serviceIntent = Intent(this, FloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.i(TAG, "Floating button service started successfully")
            Toast.makeText(this, "Floating button service started", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting floating service", e)
            Toast.makeText(
                this,
                "Error starting service: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Log.i(TAG, "Overlay permission granted from settings")
                if (!isAccessibilityServiceEnabled()) {
                    Log.i(TAG, "Now requesting Accessibility Service activation")
                    showAccessibilityInstructions()
                } else {
                    startFloatingService()
                }
            } else {
                Log.i(TAG, "Overlay permission denied")
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called, starting periodic accessibility check")
        // Start checking periodically whether the accessibility service is enabled
        handler.post(checkAccessibilityRunnable)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called, stopping periodic accessibility check")
        // Stop the periodic check when the activity is not visible
        handler.removeCallbacks(checkAccessibilityRunnable)
    }
}
