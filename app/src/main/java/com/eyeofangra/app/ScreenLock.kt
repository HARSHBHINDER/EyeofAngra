package com.eyeofangra.app

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/// Device-admin receiver whose only power is force-lock (see res/xml/device_admin.xml).
/// Being an admin is what lets the app turn the screen off the instant recording
/// starts; the foreground service keeps capturing while it's locked.
class LockAdminReceiver : DeviceAdminReceiver()

object ScreenLock {
    private fun admin(context: Context) = ComponentName(context, LockAdminReceiver::class.java)
    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /// True once the user has granted device-admin in Settings.
    fun isEnabled(context: Context): Boolean = dpm(context).isAdminActive(admin(context))

    /// Lock the screen now — no-op if the user hasn't granted admin, so recording
    /// still works, it just won't auto-lock.
    fun lockNow(context: Context) {
        if (isEnabled(context)) dpm(context).lockNow()
    }

    /// Opens the system prompt where the user grants the force-lock admin. One-time.
    fun requestAdmin(activity: Activity) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(activity))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "EyeofAngra can lock the screen the moment recording starts, so the phone " +
                    "looks off while it keeps recording. You can turn this off any time."
            )
        activity.startActivity(intent)
    }

    /// Removes admin — used when the user turns the setting off.
    fun disable(context: Context) {
        if (isEnabled(context)) dpm(context).removeActiveAdmin(admin(context))
    }
}
