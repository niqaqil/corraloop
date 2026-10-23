package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Community
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.EarthSage
import com.example.ui.theme.Terracotta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesScreen(
    communities: List<Community>,
    userJoinedCommunityIds: Set<String>,
    userCity: String,
    onCommunityClick: (Community) -> Unit,
    onCreateCommunity: (name: String, city: String, description: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCity by remember { mutableStateOf(userCity.ifBlank { "All" }) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Popular default cities + runner's city
    val availableCities = remember(communities, userCity) {
        val set = mutableSetOf("All")
        if (userCity.isNotBlank()) set.add(userCity)
        communities.forEach { if (it.city.isNotBlank()) set.add(it.city) }
        set.toList()
    }

    val filteredCommunities = remember(communities, selectedCity, searchQuery) {
        communities.filter { comm ->
            val matchesCity = selectedCity == "All" || comm.city.equals(selectedCity, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    comm.name.contains(searchQuery, ignoreCase = true) ||
                    comm.city.contains(searchQuery, ignoreCase = true) ||
                    comm.description.contains(searchQuery, ignoreCase = true)
            matchesCity && matchesQuery
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp)
        ) {
            item {
                Text(
                    text = "Running Communities",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Discover free group runs and clubs near you",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or landmark...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("communities_search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // City Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableCities.forEach { city ->
                        val isSelected = selectedCity.equals(city, ignoreCase = true)
                        InputChip(
                            selected = isSelected,
                            onClick = { selectedCity = city },
                            label = { Text(city) },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = Terracotta,
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (filteredCommunities.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Communities Found",
                        subtitle = if (selectedCity != "All") "No running clubs in $selectedCity yet. Start the first one!" else "Create a community to start free group runs.",
                        icon = Icons.Default.LocationCity
                    )
                }
            } else {
                items(filteredCommunities, key = { it.id }) { community ->
                    CommunityCard(
                        community = community,
                        isMember = userJoinedCommunityIds.contains(community.id),
                        onClick = { onCommunityClick(community) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // Floating Action Button to create community
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = Terracotta,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("create_community_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Community")
        }
    }

    if (showCreateDialog) {
        CreateCommunityDialog(
            defaultCity = if (selectedCity != "All") selectedCity else userCity,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, city, desc ->
                showCreateDialog = false
                onCreateCommunity(name, city, desc)
            }
        )
    }
}

@Composable
fun CommunityCard(
    community: Community,
    isMember: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("community_card_${community.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isMember) EarthSage.copy(alpha = 0.15f) else Terracotta.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = if (isMember) EarthSage else Terracotta,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = community.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isMember) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EarthSage.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Joined",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EarthSage
                            )
                        }
                    }
                }

                Text(
                    text = "${community.city} • ${community.memberCount} members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (community.description.isNotBlank()) {
                    Text(
                        text = community.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CreateCommunityDialog(
    defaultCity: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, city: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf(defaultCity.ifBlank { "San Francisco" }) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a Run Community", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Club Name (e.g. Marina Sunrise Runners)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && city.isNotBlank()) {
                        onConfirm(name, city, description)
                    }
                },
                enabled = name.isNotBlank() && city.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Terracotta)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
