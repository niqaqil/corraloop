package com.example.data.demo

import com.example.data.model.CheckinRecord
import com.example.data.model.Community
import com.example.data.model.Membership
import com.example.data.model.RunSession
import com.example.data.model.RunnerStats
import com.example.data.model.UserProfile
import com.example.util.LocationCheckinResult
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID

enum class DemoPersona(
    val userId: String,
    val displayName: String,
    val city: String,
    val roleTitle: String
) {
    ORGANIZER_ELENA(
        userId = "demo_organizer_elena",
        displayName = "Elena Vance",
        city = "San Francisco",
        roleTitle = "Club Organizer"
    ),
    RUNNER_MARCUS(
        userId = "demo_runner_marcus",
        displayName = "Marcus Reed",
        city = "San Francisco",
        roleTitle = "Active Runner"
    )
}

object DemoRepository {

    private val now = System.currentTimeMillis()

    private val _communities = MutableStateFlow<List<Community>>(
        listOf(
            Community(
                id = "demo_comm_marina",
                name = "Marina Sunrise Runners",
                city = "San Francisco",
                description = "Morning miles along the SF Bay with coffee afterwards. All paces welcome!",
                createdBy = DemoPersona.ORGANIZER_ELENA.userId,
                memberCount = 42L,
                createdAt = Timestamp(Date(now - 30L * 86400000L))
            ),
            Community(
                id = "demo_comm_central_park",
                name = "Central Park Strides",
                city = "New York",
                description = "Classic reservoir loops and weekly Saturday long runs through Manhattan.",
                createdBy = "organizer_nyc_sarah",
                memberCount = 118L,
                createdAt = Timestamp(Date(now - 60L * 86400000L))
            ),
            Community(
                id = "demo_comm_austin",
                name = "Austin Trail Blazers",
                city = "Austin",
                description = "Scenic shaded trail runs along Lady Bird Lake with post-run Barton Springs dips.",
                createdBy = "organizer_atx_liam",
                memberCount = 64L,
                createdAt = Timestamp(Date(now - 45L * 86400000L))
            )
        )
    )

    private val _memberships = MutableStateFlow<List<Membership>>(
        listOf(
            Membership(
                id = "demo_comm_marina_${DemoPersona.ORGANIZER_ELENA.userId}",
                userId = DemoPersona.ORGANIZER_ELENA.userId,
                communityId = "demo_comm_marina",
                role = "organizer",
                joinedAt = Timestamp(Date(now - 30L * 86400000L))
            ),
            Membership(
                id = "demo_comm_marina_${DemoPersona.RUNNER_MARCUS.userId}",
                userId = DemoPersona.RUNNER_MARCUS.userId,
                communityId = "demo_comm_marina",
                role = "member",
                joinedAt = Timestamp(Date(now - 20L * 86400000L))
            ),
            Membership(
                id = "demo_comm_austin_${DemoPersona.RUNNER_MARCUS.userId}",
                userId = DemoPersona.RUNNER_MARCUS.userId,
                communityId = "demo_comm_austin",
                role = "member",
                joinedAt = Timestamp(Date(now - 10L * 86400000L))
            )
        )
    )

    // Run 1: Starts in 10 minutes -> Checkin opened 20m ago, closes in 25m (CURRENTLY OPEN!)
    private val runOpenDate = Date(now + 10 * 60 * 1000L)
    // Run 2: Starts in 90 minutes -> Checkin opens in 60m (UPCOMING)
    private val runUpcomingDate = Date(now + 90 * 60 * 1000L)
    // Run 3: Yesterday -> (CLOSED)
    private val runPastDate = Date(now - 24 * 60 * 60 * 1000L)

    private val _runs = MutableStateFlow<List<RunSession>>(
        listOf(
            RunSession(
                id = "demo_run_embarcadero",
                communityId = "demo_comm_marina",
                title = "Embarcadero Sunrise 5K",
                startsAt = Timestamp(runOpenDate),
                meetingLat = 37.7955,
                meetingLng = -122.3937,
                meetingAddress = "Ferry Building Plaza, San Francisco",
                distanceKm = 5.0,
                checkinOpensAt = Timestamp(Date(runOpenDate.time - 30 * 60 * 1000L)),
                checkinClosesAt = Timestamp(Date(runOpenDate.time + 15 * 60 * 1000L)),
                createdBy = DemoPersona.ORGANIZER_ELENA.userId,
                createdAt = Timestamp(Date(now - 86400000L))
            ),
            RunSession(
                id = "demo_run_presidio",
                communityId = "demo_comm_marina",
                title = "Presidio Coastal 10K",
                startsAt = Timestamp(runUpcomingDate),
                meetingLat = 37.8055,
                meetingLng = -122.4418,
                meetingAddress = "Crissy Field Warming Hut, San Francisco",
                distanceKm = 10.0,
                checkinOpensAt = Timestamp(Date(runUpcomingDate.time - 30 * 60 * 1000L)),
                checkinClosesAt = Timestamp(Date(runUpcomingDate.time + 15 * 60 * 1000L)),
                createdBy = DemoPersona.ORGANIZER_ELENA.userId,
                createdAt = Timestamp(Date(now - 86400000L))
            ),
            RunSession(
                id = "demo_run_twin_peaks",
                communityId = "demo_comm_marina",
                title = "Twin Peaks Sunset Hill Repeat",
                startsAt = Timestamp(runPastDate),
                meetingLat = 37.7544,
                meetingLng = -122.4477,
                meetingAddress = "Twin Peaks Overlook, San Francisco",
                distanceKm = 6.0,
                checkinOpensAt = Timestamp(Date(runPastDate.time - 30 * 60 * 1000L)),
                checkinClosesAt = Timestamp(Date(runPastDate.time + 15 * 60 * 1000L)),
                createdBy = DemoPersona.ORGANIZER_ELENA.userId,
                createdAt = Timestamp(Date(now - 2L * 86400000L))
            )
        )
    )

