# Corraloop + Supabase setup

Supabase project: [corraloop dashboard](https://supabase.com/dashboard/project/wdepjpstshrkhjzsugjq)  
Project URL: `https://wdepjpstshrkhjzsugjq.supabase.co`  
Region: Singapore (`ap-southeast-1`)

## Target architecture: Firebase Auth + Supabase database

Corraloop will keep Firebase Authentication for Google sign-in and user identity, while Supabase Postgres becomes the only application database. Firestore data migration is intentionally out of scope because there is no production data to preserve.

The deployed Supabase database has six application tables, all with Row Level Security enabled:

- `profiles` — runner profile fields, keyed by Firebase UID.
- `communities` — community details and database-maintained member count.
- `memberships` — runner-to-community relationship; clients cannot self-promote to organizer.
- `runs` — running events and a database-controlled check-in window.
- `event_registrations` — RSVP / registration status for an event.
- `checkins` — attendance history without retaining the runner's precise GPS coordinates.

The three SQL migration versions are recorded in Supabase. Check-in is done through `public.check_in_to_run`, which validates identity, community membership, time window, GPS accuracy and server-computed distance. Only signed-in app clients may call it.

## Authentication bridge

Pass Firebase ID tokens to Supabase through its Firebase third-party-auth integration. This keeps the current login flow while replacing Firestore.

1. Open Supabase Dashboard → Authentication → Sign In / Providers → Third-Party Auth.
2. Add Firebase and enter the **Firebase Project ID** from Firebase Console → Project settings → General. Do not use the Firestore database ID in `firebase_applet_config.xml`; that is a different setting.
3. Configure Firebase to issue the `role: "authenticated"` custom claim in ID tokens. Use a trusted Firebase Admin SDK / Cloud Function, never a client-controlled claim. Set it for existing test accounts and future users, then refresh the ID token or sign in again.
4. The client provider in `app/src/main/java/com/example/data/supabase/SupabaseClientProvider.kt` forwards the token. RLS uses its `sub` as the text user ID.

Official guide: https://supabase.com/docs/guides/auth/third-party/firebase-auth

## Android client settings

The app uses the Google Secrets Gradle Plugin with `.env` and `.env.example`. The example includes the Supabase URL and **publishable** client key; these are public client values, not server secrets. Never put a Supabase secret / service-role key into Android source, `.env.example`, or an APK. The plugin makes the values available as `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_PUBLISHABLE_KEY`.

The SDK dependency is `supabase-kt`; core library desugaring is enabled for minSdk 24. Confirm Kotlin / Supabase / Ktor version compatibility by running the Android build before merging.

## Direct cutover plan — no data migration

The schema is intentionally empty. Do not export or copy Firestore data and do not load demo personas from `DemoRepository.kt` as production records.

1. Configure the Firebase third-party auth bridge and verify the current user's refreshed ID token contains `role=authenticated`.
2. Replace Firestore calls in `CorraloopRepository.kt` with Supabase queries/RPCs while preserving the repository's public methods so Compose screens need minimal changes.
3. Map the existing Kotlin models to Postgres rows. Prefer explicit DTOs with Kotlin serialization rather than Firestore-only annotations/types; convert Postgres timestamps to the existing UI model types at the repository boundary.
4. Move community creation/join/leave, event registration, and check-in writes to database operations. Use database constraints/triggers/RPCs for authorization and counters; do not trust client-only validation for attendance.
5. Update repository tests and security tests. Verify login, profile save/read, community list/create/join/leave, run list/create, registrations, check-in eligibility and RLS.
6. Run the Android Gradle build and tests. Only after they pass should the app's Firestore data dependency be removed. Keep Firebase Auth, Google sign-in, and any unrelated Firebase features still in use.

## First connection test

1. Complete Firebase third-party auth setup above.
2. Sign in to Corraloop and force-refresh the Firebase ID token.
3. Call `SupabaseConnectionCheck.readSampleCommunities()` from a coroutine. It issues a read-only query for `id,name`; an empty list is a successful response when the schema is empty.
4. Treat this as a connection smoke test only. It does **not** mean the app has been cut over: `CorraloopRepository.kt` still needs to be ported and the Android build/tests have not yet been verified.

## Security notes

- All six public tables have RLS enabled.
- Client insert/update privileges are restricted by column grants and RLS.
- Member counts are trigger-maintained rather than incremented by the client.
- Run check-in windows are fixed to 30 minutes before until 15 minutes after the scheduled start.
- The check-in RPC stores distance and accuracy, but not raw runner GPS.
- Supabase Security Advisor warns about the authenticated `SECURITY DEFINER` RPC because it needs elevated privileges to insert attendance. It is explicitly revoked from `anon` / `public` and performs its own authentication and eligibility checks. Review before production launch.

## Official references

- Supabase Kotlin quickstart: https://supabase.com/docs/guides/getting-started/quickstarts/kotlin
- Firebase third-party auth: https://supabase.com/docs/guides/auth/third-party/firebase-auth
