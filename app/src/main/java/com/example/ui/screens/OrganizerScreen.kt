package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.model.Community
import com.example.data.model.RunSession
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.EarthSage
import com.example.ui.theme.Terracotta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MeetingPreset(val name: String, val address: String, val lat: Double, val lng: Double)

val CommonMeetingPresets = listOf(
    MeetingPreset("Ferry Building", "Ferry Building Plaza, San Francisco", 37.7955, -122.3937),
    MeetingPreset("Marina Green", "Marina Blvd & Scott St, San Francisco", 37.8055, -122.4418),
    MeetingPreset("Central Park", "Columbus Circle Entrance, New York", 40.7681, -73.9819),
    MeetingPreset("Prospect Park", "Grand Army Plaza, Brooklyn", 40.6738, -73.9701),
    MeetingPreset("Lady Bird Lake", "Pfluger Pedestrian Bridge, Austin", 30.2646, -97.7533),
    MeetingPreset("Hyde Park", "Hyde Park Corner, London", 51.5033, -0.1517)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerScreen(
    userCommunities: List<Community>,
    organizedRuns: List<RunSession>,
    checkinsByRunId: Map<String, List<CheckinRecord>>,
    onCreateRun: (
        communityId: String,
        title: String,
        startsAtDate: Date,
        meetingLat: Double,
        meetingLng: Double,
        meetingAddress: String,
        distanceKm: Double
    ) -> Unit,
    onSelectRun: (RunSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())

    var isCreating by remember { mutableStateOf(false) }

    // Form states
    var selectedCommunity by remember(userCommunities) { mutableStateOf(userCommunities.firstOrNull()) }
    var expandedCommunityDropdown by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var distanceKm by remember { mutableStateOf("5.0") }
    var selectedPreset by remember { mutableStateOf(CommonMeetingPresets.first()) }
    var customAddress by remember { mutableStateOf("") }
    var customLat by remember { mutableDoubleStateOf(CommonMeetingPresets.first().lat) }
    var customLng by remember { mutableDoubleStateOf(CommonMeetingPresets.first().lng) }
    var startInMinutesOffset by remember { mutableStateOf(45L) } // default 45 min from now so checkin opens in 15 min

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp)
    ) {
        item {
            Text(
                text = "Organizer Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Schedule group runs and monitor live physical check-ins",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )

            // Create Run Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Terracotta.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Terracotta)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Schedule a Group Run",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (userCommunities.isEmpty()) {
                        Text(
                            text = "You need to create or join a community before hosting a run.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        // Community Selector Dropdown
                        ExposedDropdownMenuBox(
                            expanded = expandedCommunityDropdown,
                            onExpandedChange = { expandedCommunityDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = selectedCommunity?.name ?: "Select Community",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Hosting Community") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCommunityDropdown) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCommunityDropdown,
                                onDismissRequest = { expandedCommunityDropdown = false }
                            ) {
                                userCommunities.forEach { comm ->
                                    DropdownMenuItem(
                                        text = { Text("${comm.name} (${comm.city})") },
                                        onClick = {
                                            selectedCommunity = comm
                                            expandedCommunityDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Run Title (e.g. Saturday Morning Loop)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_title_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = distanceKm,
                                onValueChange = { distanceKm = it },
                                label = { Text("Distance (km)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("run_distance_input")
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    text = "Start Time",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        15L to "In 15m",
                                        45L to "In 45m",
                                        120L to "In 2h",
                                        1440L to "Tomorrow"
                                    ).forEach { (offset, label) ->
                                        FilterChip(
                                            selected = startInMinutesOffset == offset,
                                            onClick = { startInMinutesOffset = offset },
                                            label = { Text(label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Terracotta,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Meeting Point Preset",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CommonMeetingPresets.forEach { preset ->
                                val isSelected = selectedPreset.name == preset.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedPreset = preset
                                        customAddress = preset.address
                                        customLat = preset.lat
                                        customLng = preset.lng
                                    },
                                    label = { Text(preset.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EarthSage,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = if (customAddress.isNotBlank()) customAddress else selectedPreset.address,
                            onValueChange = { customAddress = it },
                            label = { Text("Meeting Point Address / Landmark") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Rules Enforced by Server:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "• Check-in opens 30 min before start\n• Check-in closes 15 min after start\n• Requires physical presence within 100m of meeting point",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                val comm = selectedCommunity ?: return@Button
                                val dist = distanceKm.toDoubleOrNull() ?: 5.0
                                val addr = if (customAddress.isNotBlank()) customAddress else selectedPreset.address
                                val runDate = Date(System.currentTimeMillis() + startInMinutesOffset * 60 * 1000L)

                                isCreating = true
                                onCreateRun(
                                    comm.id,
                                    title.ifBlank { "${comm.name} Group Run" },
                                    runDate,
                                    customLat,
                                    customLng,
                                    addr,
                                    dist
                                )
                                isCreating = false
                                title = ""
                                Toast.makeText(context, "Run created! Runners can now check in.", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !isCreating && selectedCommunity != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("submit_create_run_button"),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Terracotta)
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.DirectionsRun, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Publish Run & Open Attendance", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Your Scheduled Runs & Live Attendance",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (organizedRuns.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No Runs Organized Yet",
                    subtitle = "Fill in the form above to host your first community group run!",
                    icon = Icons.Default.Event
                )
            }
        } else {
            items(organizedRuns, key = { it.id }) { run ->
                val attendees = checkinsByRunId[run.id] ?: emptyList()
                OrganizerRunCard(
                    run = run,
                    attendees = attendees,
                    dateFormat = dateFormat,
                    timeFormat = timeFormat,
                    onClick = { onSelectRun(run) }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
fun OrganizerRunCard(
    run: RunSession,
    attendees: List<CheckinRecord>,
    dateFormat: SimpleDateFormat,
    timeFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val status = run.getCheckinStatus()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("organizer_run_${run.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = run.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EarthSage.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            tint = EarthSage,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${attendees.size} Checked In",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = EarthSage
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val dateStr = run.startsAt?.toDate()?.let { dateFormat.format(it) } ?: "Date TBD"
            Text(
                text = "$dateStr • ${run.distanceKm} km",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            StatusBadge(status = status)

            // Live Attendance Snippet
            if (attendees.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Live Checked-in Runners:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    attendees.take(3).forEach { checkin ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EarthSage,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${checkin.userName} (${checkin.distanceToMeetingM.toInt()}m away)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (attendees.size > 3) {
                        Text(
                            text = "+ ${attendees.size - 3} more runners • Tap to view all",
                            style = MaterialTheme.typography.labelSmall,
                            color = Terracotta
                        )
                    }
                }
            }
        }
    }
}
