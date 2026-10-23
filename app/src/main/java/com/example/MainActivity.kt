package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.data.demo.DemoPersona
import com.example.data.demo.DemoRepository
import com.example.data.model.CheckinRecord
import com.example.data.model.Community
import com.example.data.model.RunSession
import com.example.data.repository.CorraloopRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.ProfileSetupScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutUser
import com.example.ui.screens.CommunitiesScreen
import com.example.ui.screens.CommunityDetailScreen
import com.example.ui.screens.MyRunsScreen
import com.example.ui.screens.OrganizerScreen
import com.example.ui.screens.RunDetailScreen
import com.example.ui.theme.CorraloopTheme
import com.example.ui.theme.EarthSage
import com.example.ui.theme.Terracotta
import com.example.ui.theme.WarmOchre
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

sealed interface ActiveScreen {
    data object MainTabs : ActiveScreen
    data class CommunityDetail(val community: Community) : ActiveScreen
    data class RunDetail(val run: RunSession) : ActiveScreen
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CorraloopTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var activeDemoPersona by remember { mutableStateOf<DemoPersona?>(null) }
    var isCheckingAuth by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            isCheckingAuth = false
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
                isCheckingAuth = false
            },
            onUnauthenticated = {
                isCheckingAuth = false
            },
            scope = coroutineScope
        )
    }

    if (activeDemoPersona != null) {
        // Active Demo Mode
        DemoAppContent(
            persona = activeDemoPersona!!,
            onSwitchPersona = { newPersona ->
                activeDemoPersona = newPersona
            },
            onExitDemo = {
                activeDemoPersona = null
            }
        )
    } else if (isCheckingAuth) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Terracotta)
            }
        }
    } else if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            },
            onSelectDemoPersona = { persona ->
                activeDemoPersona = persona
            }
        )
    } else {
        AuthenticatedApp(
            userId = currentUser!!.uid,
            onSignOut = {
                signOutUser(
                    context = context,
                    credentialManager = credentialManager,
                    onSignOutComplete = {
                        currentUser = null
                    },
                    scope = coroutineScope
                )
            }
        )
    }
}