    private val _checkins = MutableStateFlow<List<CheckinRecord>>(
        listOf(
            // Pre-checked-in runners for Embarcadero Sunrise (open run)
            CheckinRecord(
                id = "demo_run_embarcadero_runner_sarah",
                runId = "demo_run_embarcadero",
                userId = "user_sarah_1",
                userName = "Sarah Jenkins",
                createdAt = Timestamp(Date(now - 5 * 60 * 1000L)),
                lat = 37.7956,
                lng = -122.3938,
                accuracyM = 12.0,
                distanceToMeetingM = 18.0
            ),
            CheckinRecord(
                id = "demo_run_embarcadero_runner_david",
                runId = "demo_run_embarcadero",
                userId = "user_david_2",
                userName = "David Kim",
                createdAt = Timestamp(Date(now - 2 * 60 * 1000L)),
                lat = 37.7953,
                lng = -122.3935,
                accuracyM = 22.0,
                distanceToMeetingM = 35.0
            ),
            // Past run check-ins (includes Marcus Reed for history & streak)
            CheckinRecord(
                id = "demo_run_twin_peaks_${DemoPersona.RUNNER_MARCUS.userId}",
                runId = "demo_run_twin_peaks",
                userId = DemoPersona.RUNNER_MARCUS.userId,
                userName = DemoPersona.RUNNER_MARCUS.displayName,
                createdAt = Timestamp(Date(now - 24 * 3600000L)),
                lat = 37.7545,
                lng = -122.4476,
                accuracyM = 14.0,
                distanceToMeetingM = 24.0
            ),
            CheckinRecord(
                id = "past_run_1_${DemoPersona.RUNNER_MARCUS.userId}",
                runId = "past_run_1",
                userId = DemoPersona.RUNNER_MARCUS.userId,
                userName = DemoPersona.RUNNER_MARCUS.displayName,
                createdAt = Timestamp(Date(now - 2L * 86400000L)),
                lat = 37.7955,
                lng = -122.3937,
                accuracyM = 9.0,
                distanceToMeetingM = 15.0
            ),
            CheckinRecord(
                id = "past_run_2_${DemoPersona.RUNNER_MARCUS.userId}",
                runId = "past_run_2",
                userId = DemoPersona.RUNNER_MARCUS.userId,
                userName = DemoPersona.RUNNER_MARCUS.displayName,
                createdAt = Timestamp(Date(now - 3L * 86400000L)),
                lat = 37.8055,
                lng = -122.4418,
                accuracyM = 18.0,
                distanceToMeetingM = 32.0
            ),
            CheckinRecord(
                id = "past_run_3_${DemoPersona.RUNNER_MARCUS.userId}",
                runId = "past_run_3",
                userId = DemoPersona.RUNNER_MARCUS.userId,
                userName = DemoPersona.RUNNER_MARCUS.displayName,
                createdAt = Timestamp(Date(now - 4L * 86400000L)),
                lat = 37.7749,
                lng = -122.4194,
                accuracyM = 15.0,
                distanceToMeetingM = 40.0
            ),
            CheckinRecord(
                id = "past_run_4_${DemoPersona.RUNNER_MARCUS.userId}",
                runId = "past_run_4",
                userId = DemoPersona.RUNNER_MARCUS.userId,
                userName = DemoPersona.RUNNER_MARCUS.displayName,
                createdAt = Timestamp(Date(now - 5L * 86400000L)),
                lat = 37.7690,
                lng = -122.4467,
                accuracyM = 12.0,
                distanceToMeetingM = 22.0
            )
        )
    )

    // Flow getters
    fun observeCommunities(): Flow<List<Community>> = _communities.asStateFlow()

