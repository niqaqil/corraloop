package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.CheckinRecord
import com.example.data.model.Community
import com.example.data.model.Membership
import com.example.data.model.RunSession
import com.example.data.model.RunnerStats
import com.example.data.model.UserProfile
import com.example.util.LocationCheckinResult
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException

class CorraloopRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Corraloop.")
    }

    fun currentUserId(): String? = auth.currentUser?.uid

    // --- PROFILES ---

    fun observeProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val path = "profiles/$userId"
        val registration = db.collection("profiles").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.toObject(UserProfile::class.java))
                } else {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveProfile(name: String, city: String) {
        val uid = requireUserId()
        val docRef = db.collection("profiles").document(uid)
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim(),
            "city" to city.trim(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- COMMUNITIES ---

    fun observeCommunities(): Flow<List<Community>> = flow {
        val path = "communities"
        emitAll(
            db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Community::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    emit(emptyList())
                }
        )
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<T>.emitAll(flow: Flow<T>) {
        flow.collect { value -> emit(value) }
    }

    suspend fun createCommunity(
        name: String,
        city: String,
        description: String
    ): Result<String> {
        val uid = requireUserId()
        val communitiesRef = db.collection("communities")
        val newDoc = communitiesRef.document()
        val communityPayload = mapOf(
            "name" to name.trim(),
            "city" to city.trim(),
            "description" to description.trim(),
            "createdBy" to uid,
            "memberCount" to 1L,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val membershipDoc = db.collection("memberships").document("${newDoc.id}_$uid")
        val membershipPayload = mapOf(
            "userId" to uid,
            "communityId" to newDoc.id,
            "role" to "organizer",
            "joinedAt" to FieldValue.serverTimestamp()
        )

        return try {
            db.runBatch { batch ->
                batch.set(newDoc, communityPayload)
                batch.set(membershipDoc, membershipPayload)
            }.await()
            Result.success(newDoc.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, communitiesRef.path)
            Result.failure(e)
        }
    }

    // --- MEMBERSHIPS ---

    fun observeMembershipsForUser(userId: String): Flow<List<Membership>> = flow {
        val path = "memberships"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Membership::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    emit(emptyList())
                }
        )
    }

    suspend fun joinCommunity(communityId: String): Result<Unit> {
        val uid = requireUserId()
        val membershipId = "${communityId}_$uid"
        val docRef = db.collection("memberships").document(membershipId)
        val communityRef = db.collection("communities").document(communityId)

        val payload = mapOf(
            "userId" to uid,
            "communityId" to communityId,
            "role" to "member",
            "joinedAt" to FieldValue.serverTimestamp()
        )

        return try {
            db.runBatch { batch ->
                batch.set(docRef, payload)
                batch.update(communityRef, "memberCount", FieldValue.increment(1))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun leaveCommunity(communityId: String): Result<Unit> {
        val uid = requireUserId()
        val membershipId = "${communityId}_$uid"
        val docRef = db.collection("memberships").document(membershipId)
        val communityRef = db.collection("communities").document(communityId)

        return try {
            db.runBatch { batch ->
                batch.delete(docRef)
                batch.update(communityRef, "memberCount", FieldValue.increment(-1))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- RUNS ---

    fun observeRuns(communityId: String): Flow<List<RunSession>> = flow {
        val path = "runs"
        emitAll(
            db.collection(path)
                .whereEqualTo("communityId", communityId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(RunSession::class.java).sortedBy { it.startsAt }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    emit(emptyList())
                }
        )
    }

    fun observeAllRuns(): Flow<List<RunSession>> = flow {
        val path = "runs"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(RunSession::class.java).sortedBy { it.startsAt }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    emit(emptyList())
                }
        )
    }

    fun observeRun(runId: String): Flow<RunSession?> = callbackFlow {
        val path = "runs/$runId"
        val registration = db.collection("runs").document(runId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(RunSession::class.java))
            }
        awaitClose { registration.remove() }
    }

    suspend fun createRun(
        communityId: String,
        title: String,
        startsAtDate: Date,
        meetingLat: Double,
        meetingLng: Double,
        meetingAddress: String,
        distanceKm: Double
    ): Result<String> {
        val uid = requireUserId()
        val runsRef = db.collection("runs")
        val newDoc = runsRef.document()

        val startsAtMillis = startsAtDate.time
        // checkinOpensAt = 30 min before start
        val checkinOpensAt = Timestamp(Date(startsAtMillis - 30 * 60 * 1000L))
        // checkinClosesAt = 15 min after start
        val checkinClosesAt = Timestamp(Date(startsAtMillis + 15 * 60 * 1000L))

        val payload = mapOf(
            "communityId" to communityId,
            "title" to title.trim(),
            "startsAt" to Timestamp(startsAtDate),
            "meetingLat" to meetingLat,
            "meetingLng" to meetingLng,
            "meetingAddress" to meetingAddress.trim(),
            "distanceKm" to distanceKm,
            "checkinOpensAt" to checkinOpensAt,
            "checkinClosesAt" to checkinClosesAt,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            newDoc.set(payload).await()
            Result.success(newDoc.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, runsRef.path)
            Result.failure(e)
        }
    }

    // --- CHECKINS ---

    fun observeCheckinsForRun(runId: String): Flow<List<CheckinRecord>> = flow {
        val path = "checkins"
        emitAll(
            db.collection(path)
                .whereEqualTo("runId", runId)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(CheckinRecord::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    emit(emptyList())
                }
        )
    }

    fun observeUserCheckins(userId: String): Flow<List<CheckinRecord>> = flow {
        val path = "checkins"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CheckinRecord::class.java).sortedByDescending { it.createdAt }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    emit(emptyList())
                }
        )
    }

    suspend fun performCheckin(
        run: RunSession,
        checkinData: LocationCheckinResult,
        userName: String
    ): Result<Unit> {
        val uid = requireUserId()
        val checkinId = "${run.id}_$uid"
        val docRef = db.collection("checkins").document(checkinId)

        // Strict server window validation:
        val opens = run.checkinOpensAt?.toDate()?.time
        val closes = run.checkinClosesAt?.toDate()?.time
        val now = System.currentTimeMillis()

        if (opens != null && closes != null) {
            if (now < opens) {
                return Result.failure(IllegalStateException("Check-in window is not yet open (opens 30m before run)."))
            }
            if (now > closes) {
                return Result.failure(IllegalStateException("Check-in window has closed (closed 15m after start)."))
            }
        }

        // Accuracy check: reject if > 50m
        if (checkinData.accuracyM > 50.0) {
            return Result.failure(IllegalStateException("Location accuracy (${checkinData.accuracyM.toInt()}m) is insufficient. Must be under 50m."))
        }

        // Distance check: reject if > 100m
        if (checkinData.distanceToMeetingM > 100.0) {
            return Result.failure(IllegalStateException("You are ${checkinData.distanceToMeetingM.toInt()}m away from the meeting point. You must be within 100m."))
        }

        val payload = mapOf(
            "runId" to run.id,
            "userId" to uid,
            "userName" to userName.ifBlank { "Runner" },
            "lat" to checkinData.lat,
            "lng" to checkinData.lng,
            "accuracyM" to checkinData.accuracyM,
            "distanceToMeetingM" to checkinData.distanceToMeetingM,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun computeRunnerStats(checkins: List<CheckinRecord>, runs: Map<String, RunSession>): RunnerStats {
        val totalRuns = checkins.size
        var totalDistanceKm = 0.0
        for (checkin in checkins) {
            val run = runs[checkin.runId]
            totalDistanceKm += (run?.distanceKm ?: 5.0)
        }

        // Calculate running streak (active runs grouped by day or week)
        val streak = if (totalRuns > 0) {
            val distinctDays = checkins.mapNotNull { it.createdAt?.toDate() }
                .map { "${it.year}-${it.month}-${it.date}" }
                .distinct()
                .size
            distinctDays
        } else 0

        val lastRunDate = checkins.firstOrNull()?.createdAt?.toDate()?.toString() ?: "None yet"

        return RunnerStats(
            totalRuns = totalRuns,
            totalDistanceKm = totalDistanceKm,
            currentStreak = streak,
            lastRunDateFormatted = lastRunDate
        )
    }
}
