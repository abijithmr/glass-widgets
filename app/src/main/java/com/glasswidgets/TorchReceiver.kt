package com.glasswidgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** Flips the phone's torch. No permission is needed to use the torch. */
class TorchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val cm = app.getSystemService(CameraManager::class.java) ?: return
        val id = flashCamera(cm) ?: return
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        var done = false

        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (done || cameraId != id) return
                done = true
                cm.unregisterTorchCallback(this)
                try {
                    cm.setTorchMode(id, !enabled)
                    setOn(app, !enabled)
                } catch (e: Exception) {
                    android.util.Log.e("GlassWidgets", "Torch failed", e)
                }
                Glass.refresh(app, GlassControlCenterWidget::class.java)
                pending.finish()
            }
        }
        cm.registerTorchCallback(callback, handler)
        // Never keep the broadcast open if Android doesn't answer
        handler.postDelayed({
            if (!done) {
                done = true
                cm.unregisterTorchCallback(callback)
                pending.finish()
            }
        }, 2000)
    }

    private fun flashCamera(cm: CameraManager): String? = try {
        cm.cameraIdList.firstOrNull {
            val c = cm.getCameraCharacteristics(it)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val KEY = "torch_on"

        fun isOn(context: Context): Boolean = Glass.prefs(context).getBoolean(KEY, false)

        private fun setOn(context: Context, on: Boolean) {
            Glass.prefs(context).edit().putBoolean(KEY, on).apply()
        }
    }
}