    fun observeMembershipsForUser(userId: String): Flow<List<Membership>> =
        _memberships.map { list -> list.filter { it.userId == userId } }

    fun observeAllRuns(): Flow<List<RunSession>> = _runs.asStateFlow()

    fun observeCheckinsForRun(runId: String): Flow<List<CheckinRecord>> =
        _checkins.map { list -> list.filter { it.runId == runId } }

    fun observeUserCheckins(userId: String): Flow<List<CheckinRecord>> =
        _checkins.map { list -> list.filter { it.userId == userId } }

    fun getProfile(persona: DemoPersona): UserProfile =
        UserProfile(
            userId = persona.userId,
            name = persona.displayName,
            city = persona.city,
            createdAt = Timestamp(Date(now - 30L * 86400000L))
        )

    // Interactive operations in Demo Mode
    fun joinCommunity(userId: String, communityId: String) {
        val exists = _memberships.value.any { it.userId == userId && it.communityId == communityId }
        if (!exists) {
            val newMembership = Membership(
                id = "${communityId}_$userId",
                userId = userId,
                communityId = communityId,
                role = "member",
                joinedAt = Timestamp.now()
            )
            _memberships.value = _memberships.value + newMembership
            _communities.value = _communities.value.map {
                if (it.id == communityId) it.copy(memberCount = it.memberCount + 1) else it
            }
        }
    }

    fun leaveCommunity(userId: String, communityId: String) {
        _memberships.value = _memberships.value.filterNot { it.userId == userId && it.communityId == communityId }
        _communities.value = _communities.value.map {
            if (it.id == communityId) it.copy(memberCount = (it.memberCount - 1).coerceAtLeast(1)) else it
        }
    }

    fun createCommunity(userId: String, name: String, city: String, description: String) {
        val newId = "demo_comm_${UUID.randomUUID().toString().take(6)}"
        val newCommunity = Community(
            id = newId,
            name = name,
            city = city,
            description = description,
            createdBy = userId,
            memberCount = 1L,
            createdAt = Timestamp.now()
        )
        val newMembership = Membership(
            id = "${newId}_$userId",
            userId = userId,
            communityId = newId,
            role = "organizer",
            joinedAt = Timestamp.now()
        )
        _communities.value = listOf(newCommunity) + _communities.value
        _memberships.value = _memberships.value + newMembership
    }

    fun createRun(
        userId: String,
        communityId: String,
        title: String,
        startsAtDate: Date,
        meetingLat: Double,
        meetingLng: Double,
        meetingAddress: String,
        distanceKm: Double
    ) {
        val newId = "demo_run_${UUID.randomUUID().toString().take(6)}"
        val startsAt = Timestamp(startsAtDate)
        val checkinOpensAt = Timestamp(Date(startsAtDate.time - 30 * 60 * 1000L))
        val checkinClosesAt = Timestamp(Date(startsAtDate.time + 15 * 60 * 1000L))

        val newRun = RunSession(
            id = newId,
            communityId = communityId,
            title = title,
            startsAt = startsAt,
            meetingLat = meetingLat,
            meetingLng = meetingLng,
            meetingAddress = meetingAddress,
            distanceKm = distanceKm,
            checkinOpensAt = checkinOpensAt,
            checkinClosesAt = checkinClosesAt,
            createdBy = userId,
            createdAt = Timestamp.now()
        )
        _runs.value = listOf(newRun) + _runs.value
    }

    fun performCheckin(
        run: RunSession,
        userId: String,
        userName: String,
        checkinData: LocationCheckinResult
    ) {
        val checkinId = "${run.id}_$userId"
        val record = CheckinRecord(
            id = checkinId,
            runId = run.id,
            userId = userId,
            userName = userName,
            createdAt = Timestamp.now(),
            lat = checkinData.lat,
            lng = checkinData.lng,
            accuracyM = checkinData.accuracyM,
            distanceToMeetingM = checkinData.distanceToMeetingM
        )
        _checkins.value = listOf(record) + _checkins.value.filterNot { it.id == checkinId }
    }

    fun computeRunnerStats(checkins: List<CheckinRecord>, runs: Map<String, RunSession>): RunnerStats {
        val totalRuns = checkins.size
        var totalDistanceKm = 0.0
        for (checkin in checkins) {
            val run = runs[checkin.runId]
            totalDistanceKm += (run?.distanceKm ?: 5.0)
        }

        val distinctDays = checkins.mapNotNull { it.createdAt?.toDate() }
            .map { "${it.year}-${it.month}-${it.date}" }
            .distinct()
            .size

        return RunnerStats(
            totalRuns = totalRuns,
            totalDistanceKm = totalDistanceKm,
            currentStreak = distinctDays.coerceAtLeast(1),
            lastRunDateFormatted = checkins.firstOrNull()?.createdAt?.toDate()?.toString() ?: "None yet"
        )
    }
}
