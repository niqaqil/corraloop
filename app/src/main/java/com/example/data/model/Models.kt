package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val city: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Community(
    @DocumentId val id: String = "",
    val name: String = "",
    val city: String = "",
    val description: String = "",
    val createdBy: String = "",
    val memberCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Membership(
    @DocumentId val id: String = "",
    val userId: String = "",
    val communityId: String = "",
    val role: String = "member", // "member" or "organizer"
    @ServerTimestamp val joinedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RunSession(
    @DocumentId val id: String = "",
    val communityId: String = "",
    val title: String = "",
    val startsAt: Timestamp? = null,
    val meetingLat: Double = 0.0,
    val meetingLng: Double = 0.0,
    val meetingAddress: String = "",
    val distanceKm: Double = 5.0,
    val checkinOpensAt: Timestamp? = null,
    val checkinClosesAt: Timestamp? = null,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun getCheckinStatus(currentTimeMillis: Long = System.currentTimeMillis()): CheckinWindowStatus {
        val opens = checkinOpensAt?.toDate()?.time ?: return CheckinWindowStatus.Closed("No checkin window")
        val closes = checkinClosesAt?.toDate()?.time ?: return CheckinWindowStatus.Closed("No checkin window")

        return when {
            currentTimeMillis < opens -> {
                val diffMin = ((opens - currentTimeMillis) / 60000).coerceAtLeast(1)
                CheckinWindowStatus.Upcoming(diffMin)
            }
            currentTimeMillis in opens..closes -> {
                val remainingMin = ((closes - currentTimeMillis) / 60000).coerceAtLeast(1)
                CheckinWindowStatus.Open(remainingMin)
            }
            else -> CheckinWindowStatus.Closed("Window closed")
        }
    }
}

sealed interface CheckinWindowStatus {
    data class Upcoming(val opensInMinutes: Long) : CheckinWindowStatus
    data class Open(val closesInMinutes: Long) : CheckinWindowStatus
    data class Closed(val reason: String) : CheckinWindowStatus
}

@IgnoreExtraProperties
data class CheckinRecord(
    @DocumentId val id: String = "",
    val runId: String = "",
    val userId: String = "",
    val userName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracyM: Double = 0.0,
    val distanceToMeetingM: Double = 0.0
)

data class RunnerStats(
    val totalRuns: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val currentStreak: Int = 0,
    val lastRunDateFormatted: String = ""
)