/**
 * Interactive Demo App Content with pre-seeded sample data
 * and reactive in-memory state mutations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoAppContent(
    persona: DemoPersona,
    onSwitchPersona: (DemoPersona) -> Unit,
    onExitDemo: () -> Unit
) {
    val communities by DemoRepository.observeCommunities().collectAsState(initial = emptyList())
    val memberships by DemoRepository.observeMembershipsForUser(persona.userId).collectAsState(initial = emptyList())
    val allRuns by DemoRepository.observeAllRuns().collectAsState(initial = emptyList())
    val userCheckins by DemoRepository.observeUserCheckins(persona.userId).collectAsState(initial = emptyList())

    val userJoinedCommunityIds = remember(memberships) {
        memberships.map { it.communityId }.toSet()
    }
    val userOrganizedCommunityIds = remember(memberships) {
        memberships.filter { it.role == "organizer" }.map { it.communityId }.toSet()
    }

    val organizedRuns = remember(allRuns, userOrganizedCommunityIds, persona.userId) {
        allRuns.filter { it.createdBy == persona.userId || userOrganizedCommunityIds.contains(it.communityId) }
    }

    val runsMap = remember(allRuns) {
        allRuns.associateBy { it.id }
    }

    val runnerStats = remember(userCheckins, runsMap) {
        DemoRepository.computeRunnerStats(userCheckins, runsMap)
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var activeScreen by remember { mutableStateOf<ActiveScreen>(ActiveScreen.MainTabs) }

    when (val screen = activeScreen) {
        is ActiveScreen.CommunityDetail -> {
            val comm = screen.community
            val isMember = userJoinedCommunityIds.contains(comm.id)
            val isOrganizer = comm.createdBy == persona.userId || userOrganizedCommunityIds.contains(comm.id)
            val communityRuns = remember(allRuns, comm.id) {
                allRuns.filter { it.communityId == comm.id }
            }

            CommunityDetailScreen(
                community = comm,
                runs = communityRuns,
                isMember = isMember,
                isOrganizer = isOrganizer,
                onBack = { activeScreen = ActiveScreen.MainTabs },
                onJoinClick = { DemoRepository.joinCommunity(persona.userId, comm.id) },
                onLeaveClick = { DemoRepository.leaveCommunity(persona.userId, comm.id) },
                onRunClick = { run -> activeScreen = ActiveScreen.RunDetail(run) },
                onHostRunClick = {
                    selectedTabIndex = 2
                    activeScreen = ActiveScreen.MainTabs
                }
            )
        }
        is ActiveScreen.RunDetail -> {
            val run = screen.run
            val attendees by DemoRepository.observeCheckinsForRun(run.id).collectAsState(initial = emptyList())
            val userCheckin = attendees.firstOrNull { it.userId == persona.userId }

            RunDetailScreen(
                run = run,
                attendees = attendees,
                userCheckin = userCheckin,
                currentUserName = persona.displayName,
                onBack = { activeScreen = ActiveScreen.MainTabs },
                onCheckinConfirmed = { checkinResult ->
                    DemoRepository.performCheckin(
                        run = run,
                        userId = persona.userId,
                        userName = persona.displayName,
                        checkinData = checkinResult
                    )
                }
            )
        }
        is ActiveScreen.MainTabs -> {
            Scaffold(
                topBar = {
                    Column {
                        // Demo Mode Banner
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Terracotta.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (persona == DemoPersona.ORGANIZER_ELENA) Icons.Default.Star else Icons.Default.DirectionsRun,
                                        contentDescription = null,
                                        tint = if (persona == DemoPersona.ORGANIZER_ELENA) EarthSage else Terracotta,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Demo: ${persona.displayName} (${persona.roleTitle})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val nextPersona = if (persona == DemoPersona.ORGANIZER_ELENA) {
                                                    DemoPersona.RUNNER_MARCUS
                                                } else {
                                                    DemoPersona.ORGANIZER_ELENA
                                                }
                                                onSwitchPersona(nextPersona)
                                            },
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("switch_demo_persona_button")
                                        ) {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (persona == DemoPersona.ORGANIZER_ELENA) "Switch to Marcus" else "Switch to Elena",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = onExitDemo,
                                        modifier = Modifier.size(28.dp).testTag("exit_demo_button")
                                    ) {
                                        Icon(
                                            Icons.Default.ExitToApp,
                                            contentDescription = "Exit Demo",
                                            tint = Terracotta,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        TopAppBar(
                            title = {
                                Text(
                                    text = "Corraloop",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            icon = { Icon(Icons.Default.Groups, contentDescription = "Clubs") },
                            label = { Text("Communities") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                indicatorColor = Terracotta
                            ),
                            modifier = Modifier.testTag("nav_tab_communities")
                        )
                        NavigationBarItem(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            icon = { Icon(Icons.Default.DirectionsRun, contentDescription = "Runs") },
                            label = { Text("My Runs") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                indicatorColor = Terracotta
                            ),
                            modifier = Modifier.testTag("nav_tab_my_runs")
                        )
                        NavigationBarItem(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            icon = { Icon(Icons.Default.AddCircle, contentDescription = "Host") },
                            label = { Text("Organizer") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                indicatorColor = Terracotta
                            ),
                            modifier = Modifier.testTag("nav_tab_organizer")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTabIndex) {
                        0 -> CommunitiesScreen(
                            communities = communities,
                            userJoinedCommunityIds = userJoinedCommunityIds,
                            userCity = persona.city,
                            onCommunityClick = { comm -> activeScreen = ActiveScreen.CommunityDetail(comm) },
                            onCreateCommunity = { name, city, desc ->
                                DemoRepository.createCommunity(persona.userId, name, city, desc)
                            }
                        )
                        1 -> MyRunsScreen(
                            stats = runnerStats,
                            checkins = userCheckins,
                            runsMap = runsMap
                        )
                        2 -> {
                            val userHostCommunities = remember(communities, userJoinedCommunityIds) {
                                communities.filter { userJoinedCommunityIds.contains(it.id) || it.createdBy == persona.userId }
                            }

                            var checkinsByRunId by remember { mutableStateOf<Map<String, List<CheckinRecord>>>(emptyMap()) }

                            LaunchedEffect(organizedRuns) {
                                organizedRuns.forEach { run ->
                                    DemoRepository.observeCheckinsForRun(run.id).collect { records ->
                                        checkinsByRunId = checkinsByRunId + (run.id to records)
                                    }
                                }
                            }

                            OrganizerScreen(
                                userCommunities = userHostCommunities,
                                organizedRuns = organizedRuns,
                                checkinsByRunId = checkinsByRunId,
                                onCreateRun = { commId, title, date, lat, lng, addr, dist ->
                                    DemoRepository.createRun(
                                        userId = persona.userId,
                                        communityId = commId,
                                        title = title,
                                        startsAtDate = date,
                                        meetingLat = lat,
                                        meetingLng = lng,
                                        meetingAddress = addr,
                                        distanceKm = dist
                                    )
                                },
                                onSelectRun = { run -> activeScreen = ActiveScreen.RunDetail(run) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedApp(
    userId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember(userId) { CorraloopRepository(context) }

    val userProfile by repository.observeProfile(userId).collectAsState(initial = null)
    var isProfileChecked by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        if (userProfile != null || !isProfileChecked) {
            isProfileChecked = true
        }
    }

    if (userProfile == null) {
        ProfileSetupScreen(
            currentName = Firebase.auth.currentUser?.displayName ?: "",
            currentCity = "San Francisco",
            onSaveProfile = { name, city ->
                coroutineScope.launch {
                    repository.saveProfile(name, city)
                }
            }
        )
        return
    }

    val communities by repository.observeCommunities().collectAsState(initial = emptyList())
    val memberships by repository.observeMembershipsForUser(userId).collectAsState(initial = emptyList())
    val allRuns by repository.observeAllRuns().collectAsState(initial = emptyList())
    val userCheckins by repository.observeUserCheckins(userId).collectAsState(initial = emptyList())

    val userJoinedCommunityIds = remember(memberships) {
        memberships.map { it.communityId }.toSet()
    }
    val userOrganizedCommunityIds = remember(memberships) {
        memberships.filter { it.role == "organizer" }.map { it.communityId }.toSet()
    }

    val organizedRuns = remember(allRuns, userOrganizedCommunityIds, userId) {
        allRuns.filter { it.createdBy == userId || userOrganizedCommunityIds.contains(it.communityId) }
    }

    val runsMap = remember(allRuns) {
        allRuns.associateBy { it.id }
    }

    val runnerStats = remember(userCheckins, runsMap) {
        repository.computeRunnerStats(userCheckins, runsMap)
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var activeScreen by remember { mutableStateOf<ActiveScreen>(ActiveScreen.MainTabs) }

    when (val screen = activeScreen) {
        is ActiveScreen.CommunityDetail -> {
            val comm = screen.community
            val isMember = userJoinedCommunityIds.contains(comm.id)
            val isOrganizer = comm.createdBy == userId || userOrganizedCommunityIds.contains(comm.id)
            val communityRuns = remember(allRuns, comm.id) {
                allRuns.filter { it.communityId == comm.id }
            }

            CommunityDetailScreen(
                community = comm,
                runs = communityRuns,
                isMember = isMember,
                isOrganizer = isOrganizer,
                onBack = { activeScreen = ActiveScreen.MainTabs },
                onJoinClick = {
                    coroutineScope.launch { repository.joinCommunity(comm.id) }
                },
                onLeaveClick = {
                    coroutineScope.launch { repository.leaveCommunity(comm.id) }
                },
                onRunClick = { run -> activeScreen = ActiveScreen.RunDetail(run) },
                onHostRunClick = {
                    selectedTabIndex = 2
                    activeScreen = ActiveScreen.MainTabs
                }
            )
        }
        is ActiveScreen.RunDetail -> {
            val run = screen.run
            val attendees by repository.observeCheckinsForRun(run.id).collectAsState(initial = emptyList())
            val userCheckin = attendees.firstOrNull { it.userId == userId }

            RunDetailScreen(
                run = run,
                attendees = attendees,
                userCheckin = userCheckin,
                currentUserName = userProfile?.name ?: "Runner",
                onBack = { activeScreen = ActiveScreen.MainTabs },
                onCheckinConfirmed = { checkinResult ->
                    coroutineScope.launch {
                        repository.performCheckin(
                            run = run,
                            checkinData = checkinResult,
                            userName = userProfile?.name ?: "Runner"
                        )
                    }
                }
            )
        }
        is ActiveScreen.MainTabs -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Corraloop",
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        actions = {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text(
                                        text = userProfile?.name ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            IconButton(onClick = onSignOut) {
                                Icon(
                                    Icons.Default.ExitToApp,
                                    contentDescription = "Sign out",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            icon = { Icon(Icons.Default.Groups, contentDescription = "Clubs") },
                            label = { Text("Communities") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                indicatorColor = Terracotta
                            ),
                            modifier = Modifier.testTag("nav_tab_communities")
                        )
                        NavigationBarItem(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            icon = { Icon(Icons.Default.DirectionsRun, contentDescription = "Runs") },
                            label = { Text("My Runs") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                indicatorColor = Terracotta
                            ),
                            modifier = Modifier.testTag("nav_tab_my_runs")
                        )
                        NavigationBarItem(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            icon = { Icon(Icons.Default.AddCircle, contentDescription = "Host") },
                            label = { Text("Organizer") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                indicatorColor = Terracotta
                            ),
                            modifier = Modifier.testTag("nav_tab_organizer")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTabIndex) {
                        0 -> CommunitiesScreen(
                            communities = communities,
                            userJoinedCommunityIds = userJoinedCommunityIds,
                            userCity = userProfile?.city ?: "",
                            onCommunityClick = { comm -> activeScreen = ActiveScreen.CommunityDetail(comm) },
                            onCreateCommunity = { name, city, desc ->
                                coroutineScope.launch {
                                    repository.createCommunity(name, city, desc)
                                }
                            }
                        )
                        1 -> MyRunsScreen(
                            stats = runnerStats,
                            checkins = userCheckins,
                            runsMap = runsMap
                        )
                        2 -> {
                            val userHostCommunities = remember(communities, userJoinedCommunityIds) {
                                communities.filter { userJoinedCommunityIds.contains(it.id) || it.createdBy == userId }
                            }

                            var checkinsByRunId by remember { mutableStateOf<Map<String, List<CheckinRecord>>>(emptyMap()) }

                            LaunchedEffect(organizedRuns) {
                                organizedRuns.forEach { run ->
                                    repository.observeCheckinsForRun(run.id).collect { records ->
                                        checkinsByRunId = checkinsByRunId + (run.id to records)
                                    }
                                }
                            }

                            OrganizerScreen(
                                userCommunities = userHostCommunities,
                                organizedRuns = organizedRuns,
                                checkinsByRunId = checkinsByRunId,
                                onCreateRun = { commId, title, date, lat, lng, addr, dist ->
                                    coroutineScope.launch {
                                        repository.createRun(
                                            communityId = commId,
                                            title = title,
                                            startsAtDate = date,
                                            meetingLat = lat,
                                            meetingLng = lng,
                                            meetingAddress = addr,
                                            distanceKm = dist
                                        )
                                    }
                                },
                                onSelectRun = { run -> activeScreen = ActiveScreen.RunDetail(run) }
                            )
                        }
                    }
                }
            }
        }
    }
}
