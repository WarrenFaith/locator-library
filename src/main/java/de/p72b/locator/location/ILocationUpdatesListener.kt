package de.p72b.locator.location

import android.location.Location

interface ILocationUpdatesListener {
    fun onLocationChanged(location: Location)
}