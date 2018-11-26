package de.p72b.locator.location

import android.app.Activity
import android.content.Intent
import android.content.IntentSender

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.tasks.OnFailureListener

import java.util.ArrayList

internal class SettingsClientManager(private val activity: Activity) {

    companion object {
        const val REQUEST_CODE_SETTINGS = 30
    }

    private val pendingListenerList = ArrayList<ISettingsClientResultListener>()

    fun checkIfDeviceLocationSettingFulfillRequestRequirements(
            shouldRequestSettingsChange: Boolean, locationRequest: LocationRequest,
            listener: ISettingsClientResultListener) {
        val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(activity)
        val task = client.checkLocationSettings(builder.build())
        task.addOnFailureListener(activity, OnFailureListener { e ->
            val statusCode = (e as ApiException).statusCode
            if (statusCode != LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                listener.onFailure("Settings resolution is not fulfilled")
                return@OnFailureListener
            }

            if (!shouldRequestSettingsChange) {
                listener.onFailure("Location settings aren't met")
                return@OnFailureListener
            }

            // Location settings are not satisfied, but this can be fixed by showing the user a dialog
            try {
                pendingListenerList.add(listener)
                // Show the dialog by calling startResolutionForResult(), and check the result in onActivityResult()
                val resolvable = e as ResolvableApiException
                resolvable.startResolutionForResult(activity, REQUEST_CODE_SETTINGS)
            } catch (sendEx: IntentSender.SendIntentException) {
                // Ignore the error
                listener.onFailure("Send Intent to change location settings failed")
            }
        })
        task.addOnSuccessListener(activity) { listener.onSuccess() }
    }

    fun onActivityResult(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> callPendingListener(true)
            Activity.RESULT_CANCELED -> callPendingListener(false)
        }// do nothing
    }

    private fun callPendingListener(isSucceeded: Boolean) {
        for (listener in pendingListenerList) {
            if (isSucceeded) {
                listener.onSuccess()
            } else {
                listener.onFailure("Settings change request canceled by user")
            }
        }
        pendingListenerList.clear()
    }
}
