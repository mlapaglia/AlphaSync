package com.alphasync.sonycommand

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.HandlerThread
import android.util.Log
import com.alphasync.cameralink.MyCameraLinkEventListener
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.lang.Exception
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

class SonyCommandGenerator(callerContext: Context) {
    var isReporting: Boolean = false

    private val handlerThread: HandlerThread = HandlerThread("locationUpdateThread");

    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(callerContext)
    private var logTag: String = "SonyCommandGenerator"
    private var listeners : MutableSet<WeakReference<MyCameraLinkEventListener>> = mutableSetOf()
    private val locationRequest : LocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
        .setMaxUpdateDelayMillis(10000)
        .setIntervalMillis(10000)
        .setMinUpdateIntervalMillis(5000)
        .setGranularity(Granularity.GRANULARITY_FINE)
        .build()

    fun startLocationReporting(listener: MyCameraLinkEventListener) {
        if (listeners.map { it.get() }.contains(listener)) {
            return
        }
        listeners.add(WeakReference(listener))
        listeners.removeIf { it.get() == null }

        isReporting = true
        setupLocationListener()
    }

    fun stopLocationReporting(listener: MyCameraLinkEventListener) {
        isReporting = false
        teardownLocationListener()
        var toRemove: WeakReference<MyCameraLinkEventListener>? = null
        listeners.forEach {
            if (it.get() == listener) {
                toRemove = it
            }
        }
        toRemove?.let {
            listeners.remove(it)
        }
    }

    private fun generateBytes(location: Location) : ByteArray {
        val timeZoneId = TimeZone.getDefault().toZoneId()

        val paddingBytes = ByteArray(65)
        val fixedBytes = byteArrayOf(
            0x00, 0x5D, 0x08, 0x02, 0xFC.toByte(), 0x03, 0x00, 0x00, 0x10, 0x10, 0x10
        )

        val locationBytes = getConvertedCoordinates(location)
        val dateBytes = getConvertedDate(timeZoneId)
        val timeZoneOffsetBytes = getConvertedTimeZoneOffset(timeZoneId)
        val dstOffsetBytes = getConvertedDstOffsetBytes(timeZoneId)

        val combinedBytes = ByteArray(95)
        var currentBytePosition = 0

        System.arraycopy(fixedBytes, 0, combinedBytes, currentBytePosition, fixedBytes.size)
        currentBytePosition += fixedBytes.size
        System.arraycopy(locationBytes, 0, combinedBytes, currentBytePosition, locationBytes.size)
        currentBytePosition += locationBytes.size
        System.arraycopy(dateBytes, 0, combinedBytes, currentBytePosition, dateBytes.size)
        currentBytePosition += dateBytes.size
        System.arraycopy(paddingBytes, 0, combinedBytes, currentBytePosition, paddingBytes.size)
        currentBytePosition += paddingBytes.size
        System.arraycopy(timeZoneOffsetBytes, 0, combinedBytes, currentBytePosition, timeZoneOffsetBytes.size)
        currentBytePosition += timeZoneOffsetBytes.size
        System.arraycopy(dstOffsetBytes, 0, combinedBytes, currentBytePosition, dstOffsetBytes.size)

        return combinedBytes
    }

    private fun getConvertedDstOffsetBytes(timezoneId: ZoneId): ByteArray {
        val offsetDstMin = timezoneId.rules.getDaylightSavings(Instant.now()).seconds / 60
        return offsetDstMin.toShort().toByteArray()
    }

    private fun getConvertedTimeZoneOffset(timezoneId: ZoneId): ByteArray {
        val dt = LocalDateTime.now()
        val offsetMin = timezoneId.rules.getOffset(dt).totalSeconds / 60
        return offsetMin.toShort().toByteArray()
    }

    private fun getConvertedCoordinates(location: Location): ByteArray {
        val latitude = (location.latitude * 10000000).toInt()
        val latitudeBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(latitude).array()

        val longitude = (location.longitude * 10000000).toInt()
        val longitudeBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(longitude).array()

        return latitudeBytes + longitudeBytes
    }

    private fun getConvertedDate(timezoneId: ZoneId): ByteArray {
        val currentDateTime = LocalDateTime.now(timezoneId)
        val yearBytes = currentDateTime.year.toShort().toByteArray()

        return byteArrayOf(
            yearBytes[0], yearBytes[1],
            currentDateTime.monthValue.toByte(), currentDateTime.dayOfMonth.toByte(),
            currentDateTime.hour.toByte(), currentDateTime.minute.toByte(), currentDateTime.second.toByte()
        )
    }

    private fun Short.toByteArray(): ByteArray {
        return byteArrayOf((this.toInt() shr 8).toByte(), this.toByte())
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationListener() {
        if(!handlerThread.isAlive) {
            handlerThread.start()
        }

        isReporting = true
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, handlerThread.looper)
            .addOnFailureListener { exception: Exception->
                Log.d(logTag, "Location listener failed: ${exception.message}")
            }
    }

    @SuppressLint("MissingPermission")
    private fun teardownLocationListener() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val lastLocation = locationResult.lastLocation
            if(lastLocation != null) {
                val bytes = generateBytes(lastLocation)
                listeners.forEach { it.get()?.onLocationReady?.invoke(bytes)}
            } else {
                Log.d(logTag, "Received null location.")
            }
        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            super.onLocationAvailability(locationAvailability)
            if(locationAvailability.isLocationAvailable) {
                listeners.forEach { it.get()?.onGpsSignalFound?.invoke() }
            }
            else {
                listeners.forEach { it.get()?.onGpsSignalLost?.invoke() }
            }
        }
    }
}