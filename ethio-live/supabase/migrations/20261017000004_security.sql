-- Ethio Live: row level security, privileges, realtime publication and scheduled jobs.
-- Principle: clients may READ what they are entitled to see; they never INSERT/UPDATE/DELETE tables directly.

do $$
declare t text;
begin
  for t in select tablename from pg_tables where schemaname = 'public' loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke insert, update, delete, truncate on public.%I from anon, authenticated', t);
  end loop;
end $$;

-- Profiles: only public columns are selectable; age band, xp internals and status timers stay server-side.
revoke select on profiles from anon, authenticated;
grant select (id, username, display_name, bio, avatar_url, language, interests, level, is_creator, is_verified,
              follower_count, following_count, private_account, rtc_uid, created_at) on profiles to authenticated;
create policy profiles_read on profiles for select to authenticated using (account_status <> 'deleted');

create policy user_private_self on user_private for select to authenticated using (user_id = auth.uid());
create policy user_settings_self on user_settings for select to authenticated using (user_id = auth.uid());
create policy push_tokens_self on push_tokens for select to authenticated using (user_id = auth.uid());

create policy follows_involving_me on follows for select to authenticated
  using (follower_id = auth.uid() or followee_id = auth.uid());
create policy blocks_mine on blocks for select to authenticated using (blocker_id = auth.uid());

create policy live_rooms_read on live_rooms for select to authenticated using (
  (status in ('live', 'ended') or host_id = auth.uid())
  and not is_blocked_between(auth.uid(), host_id));
create policy stream_sessions_host on stream_sessions for select to authenticated using (host_id = auth.uid());
create policy room_participants_read on room_participants for select to authenticated using (
  exists (select 1 from live_rooms r where r.id = room_id and r.status = 'live'));
create policy seat_requests_read on seat_requests for select to authenticated using (
  user_id = auth.uid() or can_manage_room(room_id, auth.uid()));
create policy room_messages_read on room_messages for select to authenticated using (
  (removed_at is null or sender_id = auth.uid())
  and exists (select 1 from live_rooms r where r.id = room_id and r.status = 'live'));
create policy room_moderators_read on room_moderators for select to authenticated using (true);
create policy recently_viewed_self on recently_viewed for select to authenticated using (user_id = auth.uid());

create policy conversations_member on conversations for select to authenticated
  using (auth.uid() in (user_low, user_high));
create policy dm_messages_member on dm_messages for select to authenticated using (
  exists (select 1 from conversations c where c.id = conversation_id and auth.uid() in (c.user_low, c.user_high)));
create policy notifications_self on notifications for select to authenticated using (user_id = auth.uid());

create policy gifts_read on gifts for select to authenticated using (available);
create policy coin_packages_read on coin_packages for select to authenticated using (active);
create policy creator_levels_read on creator_levels for select to authenticated using (true);
create policy badges_read on badges for select to authenticated using (true);
create policy user_badges_read on user_badges for select to authenticated using (true);
create policy events_read on events for select to authenticated using (published);
create policy fan_clubs_read on fan_clubs for select to authenticated using (true);
create policy fan_club_members_read on fan_club_members for select to authenticated using (true);
create policy pk_matches_read on pk_matches for select to authenticated using (true);

create policy wallet_accounts_self on wallet_accounts for select to authenticated using (owner_id = auth.uid());
create policy ledger_entries_self on ledger_entries for select to authenticated using (
  exists (select 1 from wallet_accounts a where a.id = account_id and a.owner_id = auth.uid()));
create policy gift_tx_party on gift_transactions for select to authenticated
  using (sender_id = auth.uid() or recipient_id = auth.uid());
create policy payment_orders_self on payment_orders for select to authenticated using (user_id = auth.uid());
create policy payout_requests_self on payout_requests for select to authenticated using (creator_id = auth.uid());
create policy bean_settlements_self on bean_settlements for select to authenticated using (creator_id = auth.uid());

create policy reports_reporter on reports for select to authenticated using (reporter_id = auth.uid());
create policy moderation_actions_target on moderation_actions for select to authenticated using (target_user_id = auth.uid());
create policy appeals_self on appeals for select to authenticated using (user_id = auth.uid());
create policy support_self on support_requests for select to authenticated using (user_id = auth.uid());
-- staff_members, audit_logs, platform_config, ledger_transactions, payment_webhook_events, ledger_adjustment_requests,
-- free_gift_sends, pk_score_events and reserved_usernames have RLS enabled and no client policy: functions only.
revoke select on staff_members, audit_logs, platform_config, ledger_transactions, payment_webhook_events,
  ledger_adjustment_requests, free_gift_sends, pk_score_events, reserved_usernames from anon, authenticated;
revoke all on all tables in schema public from anon;

-- ---------------------------------------------------------------- function privileges
revoke execute on all functions in schema public from public, anon;
grant execute on all functions in schema public to authenticated;

-- Internal helpers and service-only operations are not callable by app users.
do $$
declare f text;
begin
  foreach f in array array[
    'post_entry(uuid,uuid,bigint,text)', 'wallet_account(uuid,wallet_kind)', 'ensure_user_wallets(uuid)',
    'my_balances(uuid)', 'end_room_internal(uuid,text)', 'pk_close(uuid,boolean)', 'assign_free_seat(uuid,uuid)',
    'add_xp(uuid,bigint)', 'audit(uuid,text,text,text,jsonb)', 'notify(uuid,text,uuid,jsonb)', 'recompute_viewers(uuid)',
    'user_age(uuid)', 'is_adult(uuid)', 'require_active(uuid)', 'check_rate(bigint,text)',
    'credit_payment_order(uuid,text,bigint,text)', 'fail_payment_order(uuid,text)', 'refund_payment_order(uuid,text)',
    'settle_due_beans(integer)', 'claim_push_batch(integer)', 'run_maintenance()', 'bootstrap_superadmin(text)',
    'audit_logs_immutable()', 'ledger_immutable()', 'ledger_check_balanced()', 'follows_counter()'] loop
    execute format('revoke execute on function public.%s from authenticated', f);
    execute format('grant execute on function public.%s to service_role', f);
  end loop;
end $$;

grant execute on all functions in schema public to service_role;
alter default privileges in schema public revoke execute on functions from public, anon;

-- ---------------------------------------------------------------- realtime
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table live_rooms, room_participants, room_messages, seat_requests,
      pk_matches, dm_messages, conversations, notifications;
  end if;
end $$;

-- ---------------------------------------------------------------- scheduled jobs
-- Supabase: enable the pg_cron extension in the dashboard (Database > Extensions) before applying, or run
-- `select cron.schedule(...)` below by hand afterwards. Locally without pg_cron this block is skipped.
do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
    create extension if not exists pg_cron;
    perform cron.schedule('ethiolive-maintenance', '20 seconds', 'select public.run_maintenance()');
  end if;
end $$;
