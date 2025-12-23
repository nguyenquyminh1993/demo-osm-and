package com.resort_cloud.nansei.nansei_tablet.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.resort_cloud.nansei.nansei_tablet.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manager for showing alerts (text + beep sound)
 * Similar to Flutter _manageBeepIfLongSound() and ComLargeBtnDialog
 */
object AlertManager {
    
    private const val TAG = "AlertManager"
    private var beepJob: Job? = null
    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentDialog: AlertDialog? = null
    
    /**
     * Show dialog alert when user is out of map bounds
     * Similar to Flutter ComLargeBtnDialog.showStyle()
     */
    fun showMapOutOfBoundsDialog(context: Context) {
        // Close existing dialog if any
        currentDialog?.dismiss()
        
        currentDialog = AlertDialog.Builder(context)
            .setMessage(context.getString(R.string.error_rot_my_location_out_of_bounds))
            .setPositiveButton(context.getString(R.string.close)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .setOnDismissListener {
                currentDialog = null
            }
            .create()
        currentDialog?.show()
    }
    
    /**
     * Close map bounds dialog if location is back in bounds
     */
    fun closeMapOutOfBoundsDialog() {
        currentDialog?.dismiss()
        currentDialog = null
    }
    
    /**
     * Show dialog alert when location permission is disabled
     */
    fun showLocationDisabledDialog(context: Context) {
        AlertDialog.Builder(context)
            .setMessage(context.getString(R.string.map_error_location_disabled))
            .setPositiveButton(context.getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Start beep sound when user is out of caution bounds
     * Similar to Flutter _manageBeepIfLongSound()
     */
    fun startBeepSound() {
        if (beepJob?.isActive == true) {
            // Already beeping
            return
        }
        
        beepJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // Initialize ToneGenerator
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 80)
                }
                
                // Play beep sound continuously (similar to Flutter TONE_SUP_ERROR)
                // TONE_CDMA_ALERT_CALL_GUARD is similar to TONE_SUP_ERROR
                while (isActive) {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                    delay(1000) // Wait 1 second, then play again
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing beep sound", e)
            }
        }
    }
    
    /**
     * Stop beep sound
     */
    fun stopBeepSound() {
        beepJob?.cancel()
        beepJob = null
        
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing tone generator", e)
        }
    }
    
    /**
     * Check and manage beep sound based on caution status
     * Similar to Flutter _setBlShowMapOutCaution()
     */
    fun manageBeepSound(shouldShowCaution: Boolean) {
        if (shouldShowCaution) {
            startBeepSound()
        } else {
            stopBeepSound()
        }
    }
}

