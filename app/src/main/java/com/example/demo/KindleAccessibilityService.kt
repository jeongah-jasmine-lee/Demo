package com.example.demo

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class KindleAccessibilityService : AccessibilityService() {
    private val TAG = "KindleAccessibility"
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

    override fun onCreate() {
        super.onCreate()
        registerReceiver()
    }

    private fun registerReceiver() {
        val filter = IntentFilter("com.yourapp.ACTION_NAVIGATE_LIBRARY")
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                navigateToLibraryRequested = true
                lastNavigationAttempt = System.currentTimeMillis()

                // Set timeout to prevent stuck navigation requests
                navigationHandler.removeCallbacks(navigationTimeoutRunnable)
                navigationHandler.postDelayed(navigationTimeoutRunnable, LIBRARY_NAVIGATION_TIMEOUT)

                // Try navigation immediately if Kindle is already in focus
                tryNavigateToLibrary()
            }
        }, filter)
    }

    private fun tryNavigateToLibrary() {
        if (!navigateToLibraryRequested) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavigationAttempt > LIBRARY_NAVIGATION_TIMEOUT) {
            navigateToLibraryRequested = false
            return
        }

        val rootNode = rootInActiveWindow ?: return

        try {
            // Try to find different possible library buttons/tabs
            var libraryNode = findNodeByTextOrDescription(rootNode, "Library")

            if (libraryNode == null) {
                // Try alternative texts that might appear in different Kindle versions or localizations
                libraryNode = findNodeByTextOrDescription(rootNode, "My Library")
            }

            if (libraryNode == null) {
                libraryNode = findNodeByTextOrDescription(rootNode, "Books")
            }

            if (libraryNode != null) {
                val clickSuccess = libraryNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clickSuccess) {
                    Log.d(TAG, "Successfully clicked on Library")
                    navigateToLibraryRequested = false
                    navigationHandler.removeCallbacks(navigationTimeoutRunnable)
                } else {
                    Log.d(TAG, "Failed to click on Library")
                }
            } else {
                Log.d(TAG, "Library button not found")
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
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Only process if we're waiting for navigation
                    if (navigateToLibraryRequested) {
                        tryNavigateToLibrary()
                    }
                }
            }
        }
    }

    private fun findNodeByTextOrDescription(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        // Check text property
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }

        // Check content description
        if (node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
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
        Log.d(TAG, "Kindle Accessibility Service connected")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Kindle Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(null) // Unregister our broadcast receiver
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }
}
