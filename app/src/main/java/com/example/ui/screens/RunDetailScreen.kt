package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CheckinRecord
import com.example.data.model.CheckinWindowStatus
import com.example.data.model.RunSession
import com.example.ui.components.MapRadarView
import com.example.ui.components.StatusBadge
import com.example.ui.theme.EarthSage
import com.example.ui.theme.Terracotta
import com.example.util.CheckinVerificationResult
import com.example.util.LocationCheckinResult
import com.example.util.LocationHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    run: RunSession,
    attendees: List<CheckinRecord>,
    userCheckin: CheckinRecord?,
    currentUserName: String,
    onBack: () -> Unit,
    onCheckinConfirmed: (LocationCheckinResult) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dateFormat = SimpleDateFormat("EEEE, MMMM d • h:mm a", Locale.getDefault())
    val checkinTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    var isCheckingIn by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var lastMeasuredDistance by remember { mutableStateOf(userCheckin?.distanceToMeetingM) }
    var lastMeasuredAccuracy by remember { mutableStateOf(userCheckin?.accuracyM) }

    val status = run.getCheckinStatus()

    fun performLocationCheck() {
        isCheckingIn = true
        locationError = null
        coroutineScope.launch {
            val location = LocationHelper.getCurrentLocationOnce(context)
            if (location == null) {
                isCheckingIn = false
                locationError = "Unable to read device GPS. Ensure location services are enabled."
                return@launch
            }

            lastMeasuredAccuracy = location.accuracy.toDouble()
            val distance = LocationHelper.calculateDistanceMeters(
                location.latitude,
                location.longitude,
                run.meetingLat,
                run.meetingLng
            )
            lastMeasuredDistance = distance

            when (val result = LocationHelper.verifyLocationForCheckin(location, run.meetingLat, run.meetingLng)) {
                is CheckinVerificationResult.Success -> {
                    isCheckingIn = false
                    onCheckinConfirmed(result.checkinData)
                    Toast.makeText(context, "Checked in successfully! Have a great run!", Toast.LENGTH_SHORT).show()
                }
                is CheckinVerificationResult.AccuracyRejected -> {
                    isCheckingIn = false
                    locationError = "GPS accuracy ±${result.accuracyM.toInt()}m is insufficient. You must have ±50m accuracy or better."
                }
                is CheckinVerificationResult.DistanceRejected -> {
                    isCheckingIn = false
                    locationError = "You are ${result.distanceM.toInt()}m away from the meeting point. You must be within 100m to check in."
                }
                is CheckinVerificationResult.Failure -> {
                    isCheckingIn = false
                    locationError = result.message
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            performLocationCheck()
        } else {
            locationError = "Fine location permission is required to verify physical presence at the meeting spot."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                // Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = run.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Terracotta.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${run.distanceKm} km",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Terracotta,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val formattedDate = run.startsAt?.toDate()?.let { dateFormat.format(it) } ?: "Date TBD"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        StatusBadge(status = status)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Map & Meeting Radar View
                MapRadarView(
                    meetingAddress = run.meetingAddress,
                    distanceToMeetingM = lastMeasuredDistance,
                    accuracyM = lastMeasuredAccuracy
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Check-in Status & Action Section
                if (userCheckin != null) {
                    // Already Checked In Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = EarthSage.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EarthSage,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "You are Checked In!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EarthSage
                                )
                                val timeStr = userCheckin.createdAt?.toDate()?.let { checkinTimeFormat.format(it) } ?: "Just now"
                                Text(
                                    text = "Verified at $timeStr • ${userCheckin.distanceToMeetingM.toInt()}m from meeting spot",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    // Check-In Button
                    val isWindowOpen = status is CheckinWindowStatus.Open

                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        enabled = isWindowOpen && !isCheckingIn,
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Terracotta,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("checkin_button")
                    ) {
                        if (isCheckingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Reading high-precision GPS...")
                        } else {
                            Icon(Icons.Default.GpsFixed, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isWindowOpen) "Check In Now" else "Check-in Not Open",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (locationError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = locationError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Live Attendance Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Attendance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EarthSage.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${attendees.size} Checked In",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = EarthSage,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (attendees.isEmpty()) {
                item {
                    Text(
                        text = "No runners have checked in yet. Be the first to arrive!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(attendees, key = { it.id }) { checkin ->
                    AttendeeRow(checkin = checkin, checkinTimeFormat = checkinTimeFormat)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun AttendeeRow(checkin: CheckinRecord, checkinTimeFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(EarthSage.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = EarthSage,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = checkin.userName.ifBlank { "Runner" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val timeStr = checkin.createdAt?.toDate()?.let { checkinTimeFormat.format(it) } ?: "Just now"
                Text(
                    text = "Arrived at $timeStr • ${checkin.distanceToMeetingM.toInt()}m away",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EarthSage,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
