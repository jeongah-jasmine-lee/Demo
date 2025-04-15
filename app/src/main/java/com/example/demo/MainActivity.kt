package com.example.demo

import android.content.Intent
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import android.os.Build

class MainActivity : AppCompatActivity() {
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1234
    private val TAG = "KindleNav_MainActivity" // Tag for logging

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(TAG, "Application started")
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            // Request overlay permission
            Log.i(TAG, "Requesting 'Display over other apps' permission")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        } else if (!isAccessibilityServiceEnabled()) {
            Log.i(TAG, "Overlay permission granted, requesting Accessibility Service activation")
            showAccessibilityInstructions()
            startFloatingService()
        } else {
            Log.i(TAG, "All permissions granted, starting floating service")
            startFloatingService()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val accessibilityEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            )

            Log.d(TAG, "Accessibility enabled setting: $accessibilityEnabled")

            if (accessibilityEnabled == 1) {
                val serviceString = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )

                Log.d(TAG, "Enabled services: $serviceString")

                val expectedServiceName = "${packageName}/.KindleAccessibilityService"
                val isServiceEnabled = serviceString?.contains(expectedServiceName) == true

                Log.d(TAG, "Looking for service: $expectedServiceName, found: $isServiceEnabled")

                return isServiceEnabled
            }

            Log.d(TAG, "Accessibility services are not enabled on the device")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if accessibility service is enabled", e)
            return false
        }
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
                    Toast.makeText(this, "Error opening settings: ${e.message}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_LONG).show()
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
        Log.d(TAG, "onResume called")

        // Give the system a moment to register the accessibility service
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                Log.d(TAG, "Checking permissions after delay")
                // Check both permissions again
                if (Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "Overlay permission is granted")
                    if (isAccessibilityServiceEnabled()) {
                        Log.i(TAG, "Accessibility service is now enabled, starting floating service")
                        startFloatingService()
                    } else {
                        Log.d(TAG, "Accessibility service still not enabled")
                        // Only show the dialog if we're not coming back immediately from settings
                        // to avoid bothering the user too much
                        showAccessibilityInstructions()
                    }
                } else {
                    Log.d(TAG, "Overlay permission not granted")
                    checkAndRequestPermissions()
                }
            }
        }, 1000) // Short delay to let system update accessibility status
    }
}