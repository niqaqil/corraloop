-- Corraloop initial Supabase schema. Text IDs preserve existing Firebase UIDs and Firestore document IDs.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
grant usage on schema private to authenticated;

create table public.profiles (
  id text primary key,
  name text not null default 'Runner',
  city text not null default '',
  username text unique,
  avatar_url text,
  bio text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.communities (
  id text primary key default gen_random_uuid()::text,
  name text not null check (length(trim(name)) between 2 and 80),
  slug text unique,
  city text not null default '',
  description text not null default '',
  created_by text not null references public.profiles(id) on delete restrict,
  visibility text not null default 'public' check (visibility in ('public', 'private')),
  meeting_point text,
  latitude double precision check (latitude is null or latitude between -90 and 90),
  longitude double precision check (longitude is null or longitude between -180 and 180),
  max_members integer check (max_members is null or max_members > 0),
  member_count integer not null default 0 check (member_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.memberships (
  id text primary key default gen_random_uuid()::text,
  user_id text not null references public.profiles(id) on delete cascade,
  community_id text not null references public.communities(id) on delete cascade,
  role text not null default 'member' check (role in ('member', 'organizer')),
  joined_at timestamptz not null default now(),
  constraint memberships_community_user_unique unique (community_id, user_id)
);
create index memberships_user_joined_idx on public.memberships (user_id, joined_at desc);
create index memberships_community_role_idx on public.memberships (community_id, role);

create table public.runs (
  id text primary key default gen_random_uuid()::text,
  community_id text not null references public.communities(id) on delete cascade,
  title text not null check (length(trim(title)) between 2 and 120),
  description text not null default '',
  starts_at timestamptz not null,
  meeting_lat double precision check (meeting_lat is null or meeting_lat between -90 and 90),
  meeting_lng double precision check (meeting_lng is null or meeting_lng between -180 and 180),
  meeting_address text not null default '',
  distance_km numeric(6,2) check (distance_km is null or distance_km > 0),
  checkin_opens_at timestamptz,
  checkin_closes_at timestamptz,
  status text not null default 'scheduled' check (status in ('scheduled', 'cancelled', 'completed')),
  created_by text not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint runs_checkin_window_valid check (checkin_opens_at is null or checkin_closes_at is null or checkin_opens_at <= checkin_closes_at)
);
create index runs_community_starts_idx on public.runs (community_id, starts_at);
create index runs_status_starts_idx on public.runs (status, starts_at);

create table public.event_registrations (
  id text primary key default gen_random_uuid()::text,
  run_id text not null references public.runs(id) on delete cascade,
  user_id text not null references public.profiles(id) on delete cascade,
  status text not null default 'registered' check (status in ('registered', 'cancelled')),
  registered_at timestamptz not null default now(),
  constraint event_registrations_run_user_unique unique (run_id, user_id)
);
create index event_registrations_user_status_idx on public.event_registrations (user_id, status);
create index event_registrations_run_status_idx on public.event_registrations (run_id, status);

-- Raw runner coordinates are intentionally not persisted in checkins.
create table public.checkins (
  id text primary key default gen_random_uuid()::text,
  run_id text not null references public.runs(id) on delete cascade,
  user_id text not null references public.profiles(id) on delete cascade,
  user_name text not null default 'Runner',
  created_at timestamptz not null default now(),
  accuracy_m double precision not null check (accuracy_m >= 0 and accuracy_m <= 50),
  distance_to_meeting_m double precision not null check (distance_to_meeting_m >= 0 and distance_to_meeting_m <= 100),
  constraint checkins_run_user_unique unique (run_id, user_id)
);
create index checkins_user_created_idx on public.checkins (user_id, created_at desc);
create index checkins_run_created_idx on public.checkins (run_id, created_at desc);

create or replace function private.is_community_member(p_community_id text)
returns boolean language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.memberships m
    where m.community_id = p_community_id and m.user_id = (select auth.jwt() ->> 'sub')
  );
$$;

create or replace function private.is_community_organizer(p_community_id text)
returns boolean language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.communities c
    where c.id = p_community_id and c.created_by = (select auth.jwt() ->> 'sub')
  ) or exists (
    select 1 from public.memberships m
    where m.community_id = p_community_id
      and m.user_id = (select auth.jwt() ->> 'sub') and m.role = 'organizer'
  );
$$;
revoke all on function private.is_community_member(text) from public, anon;
revoke all on function private.is_community_organizer(text) from public, anon;
grant execute on function private.is_community_member(text) to authenticated;
grant execute on function private.is_community_organizer(text) to authenticated;

create or replace function private.update_community_member_count()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    update public.communities set member_count = member_count + 1, updated_at = now() where id = new.community_id;
    return new;
  elsif tg_op = 'DELETE' then
    update public.communities set member_count = greatest(member_count - 1, 0), updated_at = now() where id = old.community_id;
    return old;
  end if;
  return null;
end;
$$;
create trigger memberships_count_after_insert after insert on public.memberships
for each row execute function private.update_community_member_count();
create trigger memberships_count_after_delete after delete on public.memberships
for each row execute function private.update_community_member_count();

create or replace function private.add_community_creator_membership()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  insert into public.memberships (user_id, community_id, role)
  values (new.created_by, new.id, 'organizer') on conflict (community_id, user_id) do nothing;
  return new;
end;
$$;
create trigger communities_add_creator_membership after insert on public.communities
for each row execute function private.add_community_creator_membership();

create or replace function private.set_updated_at()
returns trigger language plpgsql set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;
create trigger profiles_set_updated_at before update on public.profiles for each row execute function private.set_updated_at();
create trigger communities_set_updated_at before update on public.communities for each row execute function private.set_updated_at();
create trigger runs_set_updated_at before update on public.runs for each row execute function private.set_updated_at();

-- Initial RPC version. The hardening migration enforces fixed bounds from starts_at.
create or replace function public.check_in_to_run(
  p_run_id text, p_latitude double precision, p_longitude double precision, p_accuracy_m double precision
)
returns public.checkins language plpgsql security definer set search_path = ''
as $$
declare
  v_user_id text := (select auth.jwt() ->> 'sub');
  v_run public.runs%rowtype;
  v_checkin public.checkins%rowtype;
  v_a double precision;
  v_distance_m double precision;
  v_now timestamptz := now();
  v_opens_at timestamptz;
  v_closes_at timestamptz;
  v_name text;
begin
  if v_user_id is null or v_user_id = '' then raise exception 'Authentication required' using errcode = '28000'; end if;
  if p_latitude is null or p_longitude is null or p_latitude < -90 or p_latitude > 90 or p_longitude < -180 or p_longitude > 180 then
    raise exception 'Invalid location coordinates' using errcode = '22023';
  end if;
  if p_accuracy_m is null or p_accuracy_m < 0 or p_accuracy_m > 50 then
    raise exception 'Location accuracy must be 50 metres or better' using errcode = '22023';
  end if;
  select r.* into v_run from public.runs r where r.id = p_run_id;
  if not found then raise exception 'Run not found' using errcode = 'P0002'; end if;
  if v_run.status <> 'scheduled' then raise exception 'This run is not available for check-in' using errcode = '22023'; end if;
  if v_run.meeting_lat is null or v_run.meeting_lng is null then raise exception 'This run has no check-in location configured' using errcode = '22023'; end if;
  if not private.is_community_member(v_run.community_id) then raise exception 'Join the community before checking in' using errcode = '42501'; end if;
  v_opens_at := coalesce(v_run.checkin_opens_at, v_run.starts_at - interval '30 minutes');
  v_closes_at := coalesce(v_run.checkin_closes_at, v_run.starts_at + interval '15 minutes');
  if v_now < v_opens_at then raise exception 'Check-in window has not opened yet' using errcode = '22023'; end if;
  if v_now > v_closes_at then raise exception 'Check-in window has closed' using errcode = '22023'; end if;
  v_a := power(sin(radians(p_latitude - v_run.meeting_lat) / 2), 2)
    + cos(radians(v_run.meeting_lat)) * cos(radians(p_latitude))
    * power(sin(radians(p_longitude - v_run.meeting_lng) / 2), 2);
  v_distance_m := 6371000 * 2 * asin(sqrt(least(1::double precision, greatest(0::double precision, v_a))));
  if v_distance_m > 100 then
    raise exception 'You must be within 100 metres of the meeting point (calculated distance: % metres)', round(v_distance_m)::integer using errcode = '22023';
  end if;
  select p.name into v_name from public.profiles p where p.id = v_user_id;
  insert into public.checkins (run_id, user_id, user_name, accuracy_m, distance_to_meeting_m)
  values (v_run.id, v_user_id, coalesce(nullif(trim(v_name), ''), 'Runner'), p_accuracy_m, v_distance_m)
  on conflict (run_id, user_id) do nothing;
  select c.* into v_checkin from public.checkins c where c.run_id = v_run.id and c.user_id = v_user_id;
  return v_checkin;
end;
$$;
revoke all on function public.check_in_to_run(text, double precision, double precision, double precision) from public, anon;
grant execute on function public.check_in_to_run(text, double precision, double precision, double precision) to authenticated;

alter table public.profiles enable row level security;
alter table public.communities enable row level security;
alter table public.memberships enable row level security;
alter table public.runs enable row level security;
alter table public.event_registrations enable row level security;
alter table public.checkins enable row level security;

revoke all on table public.profiles, public.communities, public.memberships, public.runs, public.event_registrations, public.checkins from public, anon, authenticated;
grant select, insert on public.profiles to authenticated;
grant update (name, city, username, avatar_url, bio) on public.profiles to authenticated;
grant select, insert on public.communities to authenticated;
grant update (name, slug, city, description, visibility, meeting_point, latitude, longitude, max_members) on public.communities to authenticated;
grant select, insert, delete on public.memberships to authenticated;
grant select, insert on public.runs to authenticated;
grant update (title, description, starts_at, meeting_lat, meeting_lng, meeting_address, distance_km, checkin_opens_at, checkin_closes_at, status) on public.runs to authenticated;
grant select, insert on public.event_registrations to authenticated;
grant update (status) on public.event_registrations to authenticated;
grant delete on public.event_registrations to authenticated;
grant select on public.checkins to authenticated;

create policy profiles_read_authenticated on public.profiles for select to authenticated using (true);
create policy profiles_insert_self on public.profiles for insert to authenticated with check (id = (select auth.jwt() ->> 'sub'));
create policy profiles_update_self on public.profiles for update to authenticated using (id = (select auth.jwt() ->> 'sub')) with check (id = (select auth.jwt() ->> 'sub'));

create policy communities_read_discoverable on public.communities for select to authenticated
using (visibility = 'public' or created_by = (select auth.jwt() ->> 'sub') or private.is_community_member(id));
create policy communities_create_self on public.communities for insert to authenticated with check (created_by = (select auth.jwt() ->> 'sub'));
create policy communities_update_owner on public.communities for update to authenticated using (created_by = (select auth.jwt() ->> 'sub')) with check (created_by = (select auth.jwt() ->> 'sub'));

create policy memberships_read_self_public_or_organizer on public.memberships for select to authenticated
using (user_id = (select auth.jwt() ->> 'sub') or private.is_community_organizer(community_id)
  or exists (select 1 from public.communities c where c.id = community_id and c.visibility = 'public'));
create policy memberships_join_public_community on public.memberships for insert to authenticated
with check (user_id = (select auth.jwt() ->> 'sub') and role = 'member'
  and exists (select 1 from public.communities c where c.id = community_id and c.visibility = 'public'));
create policy memberships_leave_or_organizer_remove on public.memberships for delete to authenticated
using ((user_id = (select auth.jwt() ->> 'sub') and role = 'member') or private.is_community_organizer(community_id));

create policy runs_read_community on public.runs for select to authenticated using (
  exists (select 1 from public.communities c where c.id = community_id and (c.visibility = 'public' or private.is_community_member(c.id)))
);
create policy runs_create_organizer on public.runs for insert to authenticated
with check (created_by = (select auth.jwt() ->> 'sub') and private.is_community_organizer(community_id));
create policy runs_update_organizer on public.runs for update to authenticated
using (private.is_community_organizer(community_id))
with check (created_by = (select auth.jwt() ->> 'sub') and private.is_community_organizer(community_id));

create policy registrations_read_self_or_organizer on public.event_registrations for select to authenticated
using (user_id = (select auth.jwt() ->> 'sub') or exists (select 1 from public.runs r where r.id = run_id and private.is_community_organizer(r.community_id)));
create policy registrations_insert_self on public.event_registrations for insert to authenticated with check (
  user_id = (select auth.jwt() ->> 'sub') and status = 'registered'
  and exists (select 1 from public.runs r where r.id = run_id and r.status = 'scheduled'
    and r.starts_at > now() and private.is_community_member(r.community_id))
);
create policy registrations_update_self on public.event_registrations for update to authenticated
using (user_id = (select auth.jwt() ->> 'sub'))
with check (user_id = (select auth.jwt() ->> 'sub') and status in ('registered', 'cancelled'));
create policy registrations_delete_self on public.event_registrations for delete to authenticated using (user_id = (select auth.jwt() ->> 'sub'));

create policy checkins_read_self_or_organizer on public.checkins for select to authenticated using (
  user_id = (select auth.jwt() ->> 'sub') or exists (select 1 from public.runs r where r.id = run_id and private.is_community_organizer(r.community_id))
);
