package com.example

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

class AutoClickService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickService"
        private const val PREFS_NAME = "AutoClickPrefs"
        private const val PREF_IS_ACTIVE = "is_active"
        private const val PREF_TARGET_TEXT = "target_text"
        private const val PREF_TARGET_PACKAGE = "target_package"
        private const val PREF_MIN_FARE = "min_fare"
        private const val PREF_MAX_FARE = "max_fare"
        private const val PREF_NIGHT_MODE = "night_mode"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "auto_click_channel"

        fun getNightMode(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_NIGHT_MODE, false)
        }

        fun setNightMode(context: Context, active: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_NIGHT_MODE, active).apply()
        }

        fun getMinFare(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(PREF_MIN_FARE, 0)
        }

        fun setMinFare(context: Context, amount: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(PREF_MIN_FARE, amount).apply()
        }

        fun getMaxFare(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(PREF_MAX_FARE, 10000)
        }

        fun setMaxFare(context: Context, amount: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(PREF_MAX_FARE, amount).apply()
        }

        fun isActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_IS_ACTIVE, false)
        }

        fun setIsActive(context: Context, active: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_IS_ACTIVE, active).apply()
            updateNotification(context)
        }

        fun updateNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Auto Clicker Status",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }

            val isActive = isActive(context)
            
            val toggleIntent = Intent("com.example.TOGGLE_AUTO_CLICK")
            toggleIntent.setPackage(context.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val actionText = if (isActive) "Pause" else "Resume"
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Auto Clicker")
                .setContentText(if (isActive) "Active and monitoring" else "Paused")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(isActive)
                .addAction(android.R.drawable.ic_media_play, actionText, pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        fun getTargetText(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(PREF_TARGET_TEXT, null)
            return if (stored.isNullOrBlank()) "Accept\nConfirm\nMatch\nGo\nBook\nTake" else stored
        }

        fun setTargetText(context: Context, text: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_TARGET_TEXT, text).apply()
        }

        fun getTargetPackage(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(PREF_TARGET_PACKAGE, null)
            return if (stored.isNullOrBlank()) "ola\nrapido\nuber" else stored
        }

        fun setTargetPackage(context: Context, pkg: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_TARGET_PACKAGE, pkg).apply()
        }
    }

    private var cachedIsActive = false
    private var cachedTargetTexts = listOf<String>()
    private var cachedAllowedPackages = listOf<String>()
    private var cachedMinFare = 0
    private var cachedMaxFare = 10000
    private var cachedEnforceFare = false

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        updateCache() 
    }

    private fun updateCache() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cachedIsActive = prefs.getBoolean(PREF_IS_ACTIVE, false)
        val targetTextRaw = prefs.getString(PREF_TARGET_TEXT, null) ?: "accept"
        cachedTargetTexts = targetTextRaw.lowercase().split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        val targetPkgRaw = prefs.getString(PREF_TARGET_PACKAGE, null) ?: "ola\nrapido\nuber"
        cachedAllowedPackages = targetPkgRaw.lowercase().split(",", "\n", " ").map { it.trim() }.filter { it.isNotEmpty() }
        
        cachedMinFare = prefs.getInt(PREF_MIN_FARE, 0)
        cachedMaxFare = prefs.getInt(PREF_MAX_FARE, 10000)
        val isNightMode = prefs.getBoolean(PREF_NIGHT_MODE, false)
        cachedEnforceFare = !isNightMode && (cachedMinFare > 0 || cachedMaxFare < 10000)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
        updateCache()
        updateNotification(this)
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!cachedIsActive) return
        if (cachedTargetTexts.isEmpty()) return

        val allWindows = try {
            this.windows
        } catch (e: Exception) {
            null
        }

        if (!allWindows.isNullOrEmpty()) {
            for (window in allWindows) {
                val root = window.root ?: continue
                val currentPackage = root.packageName?.toString()?.lowercase() ?: ""
                
                val normalizedCurrent = currentPackage.replace(Regex("[^a-z0-9]"), "")
                val packageAllowed = cachedAllowedPackages.isEmpty() || cachedAllowedPackages.any { allowed ->
                    val normalizedAllowed = allowed.replace(Regex("[^a-z0-9]"), "")
                    normalizedCurrent.contains(normalizedAllowed) || normalizedAllowed.contains(normalizedCurrent)
                }

                if (!packageAllowed) continue
                
                if (findAndClick(root, cachedTargetTexts, cachedEnforceFare, cachedMinFare, cachedMaxFare)) {
                    return // Stop after successfully clicking
                }
            }
            
            // If normal node check failed across all windows, try OCR fallback
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                processScreenWithOCR(cachedTargetTexts, cachedEnforceFare, cachedMinFare, cachedMaxFare)
            }
        } else {
            // Fallback to active window just in case
            val root = rootInActiveWindow ?: return
            val currentPackage = root.packageName?.toString()?.lowercase() ?: ""
            
            val normalizedCurrent = currentPackage.replace(Regex("[^a-z0-9]"), "")
            val packageAllowed = cachedAllowedPackages.isEmpty() || cachedAllowedPackages.any { allowed ->
                val normalizedAllowed = allowed.replace(Regex("[^a-z0-9]"), "")
                normalizedCurrent.contains(normalizedAllowed) || normalizedAllowed.contains(normalizedCurrent)
            }

            if (!packageAllowed) return
            
            if (!findAndClick(root, cachedTargetTexts, cachedEnforceFare, cachedMinFare, cachedMaxFare)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    processScreenWithOCR(cachedTargetTexts, cachedEnforceFare, cachedMinFare, cachedMaxFare)
                }
            }
        }
    }

    private var lastOcrTime = 0L

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.R)
    private fun processScreenWithOCR(targetTexts: List<String>, enforceFare: Boolean, minFare: Int, maxFare: Int) {
        val now = System.currentTimeMillis()
        if (now - lastOcrTime < 500L) return
        lastOcrTime = now

        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val hwBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                        if (bitmap != null) {
                            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                            recognizer.process(image)
                                .addOnSuccessListener { visionText ->
                                    if (enforceFare) {
                                        val ocrTexts = mutableListOf<String>()
                                        for (block in visionText.textBlocks) {
                                            for (line in block.lines) {
                                                ocrTexts.add(line.text)
                                            }
                                        }
                                        val ocrFare = extractFareFromTexts(ocrTexts)
                                        if (ocrFare == null || ocrFare < minFare || ocrFare > maxFare) {
                                            Log.d(TAG, "OCR Fare $ocrFare is outside limits or not found, ignoring.")
                                            return@addOnSuccessListener
                                        }
                                    }

                                    for (block in visionText.textBlocks) {
                                        for (line in block.lines) {
                                            val text = line.text.lowercase()
                                            for (target in targetTexts) {
                                                if (text.contains(target)) {
                                                    val bbox = line.boundingBox
                                                    if (bbox != null) {
                                                        performTap(bbox.exactCenterX(), bbox.exactCenterY())
                                                        Log.d(TAG, "Successfully clicked '$target' using ML Kit OCR fallback")
                                                        return@addOnSuccessListener
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    hwBuffer.close()
                                }
                        } else {
                            hwBuffer.close()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "OCR Image Error: ${e.message}")
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Screenshot failed: $errorCode")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "OCR Screen fail: ${e.message}")
        }
    }

    private fun findAndClick(root: AccessibilityNodeInfo, targetTexts: List<String>, enforceFare: Boolean, minFare: Int, maxFare: Int): Boolean {

        val nodesToClick = mutableListOf<AccessibilityNodeInfo>()
        traverseAndFind(root, targetTexts, nodesToClick)
        
        if (nodesToClick.isEmpty()) return false
        
        if (enforceFare) {
            val fare = getFareFromScreen(root)
            if (fare == null) {
                Log.d(TAG, "Target found but fare enforcement is on and fare could not be parsed.")
                return false
            } else if (fare < minFare || fare > maxFare) {
                Log.d(TAG, "Fare $fare is outside limits ($minFare - $maxFare), ignoring.")
                return false
            }
        }

        var clickedAny = false
        for (node in nodesToClick) {
            var clicked = false
            var targetNode: AccessibilityNodeInfo? = node
            
            // Try standard accessibility click first
            while (targetNode != null) {
                if (targetNode.isClickable) {
                    if (targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        clicked = true
                        break
                    }
                }
                targetNode = targetNode.parent
            }
            
            if (!clicked) {
                // EXTREME HARD CLICK: Always dispatch gesture tap to the bounds center as a powerful fallback
                val clickRect = android.graphics.Rect()
                node.getBoundsInScreen(clickRect)
                val clickX = clickRect.centerX().toFloat()
                val clickY = clickRect.centerY().toFloat()
                
                if (clickX > 0 && clickY > 0) {
                    performTap(clickX, clickY)
                    clicked = true // Assume gesture dispatch was requested
                }
            }

            if (clicked) {
                Log.d(TAG, "Successfully clicked node for '$targetTexts'")
                clickedAny = true
                return true // Exit after 1 successful click to prevent infinite loop errors
            }
        }
        
        return clickedAny
    }

    private fun traverseAndFind(node: AccessibilityNodeInfo?, targetTexts: List<String>, result: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        
        for (target in targetTexts) {
            if ((nodeText.isNotEmpty() && nodeText.contains(target)) || 
                (nodeDesc.isNotEmpty() && nodeDesc.contains(target))) {
                result.add(node)
                break
            }
        }
        
        for (i in 0 until node.childCount) {
            traverseAndFind(node.getChild(i), targetTexts, result)
        }
    }

    private fun performTap(x: Float, y: Float) {
        val path = android.graphics.Path()
        path.moveTo(x, y)

        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 1) // 1ms stroke for ultra fast tap
            )
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun getFareFromScreen(root: AccessibilityNodeInfo): Double? {
        val texts = mutableListOf<String>()
        traverseAndExtractText(root, texts)
        return extractFareFromTexts(texts)
    }

    private fun extractFareFromTexts(texts: List<String>): Double? {
        val possibleFares = mutableListOf<Double>()
        val standaloneNumbers = mutableListOf<Double>()
        
        val prefixRegex = Regex("(?:₹|rs\\.?|inr|cash|pay|earn|amount|ride fare)\\s*[:-]?\\s*([0-9]+(?:\\.[0-9]{1,2})?)")
        val suffixRegex = Regex("([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:₹|rs\\.?|inr|cash|est|/-)")
        
        for (i in texts.indices) {
            val txt = texts[i]
            val lowerTxt = txt.lowercase().replace(",", "")
            
            // Extract from Prefix (e.g. ₹ 80, rs. 100)
            prefixRegex.findAll(lowerTxt).forEach { result ->
                val parsed = result.groupValues[1].toDoubleOrNull()
                if (parsed != null && parsed in 10.0..10000.0) {
                    possibleFares.add(parsed)
                }
            }
            
            // Extract from Suffix (e.g. 80 ₹, 80 rs, 80 est)
            suffixRegex.findAll(lowerTxt).forEach { result ->
                val parsed = result.groupValues[1].toDoubleOrNull()
                if (parsed != null && parsed in 10.0..10000.0) {
                    possibleFares.add(parsed)
                }
            }

            // Check adjacent nodes for split currency
            val cleaned = txt.trim().replace(",", "")
            val match = Regex("^([0-9]+(?:\\.[0-9]{1,2})?)$").matchEntire(cleaned)
            if (match != null) {
                val parsed = match.value.toDoubleOrNull()
                if (parsed != null && parsed in 10.0..10000.0) {
                    val prevTxt = if (i > 0) texts[i - 1].lowercase() else ""
                    val nextTxt = if (i < texts.size - 1) texts[i + 1].lowercase() else ""
                    val hasCurrencyNear = prevTxt.contains("₹") || prevTxt.contains("rs") || prevTxt.contains("inr") || prevTxt.contains("cash") || prevTxt.contains("pay") || prevTxt.contains("amount") ||
                                          nextTxt.contains("₹") || nextTxt.contains("rs") || nextTxt.contains("inr") || nextTxt.contains("cash") || nextTxt.contains("est")
                    
                    if (hasCurrencyNear) {
                        possibleFares.add(parsed)
                    } else if (parsed in 15.0..5000.0) {
                        standaloneNumbers.add(parsed)
                    }
                }
            }
        }
        
        if (possibleFares.isNotEmpty()) {
            return possibleFares.maxOrNull()
        }
        
        if (standaloneNumbers.isNotEmpty()) {
            if (standaloneNumbers.size == 1) {
                return standaloneNumbers.first()
            }
            val reasonableFares = standaloneNumbers.filter { it >= 30.0 }
            if (reasonableFares.isNotEmpty()) {
                // Since standalone numbers could be time (e.g. 45 mins) or fare (e.g. 150),
                // the fare is almost always the largest completely isolated number on a request card.
                return reasonableFares.maxOrNull()
            }
        }
        
        return null
    }

    private fun traverseAndExtractText(node: AccessibilityNodeInfo?, result: MutableList<String>) {
        if (node == null) return
        val nodeText = node.text?.toString()?.trim() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.trim() ?: ""
        if (nodeText.isNotEmpty()) result.add(nodeText)
        if (nodeDesc.isNotEmpty() && nodeDesc != nodeText) result.add(nodeDesc)
        
        for (i in 0 until node.childCount) {
            traverseAndExtractText(node.getChild(i), result)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }
}
