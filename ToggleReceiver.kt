package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.TOGGLE_AUTO_CLICK") {
            val currentState = AutoClickService.isActive(context)
            AutoClickService.setIsActive(context, !currentState)
        }
    }
}
