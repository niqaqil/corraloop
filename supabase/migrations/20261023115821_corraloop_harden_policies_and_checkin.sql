-- Harden client column privileges and make check-in boundaries database-controlled.
create index if not exists communities_created_by_idx on public.communities (created_by);
create index if not exists runs_created_by_idx on public.runs (created_by);

revoke all on function private.update_community_member_count() from public, anon, authenticated;
revoke all on function private.add_community_creator_membership() from public, anon, authenticated;
revoke all on function private.set_updated_at() from public, anon, authenticated;

revoke insert on public.profiles from authenticated;
grant insert (id, name, city, username, avatar_url, bio) on public.profiles to authenticated;
revoke insert on public.communities from authenticated;
grant insert (name, slug, city, description, created_by, visibility, meeting_point, latitude, longitude, max_members) on public.communities to authenticated;
revoke insert on public.memberships from authenticated;
grant insert (user_id, community_id, role) on public.memberships to authenticated;
revoke insert on public.runs from authenticated;
grant insert (community_id, title, description, starts_at, meeting_lat, meeting_lng, meeting_address, distance_km, checkin_opens_at, checkin_closes_at, status, created_by) on public.runs to authenticated;
revoke insert on public.event_registrations from authenticated;
grant insert (run_id, user_id, status) on public.event_registrations to authenticated;

create or replace function private.sync_run_checkin_window()
returns trigger language plpgsql set search_path = ''
as $$
begin
  new.checkin_opens_at := new.starts_at - interval '30 minutes';
  new.checkin_closes_at := new.starts_at + interval '15 minutes';
  return new;
end;
$$;
revoke all on function private.sync_run_checkin_window() from public, anon, authenticated;
create trigger runs_sync_checkin_window
before insert or update of starts_at, checkin_opens_at, checkin_closes_at on public.runs
for each row execute function private.sync_run_checkin_window();

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
  v_opens_at := v_run.starts_at - interval '30 minutes';
  v_closes_at := v_run.starts_at + interval '15 minutes';
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
