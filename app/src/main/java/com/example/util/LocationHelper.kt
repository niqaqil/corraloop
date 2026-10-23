package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationCheckinResult(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val distanceToMeetingM: Double
)

sealed interface CheckinVerificationResult {
    data class Success(val checkinData: LocationCheckinResult) : CheckinVerificationResult
    data class AccuracyRejected(val accuracyM: Double, val maxAllowedM: Double = 50.0) : CheckinVerificationResult
    data class DistanceRejected(val distanceM: Double, val maxAllowedM: Double = 100.0) : CheckinVerificationResult
    data class Failure(val message: String) : CheckinVerificationResult
}

object LocationHelper {

    /**
     * Obtains single-shot device location with HIGH_ACCURACY.
     * Never performs continuous or background tracking.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationOnce(context: Context): Location? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()

        return try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).await()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Geodesic distance calculation in meters using the Haversine formula.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val earthRadiusM = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }

    /**
     * Validates runner's single-shot location against prompt constraints:
     * - Accuracy must be <= 50m
     * - Distance to meeting point must be <= 100m
     */
    fun verifyLocationForCheckin(
        location: Location,
        meetingLat: Double,
        meetingLng: Double
    ): CheckinVerificationResult {
        val accuracy = location.accuracy.toDouble()
        if (accuracy > 50.0) {
            return CheckinVerificationResult.AccuracyRejected(accuracy)
        }

        val distance = calculateDistanceMeters(
            location.latitude,
            location.longitude,
            meetingLat,
            meetingLng
        )

        if (distance > 100.0) {
            return CheckinVerificationResult.DistanceRejected(distance)
        }

        return CheckinVerificationResult.Success(
            LocationCheckinResult(
                lat = location.latitude,
                lng = location.longitude,
                accuracyM = accuracy,
                distanceToMeetingM = distance
            )
        )
    }
}
