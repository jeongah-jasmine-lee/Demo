package com.example.demo

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class KindleAccessibilityService : AccessibilityService() {
    private val TAG = "KindleNav_Accessibility"
    private val LIBRARY_NAVIGATION_TIMEOUT = 5000L // 5 seconds
    private var navigateToLibraryRequested = false
    private var lastNavigationAttempt = 0L
    private val navigationHandler = Handler(Looper.getMainLooper())
    private val navigationTimeoutRunnable = Runnable {
        // Clear navigation request if it timed out
        if (navigateToLibraryRequested) {
            navigateToLibraryRequested = false
            Log.d(TAG, "Navigation to library timed out")
        }
    }

    // Create the broadcast receiver as a field to properly unregister it later
    private val libraryNavigationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(TAG, "Received navigation broadcast")
            navigateToLibraryRequested = true
            lastNavigationAttempt = System.currentTimeMillis()

            // Set timeout to prevent stuck navigation requests
            navigationHandler.removeCallbacks(navigationTimeoutRunnable)
            navigationHandler.postDelayed(navigationTimeoutRunnable, LIBRARY_NAVIGATION_TIMEOUT)

            // Try navigation immediately if Kindle is already in focus
            tryNavigateToLibrary()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Accessibility Service onCreate")
        registerNavigationReceiver()
    }

    private fun registerNavigationReceiver() {
        try {
            Log.d(TAG, "Registering navigation broadcast receiver")
            val filter = IntentFilter("com.yourapp.ACTION_NAVIGATE_LIBRARY")

            if (Build.VERSION.SDK_INT >= 33) { // Android 13 (Tiramisu)
                // Android 13+ requires the RECEIVER_NOT_EXPORTED flag
                registerReceiver(
                    libraryNavigationReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                Log.d(TAG, "Error in registerNavigationReceiver")
            }

            Log.i(TAG, "Navigation broadcast receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering broadcast receiver", e)
        }
    }

    private fun tryNavigateToLibrary() {
        Log.d(TAG, "Attempting to navigate to Library")
        if (!navigateToLibraryRequested) {
            Log.d(TAG, "No navigation request active")
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavigationAttempt > LIBRARY_NAVIGATION_TIMEOUT) {
            Log.d(TAG, "Navigation request timed out")
            navigateToLibraryRequested = false
            return
        }

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.d(TAG, "No active window available")
            return
        }

        try {
            Log.d(TAG, "Searching for Library button/tab")
            // Try to find different possible library buttons/tabs
            var libraryNode = findNodeByTextOrDescription(rootNode, "Library")

            if (libraryNode == null) {
                // Try alternative texts that might appear in different Kindle versions or localizations
                Log.d(TAG, "Trying 'My Library' text")
                libraryNode = findNodeByTextOrDescription(rootNode, "My Library")
            }

            if (libraryNode == null) {
                Log.d(TAG, "Trying 'Books' text")
                libraryNode = findNodeByTextOrDescription(rootNode, "Books")
            }

            if (libraryNode != null) {
                Log.i(TAG, "Found Library button/tab, attempting to click")
                val clickSuccess = libraryNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clickSuccess) {
                    Log.i(TAG, "Successfully clicked on Library")
                    navigateToLibraryRequested = false
                    navigationHandler.removeCallbacks(navigationTimeoutRunnable)
                } else {
                    Log.w(TAG, "Failed to click on Library")
                }
            } else {
                Log.w(TAG, "Library button not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to library", e)
        } finally {
            rootNode.recycle()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        if (packageName.contains("com.amazon.kindle")) {
            Log.d(TAG, "Kindle app event: ${event.eventType}")
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Only process if we're waiting for navigation
                    if (navigateToLibraryRequested) {
                        Log.d(TAG, "Kindle UI changed, attempting navigation")
                        tryNavigateToLibrary()
                    }
                }
            }
        }
    }

    private fun findNodeByTextOrDescription(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        // Check text property
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            Log.d(TAG, "Found node with matching text: $text")
            return node
        }

        // Check content description
        if (node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
            Log.d(TAG, "Found node with matching description: $text")
            return node
        }

        // Check all child nodes recursively
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByTextOrDescription(child, text)
            if (result != null) {
                return result
            } else {
                child.recycle() // Important to recycle nodes we're not returning
            }
        }

        return null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Kindle Accessibility Service connected")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Kindle Accessibility Service interrupted")
    }

    override fun onDestroy() {
        Log.i(TAG, "Accessibility Service onDestroy")
        try {
            Log.d(TAG, "Unregistering broadcast receiver")
            unregisterReceiver(libraryNavigationReceiver)
            Log.i(TAG, "Broadcast receiver unregistered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        super.onDestroy()
    }
}