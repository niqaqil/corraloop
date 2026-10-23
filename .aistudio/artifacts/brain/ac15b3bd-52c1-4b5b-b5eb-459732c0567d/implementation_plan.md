# Corraloop - Quick Demo Mode & Role Switcher

This update adds an instant **Quick Demo Mode** to Corraloop, enabling immediate exploration and end-to-end testing of all features without requiring active Google credentials or Google Play Services setup in the streaming browser emulator.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The approach for dummy data testing was confirmed with the user:
> - **Demo Mode Integration**: Add a prominent "Explore with Demo Mode" option on the sign-in screen that immediately launches the app with rich sample data.
> - **Multi-Role Support**: Provide two distinct preconfigured test personas with an instant in-app role switcher:
>   1. **Elena Vance (Organizer)**: Founder & organizer of *Marina Sunrise Runners* (San Francisco); can create new runs, configure meeting points, and monitor live attendance lists.
>   2. **Marcus Reed (Runner)**: Enthusiast runner with 5-run streak, 28.5 km recorded, past verified GPS check-in history, and active community memberships.
> - **Live Persistence Preserved**: The production Google Sign-In with Firebase Firestore remains completely functional side-by-side with Demo Mode.

---

### 1. Overview & Core Concept

Demo Mode provides instant, zero-friction access to the entire Corraloop application:
- **Instant Access**: Bypasses Google Sign-In with a single tap for testing, review, and demonstration.
- **Rich Realistic State**: Pre-populated with diverse running clubs (San Francisco, New York, Austin), upcoming group runs with various check-in window statuses (Open, Upcoming, Closed), and live attendance rosters.
- **Full Interactivity**: Users in Demo Mode can join/leave communities, schedule new runs, test the single-shot GPS check-in with the interactive map radar, and toggle between Organizer and Runner perspectives in real time.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Sign-In Screen Experience**:
   - Above or below the "Sign in with Google" button, a distinct secondary card: **"Want to test right away? Explore with Demo Mode"**.
   - Includes two one-tap persona buttons:
     - 🏃 **Marcus Reed** (*Regular Runner • SF*)
     - 📋 **Elena Vance** (*Club Organizer • SF*)
2. **Demo Mode Indicator & Fast Switcher**:
   - A non-intrusive Warm Minimalist header pill displayed at the top of the screen:
     - Displays current active demo persona and role tag.
     - Includes a quick **"Switch Persona"** action button to jump between Elena (Organizer) and Marcus (Runner) without logging out.
     - Includes an **"Exit Demo"** button to return to the real sign-in screen.
3. **Pre-populated Sample Data**:
   - **Communities**:
     - *Marina Sunrise Runners* (San Francisco, 42 runners)
     - *Central Park Strides* (New York, 118 runners)
     - *Austin Trail Blazers* (Austin, 64 runners)
   - **Runs with Active Windows**:
     - *Embarcadero Sunrise 5K*: Meeting at Ferry Building Plaza. Check-in window is currently **OPEN** (closes in 12 min)!
     - *Golden Gate Promenade 10K*: Meeting at Marina Green. Check-in window is **UPCOMING** (opens in 20 min).
     - *Twin Peaks Hill Repeat*: Past run showing historical check-in roster.
   - **Runner Metrics**: Marcus starts with 5 verified runs, 28.5 km, and a 5-day active streak.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Reactive In-Memory Demo Adapter vs. Firestore Database Pollution**
  - *Chosen Approach*: Implement a reactive `DemoRepository` that manages in-memory StateFlows populated with realistic entities. When in Demo Mode, actions (creating runs, checking in, joining clubs) mutate the demo state reactively.
  - *Why*: Eliminates clutter in production Firestore databases, requires zero network latency, prevents test data collision across emulator sessions, and avoids the risk of unauthenticated writes to live collections.
- **Decision 2: Side-by-Side Coexistence with Production Auth**
  - *Chosen Approach*: The root app state maintains `demoSession: DemoUser?`. If `demoSession != null`, it routes to the demo repository; otherwise, it enforces production Firebase Credential Manager auth.
  - *Why*: Gives users both worlds: instant evaluation in the browser emulator and real cloud persistence with Google Sign-In.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Jetpack Compose UI                            │
├───────────────┬───────────────────┬───────────────────┬────────────────┤
│ AuthScreen    │ CommunitiesScreen │   RunDetailScreen │  MyRunsScreen  │
│ [Demo Bar]    │ & CommunityDetail │   & GPS Check-In  │  & Attendance  │
└───────┬───────┴─────────┬─────────┴─────────┬─────────┴────────┬───────┘
        │                 │                   │                  │
        ▼                 ▼                   ▼                  ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Corraloop UI Bridge                             │
│       (Routes to Live Repository OR In-Memory Demo Repository)         │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │
                 ┌────────────────┴────────────────┐
                 ▼                                 ▼
   ┌───────────────────────────┐     ┌───────────────────────────┐
   │    CorraloopRepository    │     │       DemoRepository      │
   │   (Live Cloud Firestore   │     │  (In-Memory StateFlows,   │
   │    & Credential Manager)  │     │   Elena & Marcus Personas)│
   └───────────────────────────┘     └───────────────────────────┘
```

#### Demo Data Models
- **Elena Vance**: `userId = "demo_organizer_elena"`, `name = "Elena Vance"`, `city = "San Francisco"`
- **Marcus Reed**: `userId = "demo_runner_marcus"`, `name = "Marcus Reed"`, `city = "San Francisco"`
- Preloaded check-in records for Marcus and other simulated runners (Sarah L., David K., Maya P.).

---

### Implementation Phases

1. **Phase 1: Demo Repository & Sample Dataset**
   - Create `app/src/main/java/com/example/data/demo/DemoRepository.kt` with initial mock data:
     - 3 communities (SF, NYC, Austin)
     - 3 runs with varied start times ensuring at least one run has an **OPEN check-in window**
     - Multiple sample check-in records with realistic GPS coordinates and accuracy
     - Full reactive methods matching `CorraloopRepository` (`joinCommunity`, `createRun`, `performCheckin`)
2. **Phase 2: Auth Screen Demo Actions**
   - Update `AuthScreen.kt` with an "Explore with Demo Mode" card offering 1-tap entry as either Elena (Organizer) or Marcus (Runner).
3. **Phase 3: Top Bar Persona Switcher**
   - Add a demo banner / switcher pill in `MainActivity.kt` allowing instant toggle between Elena and Marcus, plus an "Exit Demo" action.
4. **Phase 4: Compilation & Verification**
   - Verify with `compile_applet` and run existing automated tests.
