package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.repository.CorraloopRepository
import com.example.util.LocationCheckinResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CorraloopRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun testCreateAndReadProfile() = runBlocking {
    val uid = signInTestUser("runner_alice@test.com")
    val repository = CorraloopRepository(firestore)

    repository.saveProfile("Alice", "San Francisco")
    val profile = repository.observeProfile(uid).first()

    assertNotNull(profile)
    assertEquals("Alice", profile?.name)
    assertEquals("San Francisco", profile?.city)
  }

  @Test
  fun testCreateCommunityAndRun() = runBlocking {
    val uid = signInTestUser("organizer_bob@test.com")
    val repository = CorraloopRepository(firestore)

    val commResult = repository.createCommunity("SF Runners", "San Francisco", "Weekly free group runs")
    assertTrue("Community creation should succeed", commResult.isSuccess)
    val commId = commResult.getOrThrow()

    val runDate = Date(System.currentTimeMillis() + 60 * 60 * 1000L) // 1 hour in future
    val runResult = repository.createRun(
      communityId = commId,
      title = "Embarcadero Sunrise 5K",
      startsAtDate = runDate,
      meetingLat = 37.7955,
      meetingLng = -122.3937,
      meetingAddress = "Ferry Building Plaza",
      distanceKm = 5.0
    )
    assertTrue("Run creation should succeed", runResult.isSuccess)
  }
}
