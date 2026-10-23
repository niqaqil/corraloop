# Corraloop + Supabase setup

Supabase project: [corraloop dashboard](https://supabase.com/dashboard/project/wdepjpstshrkhjzsugjq)  
Project URL: `https://wdepjpstshrkhjzsugjq.supabase.co`  
Region: Singapore (`ap-southeast-1`)

## What has been created

The deployed database has six application tables, all with Row Level Security enabled:

- `profiles` — public runner profile fields; text IDs preserve Firebase UIDs during a staged migration.
- `communities` — community details and database-maintained member count.
- `memberships` — runner-to-community relationship; clients cannot self-promote to organizer.
- `runs` — running events and a database-controlled check-in window.
- `event_registrations` — RSVP / registration status for an event.
- `checkins` — attendance history without retaining the runner's precise GPS coordinates.

The three SQL migration versions are recorded in Supabase. Check-in is done through `public.check_in_to_run`, which validates identity, community membership, time window, GPS accuracy and server-computed distance. Only signed-in app clients may call it.

## Authentication bridge

Corraloop currently signs in via Firebase Auth. Keep that temporarily and pass Firebase ID tokens to Supabase through its Firebase third-party-auth integration. This avoids an immediate login rewrite.

1. Open Supabase Dashboard → Authentication → Sign In / Providers → Third-Party Auth.
2. Add Firebase and enter the **Firebase Project ID** from Firebase Console → Project settings → General. Do not use the Firestore database ID in `firebase_applet_config.xml`; that is a different setting.
3. Configure Firebase to issue the `role: "authenticated"` custom claim in ID tokens. Use a trusted Firebase Admin SDK / Cloud Function, never a client-controlled claim. Backfill existing users and refresh tokens / sign in again.
4. The client provider in `app/src/main/java/com/example/data/supabase/SupabaseClientProvider.kt` forwards the token. RLS uses its `sub` as a text UID, allowing Firebase UID values to be preserved.

Official guide: https://supabase.com/docs/guides/auth/third-party/firebase-auth

## Android client settings

The app uses the Google Secrets Gradle Plugin with `.env` and `.env.example`. The example now includes the Supabase URL and **publishable** client key; these are public client values, not server secrets. Never put a Supabase secret / service-role key into Android source, `.env.example`, or an APK. The plugin makes the values available as `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_PUBLISHABLE_KEY`.

The SDK dependency is `supabase-kt`; minimum Android SDK support is handled with core library desugaring because this app targets minSdk 24. If the app cannot resolve dependency versions, use the official Kotlin quickstart and make sure Kotlin / Supabase / Ktor versions are compatible.

## Firestore to Supabase mapping

| Firestore collection | Supabase table | Notes |
| --- | --- | --- |
| `profiles` | `profiles` | Preserve Firebase UID in `id`. |
| `communities` | `communities` | Preserve community document ID in `id`, creator UID in `created_by`. |
| `memberships` | `memberships` | Preserve document ID if useful; unique pair is `(community_id, user_id)`. |
| `runs` | `runs` | Preserve run document ID; timestamps become `timestamptz`. |
| `checkins` | `checkins` | Keep check-in IDs if needed. Exact runner GPS coordinates are not migrated/stored in this schema. |
| — | `event_registrations` | New relational RSVP table. |

The migrations create **empty** tables; they do not copy Firestore documents. Export Firestore separately, validate identifiers and row counts, then load using a one-off client-side migration script or trusted server process. Dry-run and reconcile counts before cutover. Do not migrate demo personas from `DemoRepository.kt` as production data.

## First connection test

1. Complete the Firebase third-party auth setup above.
2. Sign in to Corraloop to ensure a valid Firebase ID token is present.
3. Call `SupabaseConnectionCheck.readSampleCommunities()` from a coroutine. It issues a read-only query for `id,name`; an empty list is a successful response if the tables are still empty.
4. Port reads first (communities and runs), then writes (profiles, memberships, registrations), and finally the check-in RPC. Leave Firebase available until parity tests pass.

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
