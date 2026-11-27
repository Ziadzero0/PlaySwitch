package com.playswitch.ziadzero.root

import android.annotation.SuppressLint
import android.util.Log

/**
 * Prevents Android 15 (API 35) SQLiteCompatibilityWalFlags.initIfNeeded() from querying
 * Settings.Global via ContentResolver inside an unattached root app_process daemon, which
 * otherwise throws SecurityException ("Unable to find app for caller ... when getting content provider settings").
 */
object SqliteCompatFixer {
    private const val TAG = "SqliteCompatFixer"

    @Volatile
    private var applied = false

    @SuppressLint("PrivateApi", "SoonBlockedPrivateApi", "BlockedPrivateApi", "DiscouragedPrivateApi")
    fun apply() {
        if (applied) return
        synchronized(this) {
            if (applied) return
            runCatching {
                val clazz = Class.forName("android.database.sqlite.SQLiteCompatibilityWalFlags")
                runCatching {
                    val initMethod = clazz.getDeclaredMethod("init", String::class.java)
                    initMethod.isAccessible = true
                    initMethod.invoke(null, "")
                }.onFailure {
                    val field = clazz.getDeclaredField("sInitialized")
                    field.isAccessible = true
                    field.set(null, true)
                }
                Log.d(TAG, "Sanitized SQLiteCompatibilityWalFlags for root process")
            }.onFailure { err ->
                Log.w(TAG, "Failed to sanitize SQLiteCompatibilityWalFlags: ${err.message}")
            }
            applied = true
        }
    }
}
