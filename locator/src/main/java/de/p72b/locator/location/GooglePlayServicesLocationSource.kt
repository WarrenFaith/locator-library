package de.p72b.locator.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.support.v4.content.PermissionChecker
import com.google.android.gms.location.*

internal class GooglePlayServicesLocationSource(
    activity: Activity,
    private val permissionManager: PermissionManager,
    private val settingsClientManager: SettingsClientManager,
    private val locationUpdatesListener: ILocationUpdatesListener?
) {

    val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(activity)
    var locationRequest: LocationRequest = LocationRequest()
    private val locationCallback: LocationCallback

    init {
        initLocationRequest()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                if (locationResult == null || locationUpdatesListener == null) {
                    return
                }
                for (location in locationResult.locations) {
                    locationUpdatesListener.onLocationChanged(location)
                }
            }
        }
    }

    fun getLastLocation(listener: ILastLocationListener, shouldRequestLocationPermission: Boolean,
                        shouldRequestSettingsChange: Boolean, hasInterestInLocationResult: Boolean = true) {
        if (permissionManager.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            checkSettings(listener, shouldRequestSettingsChange, hasInterestInLocationResult)
            return
        }

        if (!shouldRequestLocationPermission) {
            when (permissionManager.getPermissionStatus(Manifest.permission.ACCESS_FINE_LOCATION)) {
                PermissionChecker.PERMISSION_DENIED -> listener.onError(LocationManager.ERROR_MISSING_PERMISSION, "Location permission missing")
                PermissionChecker.PERMISSION_DENIED_APP_OP -> listener.onError(LocationManager.ERROR_MISSING_PERMISSION_DO_NOT_ASK_AGAIN, "Location permission missing, never ask me again")
                PermissionChecker.PERMISSION_GRANTED -> checkSettings(listener, shouldRequestSettingsChange, hasInterestInLocationResult)
            }
            return
        }

        permissionManager.hasPermissionIfNotRequest(Manifest.permission.ACCESS_FINE_LOCATION, object : IPermissionListener {
            override fun onDenied(donNotAskAgain: Boolean) {
                if (donNotAskAgain) {
                    listener.onError(LocationManager.ERROR_MISSING_PERMISSION_DO_NOT_ASK_AGAIN, "Location permission missing, never ask me again.")
                } else {
                    listener.onError(LocationManager.ERROR_CANCELED_PERMISSION_CHANGE, "Permission change request canceled by user.")
                }
            }

            override fun onGranted() {
                checkSettings(listener, shouldRequestSettingsChange, hasInterestInLocationResult)
            }
        })
    }

    private fun checkSettings(listener: ILastLocationListener, shouldRequestSettingsChange: Boolean, hasInterestInLocationResult: Boolean) {
        settingsClientManager.checkIfDeviceLocationSettingFulfillRequestRequirements(shouldRequestSettingsChange,
            locationRequest, object : ISettingsClientResultListener {
                override fun onSuccess() {
                    if (hasInterestInLocationResult) {
                        getLastFusedLocation(listener)
                    } else {
                        listener.onSuccess(null)
                    }
                }

                override fun onFailure(code: Int, message: String) {
                    listener.onError(code, message)
                }
            })
    }

    @SuppressLint("MissingPermission")
    private fun checkSettings(listener: ILocationUpdatesListener, shouldRequestSettingsChange: Boolean) {
        settingsClientManager.checkIfDeviceLocationSettingFulfillRequestRequirements(shouldRequestSettingsChange,
            locationRequest, object : ISettingsClientResultListener {
                override fun onSuccess() {
                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                }

                override fun onFailure(code: Int, message: String) {
                    listener.onLocationChangedError(code, message)
                }
            })
    }

    fun startReceivingLocationUpdates() {
        if (locationUpdatesListener == null) {
            return
        }
        when (permissionManager.getPermissionStatus(Manifest.permission.ACCESS_FINE_LOCATION)) {
            PermissionChecker.PERMISSION_DENIED -> locationUpdatesListener.onLocationChangedError(LocationManager.ERROR_MISSING_PERMISSION, "Location permission missing.")
            PermissionChecker.PERMISSION_DENIED_APP_OP -> locationUpdatesListener.onLocationChangedError(LocationManager.ERROR_MISSING_PERMISSION_DO_NOT_ASK_AGAIN, "Location permission missing, never ask me again.")
            PermissionChecker.PERMISSION_GRANTED -> checkSettings(locationUpdatesListener, false)
        }
    }

    fun stopReceivingLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    private fun getLastFusedLocation(listener: ILastLocationListener) {
        val getLastLocationTask = fusedLocationClient.lastLocation
        getLastLocationTask.addOnSuccessListener { location ->
            if (location != null) {
                listener.onSuccess(location)
            } else {
                fusedLocationClient.requestLocationUpdates(locationRequest, RetryLocationCallback(listener), null)
            }
        }
        getLastLocationTask.addOnFailureListener { e ->
            listener.onError(
                LocationManager.ERROR_FUSED_LOCATION_ERROR,
                e.message
            )
        }
    }

    private fun initLocationRequest() {
        locationRequest.interval = 10000
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    fun restartLocationUpdates() {
        stopReceivingLocationUpdates()
        startReceivingLocationUpdates()
    }

    private inner class RetryLocationCallback(private val listener: ILastLocationListener) : LocationCallback() {
        private var retry = 0

        override fun onLocationResult(locationResult: LocationResult?) {
            if (retry >= 5) {
                listener.onError(LocationManager.ERROR_LOCATION_UPDATES_RETRY_LIMIT, "Location updates retry limit reached. No location found.")
                fusedLocationClient.removeLocationUpdates(this)
                return
            }
            retry++
            if (locationResult == null) {
                return
            }
            if (locationResult.locations.isEmpty()) {
                return
            }
            listener.onSuccess(locationResult.locations[0])
            fusedLocationClient.removeLocationUpdates(this)
        }
    }
}
