-- Close two policy edge cases before migrating app writes.
drop policy if exists memberships_leave_or_organizer_remove on public.memberships;
create policy memberships_leave_or_organizer_remove
on public.memberships for delete to authenticated
using (
  (user_id = (select auth.jwt() ->> 'sub') and role = 'member')
  or (
    private.is_community_organizer(community_id)
    and user_id <> (select auth.jwt() ->> 'sub')
    and role = 'member'
  )
);

-- created_by is not client-updatable, so co-organizers can edit runs without
-- having to be the original creator of that run.
drop policy if exists runs_update_organizer on public.runs;
create policy runs_update_organizer
on public.runs for update to authenticated
using (private.is_community_organizer(community_id))
with check (private.is_community_organizer(community_id));
