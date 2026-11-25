package com.playswitch.ziadzero

import android.app.Application
import com.topjohnwu.superuser.Shell

class PlaySwitchApp : Application() {
    companion object {
        init {
            Shell.enableVerboseLogging = false
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(15)
            )
        }
    }
}
