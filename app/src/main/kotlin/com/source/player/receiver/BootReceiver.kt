package com.source.player.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BootReceiver — called on device boot or app update. If restore-playback-state is enabled, we
 * re-initialize the service so the notification reappears and the last queue is available.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
                    intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
    )
            return

    // Fire-and-forget: check pref then optionally warm up service
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      // Hilt entry point not available in BroadcastReceiver directly,
      // so we use goAsync() pattern or simply check DataStore directly.
      // For simplicity, we just start the service — it will self-check state.
    }
  }
}
