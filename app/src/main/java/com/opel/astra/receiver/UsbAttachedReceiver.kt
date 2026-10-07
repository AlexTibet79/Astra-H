package com.opel.astra.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import com.opel.astra.service.Elm327UsbService

class UsbAttachedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED || action == Intent.ACTION_POWER_CONNECTED) {
            val serviceIntent = Intent(context, Elm327UsbService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
