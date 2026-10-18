-- Ethio Live: server-side business logic.
-- Every sensitive write is a SECURITY DEFINER function that derives the caller from auth.uid(),
-- validates eligibility, and performs the change in one database transaction.
-- Errors are raised as short machine codes (e.g. 'insufficient_balance') that the app localizes.

-- ================================================================ helpers
create or replace function me() returns uuid
language plpgsql stable set search_path = public as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'not_authenticated'; end if;
  return v;
end $$;

create or replace function user_age(p_user uuid) returns int
language sql stable set search_path = public as $$
  select extract(year from age((now() at time zone 'Africa/Addis_Ababa')::date, date_of_birth))::int
  from user_private where user_id = p_user
$$;

create or replace function is_adult(p_user uuid) returns boolean
language sql stable set search_path = public as $$
  select coalesce(user_age(p_user) >= cfg_int('adult_age'), false)
$$;

create or replace function is_blocked_between(a uuid, b uuid) returns boolean
language sql stable set search_path = public as $$
  select exists (select 1 from blocks where (blocker_id = a and blocked_id = b) or (blocker_id = b and blocked_id = a))
$$;

-- Returns the caller's profile, or raises when the account cannot act.
create or replace function require_active(p_user uuid) returns profiles
language plpgsql stable set search_path = public as $$
declare p profiles;
begin
  select * into p from profiles where id = p_user;
  if not found then raise exception 'profile_required'; end if;
  if p.account_status = 'deleted' then raise exception 'account_deleted'; end if;
  if p.account_status = 'banned' then raise exception 'account_banned'; end if;
  if p.account_status = 'suspended' and (p.status_until is null or p.status_until > now()) then
    raise exception 'account_suspended';
  end if;
  return p;
end $$;

create or replace function audit(p_actor uuid, p_action text, p_target_type text, p_target_id text, p_details jsonb default '{}')
returns void language sql set search_path = public as $$
  insert into audit_logs (actor_id, action, target_type, target_id, details)
  values (p_actor, p_action, p_target_type, p_target_id, coalesce(p_details, '{}'))
$$;

create or replace function notify(p_user uuid, p_type text, p_actor uuid, p_payload jsonb)
returns void language plpgsql set search_path = public as $$
declare s user_settings;
begin
  if p_user is null or p_user = p_actor then return; end if;
  select * into s from user_settings where user_id = p_user;
  if found and (
       (p_type = 'follow' and not s.notify_follows) or
       (p_type = 'live_start' and not s.notify_live_start) or
       (p_type = 'gift' and not s.notify_gifts) or
       (p_type = 'message' and not s.notify_messages) or
       (p_type = 'pk_invite' and not s.notify_pk) or
       (p_type = 'guest_invite' and not s.notify_guest_invites)) then
    return;
  end if;
  insert into notifications (user_id, type, actor_id, payload) values (p_user, p_type, p_actor, coalesce(p_payload, '{}'));
end $$;

-- Sliding-window rate limit check against a timestamp column. Raises 'rate_limited'.
create or replace function check_rate(p_count bigint, p_config_key text) returns void
language plpgsql stable set search_path = public as $$
declare v jsonb;
begin
  select value into v from platform_config where key = p_config_key;
  if p_count >= (v ->> 'count')::int then raise exception 'rate_limited'; end if;
end $$;

create or replace function rate_window(p_config_key text) returns interval
language sql stable set search_path = public as $$
  select make_interval(secs => (value ->> 'seconds')::int) from platform_config where key = p_config_key
$$;

-- ================================================================ ledger primitives
create or replace function wallet_account(p_owner uuid, p_kind wallet_kind) returns uuid
language plpgsql set search_path = public as $$
declare v uuid;
begin
  if p_kind::text like 'sys\_%' then
    select id into v from wallet_accounts where owner_id is null and kind = p_kind;
    return v;
  end if;
  select id into v from wallet_accounts where owner_id = p_owner and kind = p_kind;
  if v is null then
    insert into wallet_accounts (owner_id, kind) values (p_owner, p_kind)
    on conflict (owner_id, kind) where owner_id is not null do nothing;
    select id into v from wallet_accounts where owner_id = p_owner and kind = p_kind;
  end if;
  return v;
end $$;

-- Posts one leg of a ledger transaction. User account rows are locked by the UPDATE, so concurrent
-- spends from the same wallet serialize and the CHECK constraint makes overdrafts impossible.
-- System accounts are not row-updated (that row would be a global hot spot); their balance is the
-- sum of their entries (see view system_account_balances).
create or replace function post_entry(p_tx uuid, p_account uuid, p_amount bigint, p_unit text)
returns bigint language plpgsql set search_path = public as $$
declare
  v_balance bigint;
  v_kind wallet_kind;
begin
  if p_amount = 0 then return null; end if;
  select kind into v_kind from wallet_accounts where id = p_account;
  if v_kind::text like 'sys\_%' then
    insert into ledger_entries (transaction_id, account_id, amount, unit, balance_after)
    values (p_tx, p_account, p_amount, p_unit, 0);
    return null;
  end if;
  update wallet_accounts set balance = balance + p_amount, updated_at = now()
  where id = p_account and balance + p_amount >= 0
  returning balance into v_balance;
  if v_balance is null then raise exception 'insufficient_balance'; end if;
  insert into ledger_entries (transaction_id, account_id, amount, unit, balance_after)
  values (p_tx, p_account, p_amount, p_unit, v_balance);
  return v_balance;
end $$;

create or replace view system_account_balances as
  select a.kind, coalesce(sum(e.amount), 0) as balance
  from wallet_accounts a left join ledger_entries e on e.account_id = a.id
  where a.owner_id is null group by a.kind;

create or replace function ensure_user_wallets(p_user uuid) returns void
language plpgsql set search_path = public as $$
begin
  perform wallet_account(p_user, 'user_coins');
  perform wallet_account(p_user, 'user_promo');
  perform wallet_account(p_user, 'creator_beans_pending');
  perform wallet_account(p_user, 'creator_beans');
  perform wallet_account(p_user, 'payout_hold');
end $$;

-- ================================================================ accounts
create or replace function complete_signup(p_username text, p_display_name text, p_date_of_birth date,
                                           p_language text, p_terms_version text)
returns profiles language plpgsql security definer set search_path = public, extensions as $$
declare
  v_uid uuid := me();
  v_age int;
  v_profile profiles;
begin
  if exists (select 1 from profiles where id = v_uid) then raise exception 'already_registered'; end if;
  if p_terms_version is null or char_length(p_terms_version) = 0 then raise exception 'terms_required'; end if;
  if p_date_of_birth is null or p_date_of_birth > current_date then raise exception 'invalid_birth_date'; end if;
  v_age := extract(year from age(current_date, p_date_of_birth))::int;
  if v_age < cfg_int('min_signup_age') then raise exception 'age_ineligible'; end if;
  if v_age > 120 then raise exception 'invalid_birth_date'; end if;
  perform validate_username(p_username);

  insert into profiles (id, username, display_name, language, age_band)
  values (v_uid, p_username, btrim(p_display_name), coalesce(p_language, 'en'),
          case when v_age >= cfg_int('adult_age') then 'adult'::age_band else 'minor'::age_band end)
  returning * into v_profile;
  insert into user_private (user_id, date_of_birth, terms_version) values (v_uid, p_date_of_birth, p_terms_version);
  -- Younger users get conservative defaults: only followers can message them, never shown in Nearby.
  insert into user_settings (user_id, dm_policy)
  values (v_uid, case when v_age >= cfg_int('adult_age') then 'everyone'::dm_policy else 'followers'::dm_policy end);
  perform ensure_user_wallets(v_uid);
  perform audit(v_uid, 'account.signup', 'user', v_uid::text, jsonb_build_object('age_band', v_profile.age_band));
  return v_profile;
end $$;

create or replace function validate_username(p_username text) returns void
language plpgsql stable set search_path = public, extensions as $$
declare v_lower text := lower(coalesce(p_username, ''));
begin
  if v_lower !~ '^[a-z0-9_.]{3,24}$' then raise exception 'username_invalid'; end if;
  if exists (select 1 from reserved_usernames where username = v_lower)
     or v_lower ~ '(ethio_?live|official|admin|support|moderator)' then
    raise exception 'username_reserved';
  end if;
  -- Block look-alikes of verified accounts (e.g. "abebe.official" vs "abebe_official", "abebe1" vs "abebe").
  if exists (select 1 from profiles where is_verified and id <> coalesce(auth.uid(), '00000000-0000-0000-0000-000000000000')
             and regexp_replace(lower(username::text), '[^a-z]', '', 'g') = regexp_replace(v_lower, '[^a-z]', '', 'g')) then
    raise exception 'username_impersonation';
  end if;
  if exists (select 1 from profiles where username = v_lower and id <> coalesce(auth.uid(), '00000000-0000-0000-0000-000000000000')) then
    raise exception 'username_taken';
  end if;
end $$;

create or replace function my_account() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  return (
    select jsonb_build_object(
      'profile', to_jsonb(p),
      'private', jsonb_build_object('date_of_birth', up.date_of_birth, 'kyc_status', up.kyc_status,
                                    'terms_version', up.terms_version, 'is_adult', is_adult(v_uid)),
      'settings', to_jsonb(s),
      'staff_role', (select role from staff_members where user_id = v_uid and active))
    from profiles p join user_private up on up.user_id = p.id left join user_settings s on s.user_id = p.id
    where p.id = v_uid);
end $$;

create or replace function update_profile(p_display_name text, p_bio text, p_avatar_url text, p_language text,
                                          p_interests text[], p_private_account boolean)
returns profiles language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v profiles;
begin
  perform require_active(v_uid);
  if p_avatar_url is not null and p_avatar_url !~ '^https://' then raise exception 'avatar_url_invalid'; end if;
  if coalesce(array_length(p_interests, 1), 0) > 10 then raise exception 'too_many_interests'; end if;
  update profiles set display_name = btrim(p_display_name), bio = coalesce(p_bio, ''), avatar_url = p_avatar_url,
    language = coalesce(p_language, language), interests = coalesce(p_interests, '{}'),
    private_account = coalesce(p_private_account, private_account), updated_at = now()
  where id = v_uid returning * into v;
  return v;
end $$;

create or replace function change_username(p_username text) returns profiles
language plpgsql security definer set search_path = public, extensions as $$
declare v_uid uuid := me(); v profiles;
begin
  perform require_active(v_uid);
  perform validate_username(p_username);
  if exists (select 1 from audit_logs where actor_id = v_uid and action = 'account.username_changed'
             and created_at > now() - interval '30 days') then
    raise exception 'username_change_too_soon';
  end if;
  select * into v from profiles where id = v_uid;
  if v.is_verified then raise exception 'verified_username_locked'; end if;
  update profiles set username = p_username, updated_at = now() where id = v_uid returning * into v;
  perform audit(v_uid, 'account.username_changed', 'user', v_uid::text, jsonb_build_object('username', p_username));
  return v;
end $$;

create or replace function update_settings(p jsonb) returns user_settings
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v user_settings; v_adult boolean := is_adult(me());
begin
  update user_settings set
    dm_policy = coalesce((p ->> 'dm_policy')::dm_policy, dm_policy),
    notify_follows = coalesce((p ->> 'notify_follows')::boolean, notify_follows),
    notify_live_start = coalesce((p ->> 'notify_live_start')::boolean, notify_live_start),
    notify_gifts = coalesce((p ->> 'notify_gifts')::boolean, notify_gifts),
    notify_messages = coalesce((p ->> 'notify_messages')::boolean, notify_messages),
    notify_pk = coalesce((p ->> 'notify_pk')::boolean, notify_pk),
    notify_guest_invites = coalesce((p ->> 'notify_guest_invites')::boolean, notify_guest_invites),
    -- Minors can never appear in Nearby and cannot open DMs to everyone.
    show_in_nearby = case when v_adult then coalesce((p ->> 'show_in_nearby')::boolean, show_in_nearby) else false end,
    show_gift_highlights = coalesce((p ->> 'show_gift_highlights')::boolean, show_gift_highlights),
    recommendations_personalized = coalesce((p ->> 'recommendations_personalized')::boolean, recommendations_personalized),
    updated_at = now()
  where user_id = v_uid returning * into v;
  if not v_adult and v.dm_policy = 'everyone' then
    update user_settings set dm_policy = 'followers' where user_id = v_uid returning * into v;
  end if;
  return v;
end $$;

alter table user_settings add column nearby_region text;

-- Called only after the user grants location permission in the app; stores a coarse city/region name, never coordinates.
create or replace function set_nearby_region(p_region text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_region is not null and char_length(p_region) > 60 then raise exception 'region_invalid'; end if;
  update user_settings set nearby_region = p_region where user_id = me();
end $$;

create or replace function register_push_token(p_token text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if char_length(p_token) not between 20 and 4096 then raise exception 'token_invalid'; end if;
  insert into push_tokens (token, user_id) values (p_token, me())
  on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
end $$;

create or replace function remove_push_token(p_token text) returns void
language sql security definer set search_path = public as $$
  delete from push_tokens where token = p_token and user_id = me()
$$;

-- Soft deletion: personal data is erased immediately; financial records are kept pseudonymised as
-- required for accounting. The delete-account edge function then soft-deletes the auth identity.
create or replace function delete_my_account() returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v_room uuid;
begin
  for v_room in select id from live_rooms where host_id = v_uid and status in ('preparing', 'live') loop
    perform end_room_internal(v_room, 'account_deleted');
  end loop;
  update profiles set username = 'deleted_' || substr(replace(id::text, '-', ''), 1, 12),
    display_name = 'Deleted user', bio = '', avatar_url = null, interests = '{}', account_status = 'deleted',
    private_account = true, updated_at = now()
  where id = v_uid;
  update user_private set deletion_requested_at = now(), payout_method = null, kyc_reference = null where user_id = v_uid;
  delete from follows where follower_id = v_uid or followee_id = v_uid;
  delete from push_tokens where user_id = v_uid;
  delete from dm_messages where sender_id = v_uid;
  delete from recently_viewed where user_id = v_uid;
  delete from fan_club_members where user_id = v_uid;
  update room_messages set body = '[removed]', removed_at = now() where sender_id = v_uid and removed_at is null;
  perform audit(v_uid, 'account.deleted', 'user', v_uid::text, '{}');
end $$;

create or replace function export_my_data() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  return jsonb_build_object(
    'account', my_account(),
    'following', (select coalesce(jsonb_agg(followee_id), '[]') from follows where follower_id = v_uid),
    'blocked', (select coalesce(jsonb_agg(blocked_id), '[]') from blocks where blocker_id = v_uid),
    'messages_sent', (select coalesce(jsonb_agg(jsonb_build_object('conversation', conversation_id, 'body', body, 'at', created_at)), '[]')
                      from dm_messages where sender_id = v_uid),
    'gifts_sent', (select coalesce(jsonb_agg(to_jsonb(g)), '[]') from gift_transactions g where sender_id = v_uid),
    'gifts_received', (select coalesce(jsonb_agg(to_jsonb(g)), '[]') from gift_transactions g where recipient_id = v_uid),
    'ledger', (select coalesce(jsonb_agg(jsonb_build_object('kind', a.kind, 'amount', e.amount, 'unit', e.unit,
                                                            'balance_after', e.balance_after, 'at', e.created_at)), '[]')
               from ledger_entries e join wallet_accounts a on a.id = e.account_id where a.owner_id = v_uid),
    'reports_filed', (select coalesce(jsonb_agg(jsonb_build_object('type', target_type, 'reason', reason, 'status', status, 'at', created_at)), '[]')
                      from reports where reporter_id = v_uid),
    'exported_at', now());
end $$;

-- ================================================================ social graph
create or replace function follows_counter() returns trigger language plpgsql set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    update profiles set following_count = following_count + 1 where id = new.follower_id;
    update profiles set follower_count = follower_count + 1 where id = new.followee_id;
  elsif tg_op = 'DELETE' then
    update profiles set following_count = greatest(following_count - 1, 0) where id = old.follower_id;
    update profiles set follower_count = greatest(follower_count - 1, 0) where id = old.followee_id;
  end if;
  return null;
end $$;
create trigger follows_counter after insert or delete on follows for each row execute function follows_counter();

create or replace function follow_user(p_target uuid) returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v_inserted int;
begin
  perform require_active(v_uid);
  if p_target = v_uid then raise exception 'cannot_follow_self'; end if;
  if not exists (select 1 from profiles where id = p_target and account_status not in ('deleted', 'banned')) then
    raise exception 'user_not_found';
  end if;
  if is_blocked_between(v_uid, p_target) then raise exception 'blocked'; end if;
  insert into follows (follower_id, followee_id) values (v_uid, p_target) on conflict do nothing;
  get diagnostics v_inserted = row_count;
  if v_inserted > 0 then
    perform notify(p_target, 'follow', v_uid, '{}');
    update stream_sessions s set new_followers = new_followers + 1
    from live_rooms r where r.id = s.room_id and r.host_id = p_target and r.status = 'live';
  end if;
end $$;

create or replace function unfollow_user(p_target uuid) returns void
language sql security definer set search_path = public as $$
  delete from follows where follower_id = me() and followee_id = p_target
$$;

create or replace function block_user(p_target uuid) returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  if p_target = v_uid then raise exception 'cannot_block_self'; end if;
  insert into blocks (blocker_id, blocked_id) values (v_uid, p_target) on conflict do nothing;
  delete from follows where (follower_id = v_uid and followee_id = p_target) or (follower_id = p_target and followee_id = v_uid);
  -- A blocked user loses their seat in and access to the blocker's live room.
  update room_participants rp set left_at = now(), kicked_at = now(), seat_index = null, role = 'audience'
  from live_rooms r where r.id = rp.room_id and r.host_id = v_uid and r.status in ('preparing', 'live')
    and rp.user_id = p_target and rp.left_at is null;
  perform audit(v_uid, 'user.block', 'user', p_target::text, '{}');
end $$;

create or replace function unblock_user(p_target uuid) returns void
language sql security definer set search_path = public as $$
  delete from blocks where blocker_id = me() and blocked_id = p_target
$$;

create or replace function report_content(p_target_type report_target, p_target_id text, p_reason report_reason, p_details text)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := me();
  v_target_user uuid;
  v_evidence jsonb := '{}';
  v_id uuid;
begin
  perform require_active(v_uid);
  if (select count(*) from reports where reporter_id = v_uid and created_at > now() - interval '1 hour') >= 20 then
    raise exception 'rate_limited';
  end if;
  case p_target_type
    when 'user' then
      v_target_user := p_target_id::uuid;
    when 'room' then
      select host_id, jsonb_build_object('title', title, 'status', status, 'mode', mode)
      into v_target_user, v_evidence from live_rooms where id = p_target_id::uuid;
    when 'room_message' then
      select sender_id, jsonb_build_object('body', body, 'room_id', room_id, 'sent_at', created_at)
      into v_target_user, v_evidence from room_messages where id = p_target_id::bigint;
    when 'dm_message' then
      -- Only a participant of the conversation may report (and thereby disclose) a private message.
      select m.sender_id, jsonb_build_object('body', m.body, 'conversation_id', m.conversation_id, 'sent_at', m.created_at)
      into v_target_user, v_evidence
      from dm_messages m join conversations c on c.id = m.conversation_id
      where m.id = p_target_id::bigint and v_uid in (c.user_low, c.user_high);
  end case;
  if v_target_user is null then raise exception 'target_not_found'; end if;
  if v_target_user = v_uid then raise exception 'cannot_report_self'; end if;
  insert into reports (reporter_id, target_type, target_id, target_user_id, reason, details, evidence, priority)
  values (v_uid, p_target_type, p_target_id, v_target_user, p_reason, coalesce(p_details, ''), v_evidence,
          case when p_reason in ('minor_safety', 'self_harm', 'illegal') then 1
               when p_reason in ('nudity', 'violence', 'hate') then 2 else 3 end)
  returning id into v_id;
  return v_id;
end $$;

create or replace function submit_appeal(p_action uuid, p_message text) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := auth.uid(); v_id uuid;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if not exists (select 1 from moderation_actions where id = p_action and target_user_id = v_uid) then
    raise exception 'action_not_found';
  end if;
  insert into appeals (user_id, moderation_action_id, message) values (v_uid, p_action, p_message) returning id into v_id;
  return v_id;
end $$;

create or replace function submit_support_request(p_topic text, p_message text) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v_id uuid;
begin
  if (select count(*) from support_requests where user_id = v_uid and created_at > now() - interval '1 day') >= 10 then
    raise exception 'rate_limited';
  end if;
  insert into support_requests (user_id, topic, message) values (v_uid, p_topic, p_message) returning id into v_id;
  return v_id;
end $$;

-- ================================================================ discovery & search
create or replace function room_card_json(r live_rooms) returns jsonb
language sql stable set search_path = public as $$
  select jsonb_build_object(
    'id', r.id, 'mode', r.mode, 'title', r.title, 'category', r.category, 'language', r.language,
    'hashtags', r.hashtags, 'cover_url', r.cover_url, 'viewer_count', r.viewer_count, 'started_at', r.started_at,
    'audience', r.audience, 'max_seats', r.max_seats, 'status', r.status,
    'host', jsonb_build_object('id', p.id, 'username', p.username, 'display_name', p.display_name,
                               'avatar_url', p.avatar_url, 'level', p.level, 'is_verified', p.is_verified),
    'in_pk', exists (select 1 from pk_matches m where m.status = 'running' and r.id in (m.room_a, m.room_b)))
  from profiles p where p.id = r.host_id
$$;

create or replace function discover_rooms(p_tab text, p_category text default null, p_language text default null,
                                          p_limit int default 20, p_offset int default 0)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_settings user_settings;
  v_profile profiles;
  v_result jsonb;
begin
  p_limit := least(greatest(p_limit, 1), 50);
  select * into v_settings from user_settings where user_id = v_uid;
  select * into v_profile from profiles where id = v_uid;
  select coalesce(jsonb_agg(card order by ord), '[]') into v_result from (
  select room_card_json(r) as card, row_number() over (order by
    case when p_tab = 'new' then extract(epoch from r.started_at) end desc nulls last,
    case when p_tab = 'recommended' and coalesce(v_settings.recommendations_personalized, true) and v_profile.id is not null
         then (case when r.language = v_profile.language then 1 else 0 end)
            + (case when r.category = any (v_profile.interests) then 1 else 0 end) end desc nulls last,
    r.viewer_count desc, r.started_at desc, r.id) as ord
  from live_rooms r join profiles h on h.id = r.host_id
  where r.status = 'live'
    and h.account_status in ('active', 'warned')
    and (p_category is null or r.category = p_category)
    and (p_language is null or r.language = p_language)
    and (v_uid is null or not is_blocked_between(v_uid, r.host_id))
    and (r.audience = 'public' or r.host_id = v_uid
         or exists (select 1 from follows f where f.follower_id = v_uid and f.followee_id = r.host_id))
    and case p_tab
          when 'following' then exists (select 1 from follows f where f.follower_id = v_uid and f.followee_id = r.host_id)
          when 'recent' then exists (select 1 from recently_viewed rv where rv.user_id = v_uid and rv.room_id = r.id)
          when 'audio' then r.mode = 'audio'
          when 'multi' then r.mode = 'multi_guest'
          when 'pk' then exists (select 1 from pk_matches m where m.status = 'running' and r.id in (m.room_a, m.room_b))
          when 'nearby' then v_settings.nearby_region is not null and exists (
            select 1 from user_settings hs where hs.user_id = r.host_id and hs.show_in_nearby
              and hs.nearby_region = v_settings.nearby_region)
          else true
        end
  order by ord
  limit p_limit offset greatest(p_offset, 0)) x;
  return v_result;
end $$;

create or replace function search_users(p_query text, p_limit int default 20, p_offset int default 0)
returns table (id uuid, username text, display_name text, avatar_url text, level int, is_verified boolean,
               is_creator boolean, follower_count int, is_live boolean)
language sql stable security definer set search_path = public, extensions as $$
  select p.id, p.username::text, p.display_name, p.avatar_url, p.level, p.is_verified, p.is_creator, p.follower_count,
         exists (select 1 from live_rooms r where r.host_id = p.id and r.status = 'live')
  from profiles p
  where char_length(btrim(p_query)) >= 1
    and p.account_status in ('active', 'warned')
    and (auth.uid() is null or not is_blocked_between(auth.uid(), p.id))
    and (p.username::text ilike '%' || btrim(p_query) || '%' or p.display_name ilike '%' || btrim(p_query) || '%'
         or p.username::text % btrim(p_query))
  order by (lower(p.username::text) = lower(btrim(p_query))) desc,
           similarity(p.username::text, btrim(p_query)) desc, p.follower_count desc
  limit least(greatest(p_limit, 1), 50) offset greatest(p_offset, 0)
$$;

create or replace function search_rooms(p_query text, p_category text default null, p_language text default null,
                                        p_limit int default 20, p_offset int default 0)
returns jsonb language sql stable security definer set search_path = public, extensions as $$
  select coalesce(jsonb_agg(card order by ord), '[]') from (
  select room_card_json(r) as card, row_number() over (order by r.viewer_count desc, r.id) as ord
  from live_rooms r join profiles h on h.id = r.host_id
  where r.status = 'live' and h.account_status in ('active', 'warned')
    and r.audience = 'public'
    and (p_category is null or r.category = p_category)
    and (p_language is null or r.language = p_language)
    and (auth.uid() is null or not is_blocked_between(auth.uid(), r.host_id))
    and (r.title ilike '%' || btrim(p_query) || '%'
         or lower(ltrim(btrim(p_query), '#')) = any (select lower(t) from unnest(r.hashtags) t)
         or h.username::text ilike '%' || btrim(p_query) || '%')
  order by ord
  limit least(greatest(p_limit, 1), 50) offset greatest(p_offset, 0)) x
$$;

create or replace function trending_hashtags(p_limit int default 15)
returns table (tag text, live_rooms bigint) language sql stable security definer set search_path = public as $$
  select lower(t), count(*) from live_rooms r, unnest(r.hashtags) t
  where r.status = 'live' and r.audience = 'public' group by lower(t) order by 2 desc, 1 limit p_limit
$$;

create or replace function trending_creators(p_limit int default 20)
returns table (id uuid, username text, display_name text, avatar_url text, level int, is_verified boolean,
               score bigint, is_live boolean)
language sql stable security definer set search_path = public as $$
  select p.id, p.username::text, p.display_name, p.avatar_url, p.level, p.is_verified,
         coalesce(g.coins, 0) + coalesce(f.new_followers, 0) * 10 as score,
         exists (select 1 from live_rooms r where r.host_id = p.id and r.status = 'live')
  from profiles p
  left join (select recipient_id, sum(coins_spent + promo_coins_spent) coins from gift_transactions
             where created_at > now() - interval '7 days' group by recipient_id) g on g.recipient_id = p.id
  left join (select followee_id, count(*) new_followers from follows
             where created_at > now() - interval '7 days' group by followee_id) f on f.followee_id = p.id
  where p.account_status in ('active', 'warned') and (g.coins is not null or f.new_followers is not null)
    and (auth.uid() is null or not is_blocked_between(auth.uid(), p.id))
  order by score desc limit least(p_limit, 50)
$$;

create or replace function public_profile(p_user uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_uid uuid := auth.uid(); p profiles; v_follows boolean; v_followed_by boolean; v_blocked boolean;
begin
  select * into p from profiles where id = p_user;
  if not found or p.account_status = 'deleted' then raise exception 'user_not_found'; end if;
  v_blocked := v_uid is not null and exists (select 1 from blocks where blocker_id = v_uid and blocked_id = p_user);
  if v_uid is not null and exists (select 1 from blocks where blocker_id = p_user and blocked_id = v_uid) then
    raise exception 'user_not_found';
  end if;
  v_follows := exists (select 1 from follows where follower_id = v_uid and followee_id = p_user);
  v_followed_by := exists (select 1 from follows where follower_id = p_user and followee_id = v_uid);
  return jsonb_build_object(
    'id', p.id, 'username', p.username, 'display_name', p.display_name, 'avatar_url', p.avatar_url,
    'bio', case when p.private_account and not v_follows and v_uid <> p.id then '' else p.bio end,
    'language', p.language, 'interests', p.interests, 'level', p.level, 'xp', p.xp,
    'is_creator', p.is_creator, 'is_verified', p.is_verified, 'follower_count', p.follower_count,
    'following_count', p.following_count, 'private_account', p.private_account,
    'account_status', case when p.account_status = 'banned' then 'banned' else 'active' end,
    'is_following', v_follows, 'follows_you', v_followed_by, 'is_blocked', v_blocked,
    'badges', (select coalesce(jsonb_agg(jsonb_build_object('id', b.id, 'name', b.name, 'icon_key', b.icon_key)), '[]')
               from user_badges ub join badges b on b.id = ub.badge_id where ub.user_id = p.id),
    'live_room', (select room_card_json(r) from live_rooms r where r.host_id = p.id and r.status = 'live' limit 1),
    'gift_highlights', case when coalesce((select show_gift_highlights from user_settings where user_id = p.id), true) then
      (select coalesce(jsonb_agg(x), '[]') from (
         select g.gift_id, sum(g.quantity) as count from gift_transactions g where g.recipient_id = p.id
         group by g.gift_id order by 2 desc limit 6) x) else '[]'::jsonb end,
    'recent_lives', case when p.private_account and not v_follows and v_uid <> p.id then '[]'::jsonb else
      (select coalesce(jsonb_agg(x), '[]') from (
         select r.id, r.title, r.category, r.mode, r.started_at, s.duration_seconds, s.peak_viewers
         from live_rooms r join stream_sessions s on s.room_id = r.id
         where r.host_id = p.id and r.status = 'ended' order by r.started_at desc limit 10) x) end,
    'fan_club', (select jsonb_build_object('id', c.id, 'name', c.name, 'badge_label', c.badge_label,
                   'member_count', (select count(*) from fan_club_members m where m.club_id = c.id),
                   'is_member', exists (select 1 from fan_club_members m where m.club_id = c.id and m.user_id = v_uid))
                 from fan_clubs c where c.creator_id = p.id));
end $$;

create or replace function list_follows(p_user uuid, p_direction text, p_limit int default 30, p_offset int default 0)
returns table (id uuid, username text, display_name text, avatar_url text, level int, followed_at timestamptz)
language sql stable security definer set search_path = public as $$
  select p.id, p.username::text, p.display_name, p.avatar_url, p.level, f.created_at
  from follows f join profiles p on p.id = case when p_direction = 'followers' then f.follower_id else f.followee_id end
  where (case when p_direction = 'followers' then f.followee_id else f.follower_id end) = p_user
    and p.account_status <> 'deleted'
    and (not (select private_account from profiles where id = p_user) or p_user = auth.uid()
         or exists (select 1 from follows x where x.follower_id = auth.uid() and x.followee_id = p_user))
  order by f.created_at desc limit least(p_limit, 100) offset p_offset
$$;

create or replace function list_blocked() returns table (id uuid, username text, display_name text, avatar_url text, blocked_at timestamptz)
language sql stable security definer set search_path = public as $$
  select p.id, p.username::text, p.display_name, p.avatar_url, b.created_at
  from blocks b join profiles p on p.id = b.blocked_id where b.blocker_id = me() order by b.created_at desc
$$;

-- ================================================================ live rooms
create or replace function recompute_viewers(p_room uuid) returns int
language plpgsql set search_path = public as $$
declare v int;
begin
  select count(*) into v from room_participants
  where room_id = p_room and left_at is null and role <> 'host'
    and last_seen_at > now() - make_interval(secs => cfg_int('presence_timeout_seconds')::int);
  update live_rooms set viewer_count = v, peak_viewers = greatest(peak_viewers, v) where id = p_room and viewer_count <> v;
  return v;
end $$;

create or replace function start_live(p_mode room_mode, p_title text, p_category text, p_language text,
                                      p_audience room_audience, p_hashtags text[], p_max_seats int, p_cover_url text)
returns live_rooms language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); p profiles; r live_rooms; v_seats int;
begin
  p := require_active(v_uid);
  if not is_adult(v_uid) then raise exception 'broadcast_age_restricted'; end if;
  if p.broadcast_banned_until is not null and p.broadcast_banned_until > now() then raise exception 'broadcast_banned'; end if;
  if p_cover_url is not null and p_cover_url !~ '^https://' then raise exception 'cover_url_invalid'; end if;
  v_seats := case p_mode
    when 'gaming' then 1
    when 'video' then coalesce(p_max_seats, 4)
    when 'audio' then coalesce(p_max_seats, 9)
    when 'multi_guest' then coalesce(p_max_seats, 6) end;
  if v_seats not in (1, 2, 4, 6, 9) or (p_mode = 'multi_guest' and v_seats < 2) then raise exception 'invalid_seat_count'; end if;
  -- Close a previous attempt that never went live (e.g. the app was killed while preparing).
  update live_rooms set status = 'failed', ended_at = now(), end_reason = 'superseded'
  where host_id = v_uid and status = 'preparing';
  if exists (select 1 from live_rooms where host_id = v_uid and status = 'live') then raise exception 'already_live'; end if;
  insert into live_rooms (host_id, mode, title, category, language, audience, hashtags, max_seats, cover_url)
  values (v_uid, p_mode, btrim(p_title), p_category, coalesce(p_language, p.language), coalesce(p_audience, 'public'),
          coalesce((select array_agg(distinct lower(ltrim(t, '#'))) from unnest(p_hashtags) t
                    where t ~ '^#?[[:alnum:]_]{1,30}$'), '{}'),
          v_seats, p_cover_url)
  returning * into r;
  insert into room_participants (room_id, user_id, role, seat_index, camera_off)
  values (r.id, v_uid, 'host', 0, p_mode = 'audio');
  return r;
end $$;

-- The app calls this only after the streaming SDK reports that publishing succeeded.
create or replace function confirm_live(p_room uuid) returns live_rooms
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); r live_rooms;
begin
  select * into r from live_rooms where id = p_room for update;
  if not found or r.host_id <> v_uid then raise exception 'not_room_host'; end if;
  if r.status = 'live' then return r; end if;
  if r.status <> 'preparing' then raise exception 'room_not_preparing'; end if;
  update live_rooms set status = 'live', started_at = now(), last_heartbeat_at = now() where id = p_room returning * into r;
  insert into stream_sessions (room_id, host_id, started_at) values (r.id, v_uid, r.started_at);
  insert into room_messages (room_id, sender_id, kind, body) values (r.id, null, 'system', 'live_started');
  insert into notifications (user_id, type, actor_id, payload)
  select f.follower_id, 'live_start', v_uid, jsonb_build_object('room_id', r.id, 'title', r.title)
  from follows f join user_settings s on s.user_id = f.follower_id
  where f.followee_id = v_uid and s.notify_live_start;
  update profiles set is_creator = true where id = v_uid and not is_creator;
  return r;
end $$;

create or replace function fail_live(p_room uuid, p_reason text) returns void
language plpgsql security definer set search_path = public as $$
begin
  update live_rooms set status = 'failed', ended_at = now(), end_reason = left(coalesce(p_reason, 'publish_failed'), 100)
  where id = p_room and host_id = me() and status = 'preparing';
end $$;

create or replace function end_room_internal(p_room uuid, p_reason text) returns void
language plpgsql set search_path = public as $$
declare r live_rooms; v_match uuid;
begin
  select * into r from live_rooms where id = p_room for update;
  if not found or r.status not in ('preparing', 'live') then return; end if;
  for v_match in select id from pk_matches where status in ('invited', 'running') and p_room in (room_a, room_b) loop
    perform pk_close(v_match, true);
  end loop;
  update live_rooms set status = case when r.status = 'preparing' then 'failed'::room_status
                                      when p_reason like 'moderation%' then 'banned'::room_status
                                      else 'ended'::room_status end,
    ended_at = now(), end_reason = p_reason, viewer_count = 0
  where id = p_room;
  update stream_sessions set ended_at = now(), duration_seconds = extract(epoch from now() - started_at)::int,
    peak_viewers = r.peak_viewers, gift_coins = r.total_gift_coins
  where room_id = p_room;
  update room_participants set left_at = now(), seat_index = null where room_id = p_room and left_at is null;
  update seat_requests set status = 'expired', responded_at = now() where room_id = p_room and status = 'pending';
  if r.status = 'live' then
    insert into room_messages (room_id, sender_id, kind, body) values (p_room, null, 'system', 'live_ended');
    insert into user_badges (user_id, badge_id) values (r.host_id, 'first_live') on conflict do nothing;
    perform add_xp(r.host_id, least(extract(epoch from now() - r.started_at)::bigint / 60, 240));
  end if;
end $$;

create or replace function end_live(p_room uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from live_rooms where id = p_room and host_id = me()) then raise exception 'not_room_host'; end if;
  perform end_room_internal(p_room, 'host_ended');
end $$;

create or replace function add_xp(p_user uuid, p_xp bigint) returns void
language plpgsql set search_path = public as $$
begin
  if p_xp <= 0 then return; end if;
  update profiles set xp = xp + p_xp,
    level = (select max(level) from creator_levels where min_xp <= xp + p_xp)
  where id = p_user;
end $$;

create or replace function join_room(p_room uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); r live_rooms; v_part room_participants;
begin
  perform require_active(v_uid);
  select * into r from live_rooms where id = p_room;
  if not found then raise exception 'room_not_found'; end if;
  if r.host_id <> v_uid and r.status <> 'live' then raise exception 'room_not_live'; end if;
  if is_blocked_between(v_uid, r.host_id) then raise exception 'room_not_found'; end if;
  if r.audience = 'followers' and r.host_id <> v_uid
     and not exists (select 1 from follows where follower_id = v_uid and followee_id = r.host_id) then
    raise exception 'followers_only';
  end if;
  select * into v_part from room_participants where room_id = p_room and user_id = v_uid;
  if found and v_part.kicked_at is not null and v_part.kicked_at > now() - interval '24 hours' then
    raise exception 'removed_from_room';
  end if;
  insert into room_participants (room_id, user_id, role)
  values (p_room, v_uid, case when r.host_id = v_uid then 'host'::participant_role else 'audience'::participant_role end)
  on conflict (room_id, user_id) do update set
    left_at = null, last_seen_at = now(),
    -- A guest who reconnects within the presence window keeps their seat; otherwise they rejoin as audience.
    seat_index = case when room_participants.left_at is null then room_participants.seat_index
                      when room_participants.role = 'host' then 0 else null end,
    role = case when room_participants.role = 'host' then 'host'::participant_role
                when room_participants.left_at is null then room_participants.role
                else 'audience'::participant_role end
  returning * into v_part;
  if exists (select 1 from room_moderators where room_id = p_room and user_id = v_uid) and v_part.role = 'audience' then
    update room_participants set role = 'moderator' where room_id = p_room and user_id = v_uid returning * into v_part;
  end if;
  insert into recently_viewed (user_id, room_id) values (v_uid, p_room)
  on conflict (user_id, room_id) do update set viewed_at = now();
  perform recompute_viewers(p_room);
  return jsonb_build_object('role', v_part.role, 'seat_index', v_part.seat_index, 'room', room_card_json(r),
                            'channel_name', r.channel_name);
end $$;

create or replace function leave_room(p_room uuid) returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  if exists (select 1 from live_rooms where id = p_room and host_id = v_uid) then
    perform end_room_internal(p_room, 'host_left');
    return;
  end if;
  update room_participants set left_at = now(), seat_index = null,
    role = case when role = 'guest' then 'audience'::participant_role else role end
  where room_id = p_room and user_id = v_uid and left_at is null;
  update seat_requests set status = 'cancelled', responded_at = now()
  where room_id = p_room and user_id = v_uid and status = 'pending';
  perform recompute_viewers(p_room);
end $$;

-- Sent every ~15 s by everyone in the room. Hosts also keep the room alive with it.
create or replace function room_heartbeat(p_room uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); r live_rooms; v_count int;
begin
  update room_participants set last_seen_at = now() where room_id = p_room and user_id = v_uid and left_at is null;
  if not found then raise exception 'not_in_room'; end if;
  select * into r from live_rooms where id = p_room;
  if r.host_id = v_uid then
    update live_rooms set last_heartbeat_at = now() where id = p_room;
    v_count := recompute_viewers(p_room);
  else
    v_count := r.viewer_count;
  end if;
  return jsonb_build_object('status', r.status, 'viewer_count', v_count);
end $$;

create or replace function room_snapshot(p_room uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_uid uuid := auth.uid(); r live_rooms;
begin
  select * into r from live_rooms where id = p_room;
  if not found or (v_uid is not null and is_blocked_between(v_uid, r.host_id)) then raise exception 'room_not_found'; end if;
  return jsonb_build_object(
    'room', room_card_json(r),
    'host_followed', exists (select 1 from follows where follower_id = v_uid and followee_id = r.host_id),
    'participants', (select coalesce(jsonb_agg(jsonb_build_object(
        'user_id', rp.user_id, 'role', rp.role, 'seat_index', rp.seat_index, 'mic_muted', rp.mic_muted or rp.muted_by_host,
        'camera_off', rp.camera_off, 'muted_by_host', rp.muted_by_host, 'rtc_uid', p.rtc_uid,
        'username', p.username, 'display_name', p.display_name, 'avatar_url', p.avatar_url, 'level', p.level)
        order by rp.seat_index nulls last, rp.joined_at), '[]')
      from room_participants rp join profiles p on p.id = rp.user_id
      where rp.room_id = p_room and rp.left_at is null and (rp.seat_index is not null or rp.role in ('host', 'moderator'))),
    'messages', (select coalesce(jsonb_agg(m order by m.id), '[]') from (
        select rm.id, rm.kind, rm.body, rm.meta, rm.created_at, rm.sender_id, p.username, p.display_name, p.level
        from room_messages rm left join profiles p on p.id = rm.sender_id
        where rm.room_id = p_room and rm.removed_at is null order by rm.id desc limit 50) m),
    'pending_requests', case when v_uid = r.host_id or exists (select 1 from room_moderators where room_id = p_room and user_id = v_uid) then
      (select coalesce(jsonb_agg(jsonb_build_object('id', s.id, 'user_id', s.user_id, 'kind', s.kind, 'username', p.username,
                                                    'display_name', p.display_name, 'avatar_url', p.avatar_url, 'created_at', s.created_at)), '[]')
       from seat_requests s join profiles p on p.id = s.user_id where s.room_id = p_room and s.status = 'pending')
      else '[]'::jsonb end,
    'my_invite', (select jsonb_build_object('id', s.id) from seat_requests s
                  where s.room_id = p_room and s.user_id = v_uid and s.kind = 'invite' and s.status = 'pending'),
    'pk', (select to_jsonb(m) || jsonb_build_object('server_now', now()) from pk_matches m
           where m.status in ('invited', 'running') and p_room in (m.room_a, m.room_b) limit 1));
end $$;

create or replace function room_viewers(p_room uuid, p_limit int default 50)
returns table (user_id uuid, username text, display_name text, avatar_url text, level int, role participant_role)
language sql stable security definer set search_path = public as $$
  select p.id, p.username::text, p.display_name, p.avatar_url, p.level, rp.role
  from room_participants rp join profiles p on p.id = rp.user_id
  where rp.room_id = p_room and rp.left_at is null
    and rp.last_seen_at > now() - make_interval(secs => cfg_int('presence_timeout_seconds')::int)
  order by rp.role, p.level desc limit least(p_limit, 200)
$$;

create or replace function can_manage_room(p_room uuid, p_user uuid) returns boolean
language sql stable set search_path = public as $$
  select exists (select 1 from live_rooms where id = p_room and host_id = p_user)
      or exists (select 1 from room_moderators where room_id = p_room and user_id = p_user)
$$;

-- ---------------------------------------------------------------- chat
create or replace function send_room_message(p_room uuid, p_body text) returns bigint
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v_body text := btrim(p_body); v_id bigint; v_recent bigint;
begin
  perform require_active(v_uid);
  if v_body is null or char_length(v_body) = 0 then raise exception 'message_empty'; end if;
  if char_length(v_body) > 300 then raise exception 'message_too_long'; end if;
  if not exists (select 1 from live_rooms where id = p_room and status = 'live') then raise exception 'room_not_live'; end if;
  if not exists (select 1 from room_participants where room_id = p_room and user_id = v_uid and left_at is null) then
    raise exception 'not_in_room';
  end if;
  if exists (select 1 from room_mutes where room_id = p_room and user_id = v_uid and until > now()) then
    raise exception 'muted_in_room';
  end if;
  select count(*) into v_recent from room_messages
  where sender_id = v_uid and room_id = p_room and kind = 'chat' and created_at > now() - rate_window('room_message_rate');
  perform check_rate(v_recent, 'room_message_rate');
  insert into room_messages (room_id, sender_id, kind, body) values (p_room, v_uid, 'chat', v_body) returning id into v_id;
  return v_id;
end $$;

create or replace function remove_room_message(p_message bigint) returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v_room uuid; v_sender uuid;
begin
  select room_id, sender_id into v_room, v_sender from room_messages where id = p_message;
  if v_room is null then raise exception 'message_not_found'; end if;
  if v_sender <> v_uid and not can_manage_room(v_room, v_uid) then raise exception 'not_allowed'; end if;
  update room_messages set removed_at = now() where id = p_message;
end $$;

create or replace function mute_in_room(p_room uuid, p_user uuid, p_minutes int) returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  if not can_manage_room(p_room, v_uid) then raise exception 'not_room_manager'; end if;
  if exists (select 1 from live_rooms where id = p_room and host_id = p_user) then raise exception 'cannot_mute_host'; end if;
  insert into room_mutes (room_id, user_id, muted_by, until)
  values (p_room, p_user, v_uid, now() + make_interval(mins => least(greatest(p_minutes, 1), 1440)))
  on conflict (room_id, user_id) do update set until = excluded.until, muted_by = excluded.muted_by;
end $$;

create or replace function set_room_moderator(p_room uuid, p_user uuid, p_enabled boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from live_rooms where id = p_room and host_id = me()) then raise exception 'not_room_host'; end if;
  if p_enabled then
    insert into room_moderators values (p_room, p_user) on conflict do nothing;
    update room_participants set role = 'moderator' where room_id = p_room and user_id = p_user and role = 'audience';
  else
    delete from room_moderators where room_id = p_room and user_id = p_user;
    update room_participants set role = 'audience' where room_id = p_room and user_id = p_user and role = 'moderator';
  end if;
end $$;

-- ---------------------------------------------------------------- guest seats
create or replace function assign_free_seat(p_room uuid, p_user uuid) returns int
language plpgsql set search_path = public as $$
declare v_max int; v_seat int;
begin
  select max_seats into v_max from live_rooms where id = p_room and status = 'live' for update;
  if v_max is null then raise exception 'room_not_live'; end if;
  select s into v_seat from generate_series(1, v_max - 1) s
  where not exists (select 1 from room_participants where room_id = p_room and seat_index = s and left_at is null)
  order by s limit 1;
  if v_seat is null then raise exception 'no_free_seat'; end if;
  update room_participants set seat_index = v_seat, role = 'guest', mic_muted = false, muted_by_host = false,
    camera_off = (select mode = 'audio' from live_rooms where id = p_room)
  where room_id = p_room and user_id = p_user and left_at is null;
  if not found then raise exception 'not_in_room'; end if;
  return v_seat;
end $$;

create or replace function request_seat(p_room uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v_id uuid;
begin
  perform require_active(v_uid);
  if not exists (select 1 from room_participants where room_id = p_room and user_id = v_uid and left_at is null and seat_index is null) then
    raise exception 'not_in_room';
  end if;
  if (select max_seats from live_rooms where id = p_room and status = 'live') is distinct from null
     and (select max_seats from live_rooms where id = p_room) < 2 then
    raise exception 'room_has_no_guest_seats';
  end if;
  insert into seat_requests (room_id, user_id, kind) values (p_room, v_uid, 'request')
  on conflict (room_id, user_id) where status = 'pending' do nothing returning id into v_id;
  if v_id is null then raise exception 'request_already_pending'; end if;
  return v_id;
end $$;

create or replace function cancel_seat_request(p_request uuid) returns void
language sql security definer set search_path = public as $$
  update seat_requests set status = 'cancelled', responded_at = now()
  where id = p_request and user_id = me() and status = 'pending'
$$;

create or replace function respond_seat_request(p_request uuid, p_accept boolean) returns int
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); s seat_requests; v_seat int;
begin
  select * into s from seat_requests where id = p_request for update;
  if not found or s.kind <> 'request' then raise exception 'request_not_found'; end if;
  if not can_manage_room(s.room_id, v_uid) then raise exception 'not_room_manager'; end if;
  if s.status <> 'pending' then raise exception 'request_not_pending'; end if;
  if p_accept then
    v_seat := assign_free_seat(s.room_id, s.user_id);
    update seat_requests set status = 'accepted', responded_at = now() where id = p_request;
  else
    update seat_requests set status = 'rejected', responded_at = now() where id = p_request;
  end if;
  return v_seat;
end $$;

create or replace function invite_to_seat(p_room uuid, p_user uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v_id uuid;
begin
  if not can_manage_room(p_room, v_uid) then raise exception 'not_room_manager'; end if;
  if is_blocked_between((select host_id from live_rooms where id = p_room), p_user) then raise exception 'blocked'; end if;
  if not exists (select 1 from room_participants where room_id = p_room and user_id = p_user and left_at is null and seat_index is null) then
    raise exception 'user_not_in_room';
  end if;
  insert into seat_requests (room_id, user_id, kind) values (p_room, p_user, 'invite')
  on conflict (room_id, user_id) where status = 'pending' do nothing returning id into v_id;
  if v_id is null then raise exception 'request_already_pending'; end if;
  perform notify(p_user, 'guest_invite', v_uid, jsonb_build_object('room_id', p_room, 'request_id', v_id));
  return v_id;
end $$;

create or replace function respond_seat_invite(p_request uuid, p_accept boolean) returns int
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); s seat_requests; v_seat int;
begin
  select * into s from seat_requests where id = p_request for update;
  if not found or s.kind <> 'invite' or s.user_id <> v_uid then raise exception 'request_not_found'; end if;
  if s.status <> 'pending' then raise exception 'request_not_pending'; end if;
  if p_accept then
    v_seat := assign_free_seat(s.room_id, v_uid);
    update seat_requests set status = 'accepted', responded_at = now() where id = p_request;
  else
    update seat_requests set status = 'rejected', responded_at = now() where id = p_request;
  end if;
  return v_seat;
end $$;

create or replace function leave_seat(p_room uuid) returns void
language sql security definer set search_path = public as $$
  update room_participants set seat_index = null, role = 'audience'
  where room_id = p_room and user_id = me() and role = 'guest'
$$;

create or replace function remove_guest(p_room uuid, p_user uuid, p_kick boolean) returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  if not can_manage_room(p_room, v_uid) then raise exception 'not_room_manager'; end if;
  if exists (select 1 from live_rooms where id = p_room and host_id = p_user) then raise exception 'cannot_remove_host'; end if;
  update room_participants set seat_index = null,
    role = case when role = 'guest' then 'audience'::participant_role else role end,
    left_at = case when p_kick then now() else left_at end,
    kicked_at = case when p_kick then now() else kicked_at end
  where room_id = p_room and user_id = p_user;
  if p_kick then perform recompute_viewers(p_room); end if;
end $$;

create or replace function host_set_guest_mute(p_room uuid, p_user uuid, p_muted boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not can_manage_room(p_room, me()) then raise exception 'not_room_manager'; end if;
  update room_participants set muted_by_host = p_muted, mic_muted = case when p_muted then true else mic_muted end
  where room_id = p_room and user_id = p_user and seat_index is not null;
end $$;

create or replace function set_media_state(p_room uuid, p_mic_muted boolean, p_camera_off boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  update room_participants set mic_muted = p_mic_muted or muted_by_host, camera_off = p_camera_off
  where room_id = p_room and user_id = me() and left_at is null;
end $$;

-- Used by the stream-token edge function: decides the caller's media role for the room.
create or replace function authorize_stream(p_room uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); r live_rooms; rp room_participants; p profiles;
begin
  p := require_active(v_uid);
  select * into r from live_rooms where id = p_room;
  if not found or r.status not in ('preparing', 'live') then raise exception 'room_not_live'; end if;
  select * into rp from room_participants where room_id = p_room and user_id = v_uid and left_at is null;
  if not found then raise exception 'not_in_room'; end if;
  return jsonb_build_object('channel_name', r.channel_name, 'rtc_uid', p.rtc_uid,
    'publisher', rp.role = 'host' or (rp.seat_index is not null and rp.role = 'guest'), 'mode', r.mode);
end $$;

-- Lets a host in a running PK battle relay their stream into the opponent's channel.
create or replace function authorize_pk_relay(p_match uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); m pk_matches; v_dest text; v_src text; v_uid_rtc int;
begin
  select * into m from pk_matches where id = p_match and status = 'running';
  if not found or v_uid not in (m.host_a, m.host_b) then raise exception 'pk_not_running'; end if;
  select channel_name into v_src from live_rooms where id = case when v_uid = m.host_a then m.room_a else m.room_b end;
  select channel_name into v_dest from live_rooms where id = case when v_uid = m.host_a then m.room_b else m.room_a end;
  select rtc_uid into v_uid_rtc from profiles where id = v_uid;
  return jsonb_build_object('source_channel', v_src, 'dest_channel', v_dest, 'rtc_uid', v_uid_rtc);
end $$;

-- ================================================================ gifts
create or replace function list_gifts() returns setof gifts
language sql stable security definer set search_path = public as $$
  select g.* from gifts g left join events e on e.id = g.event_id
  where g.available and (g.event_id is null or (e.published and now() between e.starts_at and e.ends_at))
  order by g.sort_order, g.price_coins
$$;

create or replace function send_gift(p_room uuid, p_recipient uuid, p_gift text, p_quantity int, p_idempotency_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := me();
  g gifts;
  r live_rooms;
  v_tx uuid;
  v_total bigint;
  v_promo_acct uuid; v_coin_acct uuid;
  v_promo_bal bigint; v_coin_bal bigint;
  v_promo_spend bigint; v_coin_spend bigint;
  v_beans bigint;
  v_match pk_matches;
  v_side pk_side;
  v_points bigint := 0;
  v_existing jsonb;
begin
  perform require_active(v_uid);
  if p_idempotency_key is null or char_length(p_idempotency_key) not between 8 and 100 then raise exception 'idempotency_key_required'; end if;
  if p_quantity is null or p_quantity not between 1 and 999 then raise exception 'invalid_quantity'; end if;
  if p_recipient = v_uid then raise exception 'cannot_gift_self'; end if;

  -- Replays of an already-processed request return the original result and never charge twice.
  select jsonb_build_object('transaction_id', t.id, 'duplicate', true, 'coins_spent', gt.coins_spent,
                            'promo_coins_spent', gt.promo_coins_spent, 'beans_earned', gt.beans_earned)
  into v_existing
  from ledger_transactions t join gift_transactions gt on gt.id = t.id
  where t.kind = 'gift' and t.actor_id = v_uid and t.idempotency_key = p_idempotency_key;
  if v_existing is not null then return v_existing || my_balances(v_uid); end if;
  if exists (select 1 from free_gift_sends where sender_id = v_uid and idempotency_key = p_idempotency_key) then
    return jsonb_build_object('duplicate', true, 'coins_spent', 0, 'promo_coins_spent', 0, 'beans_earned', 0) || my_balances(v_uid);
  end if;

  select * into g from gifts where id = p_gift and available;
  if not found then raise exception 'gift_unavailable'; end if;
  if g.event_id is not null and not exists (select 1 from events e where e.id = g.event_id and e.published
                                            and now() between e.starts_at and e.ends_at) then
    raise exception 'gift_unavailable';
  end if;
  if not exists (select 1 from profiles where id = p_recipient and account_status in ('active', 'warned')) then
    raise exception 'recipient_unavailable';
  end if;
  if is_blocked_between(v_uid, p_recipient) then raise exception 'blocked'; end if;

  if p_room is not null then
    select * into r from live_rooms where id = p_room;
    if not found or r.status <> 'live' then raise exception 'room_not_live'; end if;
    if not exists (select 1 from room_participants where room_id = p_room and user_id = v_uid and left_at is null) then
      raise exception 'not_in_room';
    end if;
    if not exists (select 1 from room_participants where room_id = p_room and user_id = p_recipient and left_at is null
                   and (role = 'host' or seat_index is not null)) then
      raise exception 'recipient_not_on_stage';
    end if;
  end if;

  -- Free gifts: no ledger, daily cap, no PK points.
  if g.price_coins = 0 then
    if (select count(*) from free_gift_sends where sender_id = v_uid and created_at > now() - interval '1 day')
       + p_quantity > cfg_int('free_gifts_per_day') then
      raise exception 'free_gift_limit';
    end if;
    insert into free_gift_sends (sender_id, recipient_id, room_id, gift_id, idempotency_key)
    select v_uid, p_recipient, p_room, g.id, case when i = 1 then p_idempotency_key else p_idempotency_key || ':' || i end
    from generate_series(1, p_quantity) i;
    if p_room is not null then
      insert into room_messages (room_id, sender_id, kind, body, meta)
      values (p_room, v_uid, 'gift', g.name, jsonb_build_object('gift_id', g.id, 'quantity', p_quantity, 'recipient_id', p_recipient,
                                                                 'animation_key', g.animation_key, 'free', true));
    end if;
    return jsonb_build_object('duplicate', false, 'coins_spent', 0, 'promo_coins_spent', 0, 'beans_earned', 0) || my_balances(v_uid);
  end if;

  if not is_adult(v_uid) then raise exception 'paid_gifts_age_restricted'; end if;
  v_total := g.price_coins::bigint * p_quantity;

  -- Lock the sender's wallets in a fixed order (promo, then coins) to serialize concurrent spends.
  v_promo_acct := wallet_account(v_uid, 'user_promo');
  v_coin_acct := wallet_account(v_uid, 'user_coins');
  select balance into v_promo_bal from wallet_accounts where id = v_promo_acct for update;
  select balance into v_coin_bal from wallet_accounts where id = v_coin_acct for update;
  if v_promo_bal + v_coin_bal < v_total then raise exception 'insufficient_balance'; end if;
  -- Promotional credits are spent first; they never generate withdrawable creator earnings unless configured.
  v_promo_spend := least(v_promo_bal, v_total);
  v_coin_spend := v_total - v_promo_spend;
  v_beans := (v_coin_spend * cfg_int('creator_bean_share_bps')) / 10000
           + (v_promo_spend * cfg_int('promo_bean_share_bps')) / 10000;

  insert into ledger_transactions (kind, actor_id, idempotency_key, reference)
  values ('gift', v_uid, p_idempotency_key, jsonb_build_object('gift_id', g.id, 'quantity', p_quantity,
                                                               'recipient_id', p_recipient, 'room_id', p_room))
  on conflict (kind, actor_id, idempotency_key) do nothing
  returning id into v_tx;
  if v_tx is null then raise exception 'duplicate_in_progress'; end if;

  perform post_entry(v_tx, v_promo_acct, -v_promo_spend, 'coin');
  perform post_entry(v_tx, v_coin_acct, -v_coin_spend, 'coin');
  perform post_entry(v_tx, wallet_account(null, 'sys_coin_revenue'), v_total, 'coin');
  if v_beans > 0 then
    perform post_entry(v_tx, wallet_account(null, 'sys_bean_issuance'), -v_beans, 'bean');
    perform post_entry(v_tx, wallet_account(p_recipient, 'creator_beans_pending'), v_beans, 'bean');
    insert into bean_settlements (transaction_id, creator_id, beans, release_at)
    values (v_tx, p_recipient, v_beans, now() + make_interval(days => cfg_int('settlement_hold_days')::int));
  end if;

  -- PK points are derived here from the verified spend; the client cannot pick a side or a score.
  if p_room is not null then
    select * into v_match from pk_matches
    where status = 'running' and p_room in (room_a, room_b) and now() < ends_at for update;
    if found then
      v_side := case when p_recipient = v_match.host_a then 'a'::pk_side when p_recipient = v_match.host_b then 'b'::pk_side end;
      if v_side is not null then v_points := v_total * cfg_int('pk_points_per_coin'); end if;
    end if;
  end if;

  insert into gift_transactions (id, sender_id, recipient_id, room_id, gift_id, quantity, coins_spent, promo_coins_spent,
                                 beans_earned, pk_match_id, pk_side)
  values (v_tx, v_uid, p_recipient, p_room, g.id, p_quantity, v_coin_spend, v_promo_spend, v_beans,
          case when v_points > 0 then v_match.id end, case when v_points > 0 then v_side end);

  if v_points > 0 then
    insert into pk_score_events (match_id, side, gift_transaction_id, points) values (v_match.id, v_side, v_tx, v_points);
    update pk_matches set score_a = score_a + case when v_side = 'a' then v_points else 0 end,
                          score_b = score_b + case when v_side = 'b' then v_points else 0 end
    where id = v_match.id;
  end if;

  if p_room is not null then
    update live_rooms set total_gift_coins = total_gift_coins + v_total where id = p_room;
    insert into room_messages (room_id, sender_id, kind, body, meta)
    values (p_room, v_uid, 'gift', g.name, jsonb_build_object('gift_id', g.id, 'quantity', p_quantity, 'recipient_id', p_recipient,
                                                               'animation_key', g.animation_key, 'coins', v_total,
                                                               'fullscreen', g.is_animated_fullscreen, 'pk_points', v_points));
  end if;
  update profiles set total_gift_coins_received = total_gift_coins_received + v_total where id = p_recipient;
  perform add_xp(p_recipient, v_total);
  perform add_xp(v_uid, v_total / 2);
  update fan_club_members m set contribution_coins = contribution_coins + v_total,
    loyalty_level = 1 + least(9, ((contribution_coins + v_total) / 5000)::int)
  from fan_clubs c where c.id = m.club_id and c.creator_id = p_recipient and m.user_id = v_uid;
  if v_total >= 100 then
    perform notify(p_recipient, 'gift', v_uid, jsonb_build_object('gift_id', g.id, 'quantity', p_quantity, 'coins', v_total, 'room_id', p_room));
  end if;
  if (select coalesce(sum(coins_spent + promo_coins_spent), 0) from gift_transactions where sender_id = v_uid) >= 10000 then
    insert into user_badges (user_id, badge_id) values (v_uid, 'generous') on conflict do nothing;
  end if;

  return jsonb_build_object('transaction_id', v_tx, 'duplicate', false, 'coins_spent', v_coin_spend,
                            'promo_coins_spent', v_promo_spend, 'beans_earned', v_beans, 'pk_points', v_points)
         || my_balances(v_uid);
end $$;

create or replace function my_balances(p_user uuid) returns jsonb
language sql stable set search_path = public as $$
  select jsonb_build_object(
    'coins', coalesce(sum(balance) filter (where kind = 'user_coins'), 0),
    'promo_coins', coalesce(sum(balance) filter (where kind = 'user_promo'), 0),
    'beans_pending', coalesce(sum(balance) filter (where kind = 'creator_beans_pending'), 0),
    'beans', coalesce(sum(balance) filter (where kind = 'creator_beans'), 0),
    'beans_in_payout', coalesce(sum(balance) filter (where kind = 'payout_hold'), 0))
  from wallet_accounts where owner_id = p_user
$$;

-- ================================================================ wallet
create or replace function my_wallet() returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  perform ensure_user_wallets(v_uid);
  return my_balances(v_uid) || jsonb_build_object(
    'is_adult', is_adult(v_uid),
    'kyc_status', (select kyc_status from user_private where user_id = v_uid),
    'next_release', (select jsonb_build_object('beans', sum(beans), 'at', min(release_at)) from bean_settlements
                     where creator_id = v_uid and settled_at is null));
end $$;

create or replace function my_ledger(p_limit int default 30, p_before bigint default null)
returns table (entry_id bigint, transaction_id uuid, kind ledger_kind, account wallet_kind, amount bigint, unit text,
               balance_after bigint, reference jsonb, created_at timestamptz)
language sql stable security definer set search_path = public as $$
  select e.id, t.id, t.kind, a.kind, e.amount, e.unit, e.balance_after, t.reference, e.created_at
  from ledger_entries e join wallet_accounts a on a.id = e.account_id join ledger_transactions t on t.id = e.transaction_id
  where a.owner_id = me() and (p_before is null or e.id < p_before)
  order by e.id desc limit least(p_limit, 100)
$$;

create or replace function list_coin_packages() returns setof coin_packages
language sql stable security definer set search_path = public as $$
  select * from coin_packages where active order by sort_order, coins
$$;

-- Creates a pending order; the payment edge functions then talk to the provider.
create or replace function create_payment_order(p_package text, p_provider payment_provider) returns payment_orders
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); pkg coin_packages; o payment_orders;
begin
  perform require_active(v_uid);
  if not is_adult(v_uid) then raise exception 'purchase_age_restricted'; end if;
  select * into pkg from coin_packages where id = p_package and active;
  if not found then raise exception 'package_unavailable'; end if;
  if p_provider = 'google_play' and pkg.play_product_id is null then raise exception 'package_unavailable'; end if;
  if (select count(*) from payment_orders where user_id = v_uid and created_at > now() - interval '1 hour') >= 20 then
    raise exception 'rate_limited';
  end if;
  insert into payment_orders (user_id, provider, package_id, coins, bonus_promo_coins, amount_minor, currency, status)
  values (v_uid, p_provider, pkg.id, pkg.coins, pkg.bonus_promo_coins, pkg.price_minor, pkg.currency, 'created')
  returning * into o;
  return o;
end $$;

-- SERVICE ROLE ONLY (granted below). Called by payment webhooks after the provider confirmed payment.
create or replace function credit_payment_order(p_order uuid, p_provider_ref text, p_amount_minor bigint, p_currency text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare o payment_orders; v_tx uuid;
begin
  select * into o from payment_orders where id = p_order for update;
  if not found then raise exception 'order_not_found'; end if;
  if o.status = 'paid' then return jsonb_build_object('duplicate', true, 'order_id', o.id); end if;
  if o.status not in ('created', 'pending') then raise exception 'order_not_payable'; end if;
  if p_amount_minor <> o.amount_minor or upper(p_currency) <> upper(o.currency) then
    update payment_orders set status = 'failed', failure_reason = 'amount_mismatch', provider_ref = p_provider_ref where id = o.id;
    perform audit(null, 'payment.amount_mismatch', 'payment_order', o.id::text,
                  jsonb_build_object('expected', o.amount_minor, 'got', p_amount_minor, 'currency', p_currency));
    return jsonb_build_object('credited', false, 'reason', 'amount_mismatch');
  end if;
  insert into ledger_transactions (kind, actor_id, idempotency_key, reference)
  values ('purchase', o.user_id, 'order:' || o.id, jsonb_build_object('order_id', o.id, 'provider', o.provider,
                                                                     'provider_ref', p_provider_ref, 'package_id', o.package_id))
  returning id into v_tx;
  perform post_entry(v_tx, wallet_account(null, 'sys_coin_issuance'), -o.coins, 'coin');
  perform post_entry(v_tx, wallet_account(o.user_id, 'user_coins'), o.coins, 'coin');
  if o.bonus_promo_coins > 0 then
    perform post_entry(v_tx, wallet_account(null, 'sys_promo_issuance'), -o.bonus_promo_coins, 'coin');
    perform post_entry(v_tx, wallet_account(o.user_id, 'user_promo'), o.bonus_promo_coins, 'coin');
  end if;
  update payment_orders set status = 'paid', provider_ref = p_provider_ref, verified_at = now(), ledger_transaction_id = v_tx
  where id = o.id;
  perform audit(null, 'payment.credited', 'payment_order', o.id::text, jsonb_build_object('coins', o.coins, 'provider', o.provider));
  return jsonb_build_object('credited', true, 'order_id', o.id, 'coins', o.coins);
end $$;

-- SERVICE ROLE ONLY. Marks a failed/cancelled payment so the app can show it.
create or replace function fail_payment_order(p_order uuid, p_reason text) returns void
language sql security definer set search_path = public as $$
  update payment_orders set status = 'failed', failure_reason = left(p_reason, 200)
  where id = p_order and status in ('created', 'pending')
$$;

-- SERVICE ROLE ONLY. Reverses a refunded purchase. Coins already spent cannot be clawed back below zero;
-- the shortfall is recorded for the finance team.
create or replace function refund_payment_order(p_order uuid, p_reason text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare o payment_orders; v_tx uuid; v_bal bigint; v_take bigint; v_promo_bal bigint; v_promo_take bigint;
begin
  select * into o from payment_orders where id = p_order for update;
  if not found or o.status <> 'paid' then raise exception 'order_not_refundable'; end if;
  select balance into v_bal from wallet_accounts where id = wallet_account(o.user_id, 'user_coins') for update;
  select balance into v_promo_bal from wallet_accounts where id = wallet_account(o.user_id, 'user_promo') for update;
  v_take := least(v_bal, o.coins);
  v_promo_take := least(v_promo_bal, o.bonus_promo_coins);
  insert into ledger_transactions (kind, actor_id, idempotency_key, reference)
  values ('refund', o.user_id, 'refund:' || o.id, jsonb_build_object('order_id', o.id, 'reason', p_reason,
                                                                     'shortfall', o.coins - v_take))
  returning id into v_tx;
  perform post_entry(v_tx, wallet_account(o.user_id, 'user_coins'), -v_take, 'coin');
  perform post_entry(v_tx, wallet_account(null, 'sys_coin_issuance'), v_take, 'coin');
  perform post_entry(v_tx, wallet_account(o.user_id, 'user_promo'), -v_promo_take, 'coin');
  perform post_entry(v_tx, wallet_account(null, 'sys_promo_issuance'), v_promo_take, 'coin');
  update payment_orders set status = 'refunded' where id = o.id;
  perform audit(null, 'payment.refunded', 'payment_order', o.id::text, jsonb_build_object('shortfall', o.coins - v_take));
  return jsonb_build_object('refunded_coins', v_take, 'shortfall', o.coins - v_take);
end $$;

-- ================================================================ creator earnings & payouts
create or replace function settle_due_beans(p_limit int default 1000) returns int
language plpgsql security definer set search_path = public as $$
declare s bean_settlements; v_tx uuid; n int := 0;
begin
  for s in select * from bean_settlements where settled_at is null and release_at <= now()
           order by release_at limit p_limit for update skip locked loop
    insert into ledger_transactions (kind, actor_id, idempotency_key, reference)
    values ('settlement', s.creator_id, 'settle:' || s.transaction_id, jsonb_build_object('gift_transaction_id', s.transaction_id))
    returning id into v_tx;
    perform post_entry(v_tx, wallet_account(s.creator_id, 'creator_beans_pending'), -s.beans, 'bean');
    perform post_entry(v_tx, wallet_account(s.creator_id, 'creator_beans'), s.beans, 'bean');
    update bean_settlements set settled_at = now() where transaction_id = s.transaction_id;
    n := n + 1;
  end loop;
  return n;
end $$;

create or replace function payout_terms() returns jsonb
language sql stable set search_path = public as $$
  select jsonb_build_object('min_beans', cfg_int('payout_min_beans'), 'etb_minor_per_100_beans', cfg_int('bean_to_etb_minor'),
                            'fee_bps', cfg_int('payout_fee_bps'), 'hold_days', cfg_int('settlement_hold_days'),
                            'creator_share_bps', cfg_int('creator_bean_share_bps'), 'promo_share_bps', cfg_int('promo_bean_share_bps'),
                            'processing_days', '3-10 business days after approval')
$$;

create or replace function request_payout(p_beans bigint, p_method jsonb) returns payout_requests
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v_tx uuid; v_gross bigint; v_fee bigint; pr payout_requests;
begin
  perform require_active(v_uid);
  if not is_adult(v_uid) then raise exception 'payout_age_restricted'; end if;
  if (select kyc_status from user_private where user_id = v_uid) <> 'verified' then raise exception 'kyc_required'; end if;
  if p_beans < cfg_int('payout_min_beans') then raise exception 'payout_below_minimum'; end if;
  if p_method is null or p_method ->> 'type' not in ('bank_transfer', 'telebirr', 'cbe_birr', 'mpesa') then
    raise exception 'payout_method_invalid';
  end if;
  if exists (select 1 from payout_requests where creator_id = v_uid and status in ('requested', 'under_review', 'approved')) then
    raise exception 'payout_already_open';
  end if;
  v_gross := p_beans * cfg_int('bean_to_etb_minor') / 100;
  v_fee := v_gross * cfg_int('payout_fee_bps') / 10000;
  insert into ledger_transactions (kind, actor_id, idempotency_key, reference)
  values ('payout_request', v_uid, gen_random_uuid()::text, jsonb_build_object('beans', p_beans))
  returning id into v_tx;
  perform post_entry(v_tx, wallet_account(v_uid, 'creator_beans'), -p_beans, 'bean');
  perform post_entry(v_tx, wallet_account(v_uid, 'payout_hold'), p_beans, 'bean');
  insert into payout_requests (creator_id, beans, gross_etb_minor, fee_etb_minor, net_etb_minor, method, hold_transaction_id)
  values (v_uid, p_beans, v_gross, v_fee, v_gross - v_fee, p_method, v_tx) returning * into pr;
  perform audit(v_uid, 'payout.requested', 'payout', pr.id::text, jsonb_build_object('beans', p_beans));
  return pr;
end $$;

create or replace function my_payouts() returns setof payout_requests
language sql stable security definer set search_path = public as $$
  select * from payout_requests where creator_id = me() order by created_at desc limit 50
$$;

create or replace function creator_dashboard() returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  perform ensure_user_wallets(v_uid);
  return jsonb_build_object(
    'balances', my_balances(v_uid),
    'terms', payout_terms(),
    'kyc_status', (select kyc_status from user_private where user_id = v_uid),
    'is_adult', is_adult(v_uid),
    'total_gift_coins', (select total_gift_coins_received from profiles where id = v_uid),
    'beans_paid_out', (select coalesce(sum(beans), 0) from payout_requests where creator_id = v_uid and status = 'paid'),
    'sessions', (select jsonb_build_object('count', count(*), 'total_seconds', coalesce(sum(duration_seconds), 0),
                                           'peak_viewers', coalesce(max(peak_viewers), 0), 'avg_peak', coalesce(avg(peak_viewers)::int, 0))
                 from stream_sessions where host_id = v_uid and ended_at is not null),
    'recent_sessions', (select coalesce(jsonb_agg(x), '[]') from (
        select r.title, s.started_at, s.duration_seconds, s.peak_viewers, s.gift_coins, s.new_followers
        from stream_sessions s join live_rooms r on r.id = s.room_id where s.host_id = v_uid
        order by s.started_at desc limit 10) x),
    'earnings_by_day', (select coalesce(jsonb_agg(x order by x.day), '[]') from (
        select (created_at at time zone 'Africa/Addis_Ababa')::date as day, sum(beans_earned) as beans, sum(coins_spent + promo_coins_spent) as coins
        from gift_transactions where recipient_id = v_uid and created_at > now() - interval '30 days' group by 1) x),
    'follower_growth', (select coalesce(jsonb_agg(x order by x.day), '[]') from (
        select (created_at at time zone 'Africa/Addis_Ababa')::date as day, count(*) as new_followers
        from follows where followee_id = v_uid and created_at > now() - interval '30 days' group by 1) x),
    'follower_count', (select follower_count from profiles where id = v_uid),
    'gift_breakdown', (select coalesce(jsonb_agg(x), '[]') from (
        select gift_id, sum(quantity) as count, sum(coins_spent + promo_coins_spent) as coins
        from gift_transactions where recipient_id = v_uid group by gift_id order by 3 desc limit 10) x),
    'top_supporters', (select coalesce(jsonb_agg(x), '[]') from (
        select p.id, p.username, p.display_name, p.avatar_url, sum(g.coins_spent + g.promo_coins_spent) as coins
        from gift_transactions g join profiles p on p.id = g.sender_id
        where g.recipient_id = v_uid and p.account_status <> 'deleted' group by p.id order by coins desc limit 10) x),
    'open_payout', (select to_jsonb(pr) from payout_requests pr where creator_id = v_uid
                    and status in ('requested', 'under_review', 'approved') limit 1));
end $$;

-- ================================================================ PK battles
create or replace function pk_invite(p_my_room uuid, p_target_room uuid, p_duration_seconds int) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); a live_rooms; b live_rooms; v_id uuid;
begin
  perform require_active(v_uid);
  select * into a from live_rooms where id = p_my_room;
  select * into b from live_rooms where id = p_target_room;
  if a.host_id is distinct from v_uid then raise exception 'not_room_host'; end if;
  if a.status <> 'live' or b.status is distinct from 'live' then raise exception 'room_not_live'; end if;
  if a.mode = 'audio' or b.mode = 'audio' then raise exception 'pk_requires_video'; end if;
  if b.host_id = v_uid then raise exception 'cannot_pk_self'; end if;
  if is_blocked_between(v_uid, b.host_id) then raise exception 'blocked'; end if;
  if not is_adult(b.host_id) then raise exception 'pk_target_ineligible'; end if;
  -- Clear invitations that were never answered.
  update pk_matches set status = 'expired' where status = 'invited' and invited_at < now() - interval '60 seconds'
    and (room_a in (a.id, b.id) or room_b in (a.id, b.id));
  insert into pk_matches (room_a, room_b, host_a, host_b, duration_seconds)
  values (a.id, b.id, v_uid, b.host_id, coalesce(p_duration_seconds, cfg_int('pk_default_seconds')::int))
  returning id into v_id;
  perform notify(b.host_id, 'pk_invite', v_uid, jsonb_build_object('match_id', v_id, 'room_id', a.id));
  return v_id;
exception when unique_violation then
  raise exception 'pk_already_active';
end $$;

create or replace function pk_respond(p_match uuid, p_accept boolean) returns pk_matches
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); m pk_matches;
begin
  select * into m from pk_matches where id = p_match for update;
  if not found or m.host_b <> v_uid then raise exception 'pk_not_found'; end if;
  if m.status <> 'invited' then raise exception 'pk_not_pending'; end if;
  if m.invited_at < now() - interval '60 seconds' then
    update pk_matches set status = 'expired' where id = p_match returning * into m;
    raise exception 'pk_invite_expired';
  end if;
  if p_accept then
    if exists (select 1 from live_rooms where id in (m.room_a, m.room_b) and status <> 'live') then raise exception 'room_not_live'; end if;
    update pk_matches set status = 'running', started_at = now(), ends_at = now() + make_interval(secs => duration_seconds)
    where id = p_match returning * into m;
    insert into room_messages (room_id, kind, body, meta)
    select x, 'pk', 'pk_started', jsonb_build_object('match_id', m.id) from unnest(array[m.room_a, m.room_b]) x;
  else
    update pk_matches set status = 'declined' where id = p_match returning * into m;
  end if;
  return m;
end $$;

-- Authoritative result: scores are re-derived from the score events, never from client input.
create or replace function pk_close(p_match uuid, p_force boolean) returns pk_matches
language plpgsql set search_path = public as $$
declare m pk_matches; v_a bigint; v_b bigint; v_winner text;
begin
  select * into m from pk_matches where id = p_match for update;
  if m.status = 'invited' then
    update pk_matches set status = 'cancelled' where id = p_match returning * into m;
    return m;
  end if;
  if m.status <> 'running' then return m; end if;
  if not p_force and now() < m.ends_at then return m; end if;
  select coalesce(sum(points) filter (where side = 'a'), 0), coalesce(sum(points) filter (where side = 'b'), 0)
  into v_a, v_b from pk_score_events where match_id = p_match;
  v_winner := case when v_a > v_b then 'a' when v_b > v_a then 'b' else 'draw' end;
  update pk_matches set status = 'finished', finished_at = now(), score_a = v_a, score_b = v_b, winner = v_winner
  where id = p_match returning * into m;
  insert into room_messages (room_id, kind, body, meta)
  select x, 'pk', 'pk_finished', jsonb_build_object('match_id', m.id, 'winner', v_winner, 'score_a', v_a, 'score_b', v_b)
  from unnest(array[m.room_a, m.room_b]) x;
  if v_winner in ('a', 'b') then
    insert into user_badges (user_id, badge_id) values (case when v_winner = 'a' then m.host_a else m.host_b end, 'pk_winner')
    on conflict do nothing;
    perform add_xp(case when v_winner = 'a' then m.host_a else m.host_b end, 200);
  end if;
  return m;
end $$;

-- Anyone in either room may ask the server to close a match once its time is up.
create or replace function pk_finish(p_match uuid) returns pk_matches
language plpgsql security definer set search_path = public as $$
begin
  perform me();
  return pk_close(p_match, false);
end $$;

-- A host may stop the match early; it is then scored as it stands.
create or replace function pk_cancel(p_match uuid) returns pk_matches
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  if not exists (select 1 from pk_matches where id = p_match and v_uid in (host_a, host_b)) then raise exception 'pk_not_found'; end if;
  return pk_close(p_match, true);
end $$;

create or replace function pk_candidates(p_my_room uuid, p_limit int default 20) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(card order by ord), '[]') from (
  select room_card_json(r) as card,
         row_number() over (order by (exists (select 1 from follows f where f.follower_id = me() and f.followee_id = r.host_id)) desc, r.viewer_count desc) as ord
  from live_rooms r
  where r.status = 'live' and r.mode in ('video', 'multi_guest') and r.id <> p_my_room and r.host_id <> me()
    and not is_blocked_between(me(), r.host_id) and is_adult(r.host_id)
    and not exists (select 1 from pk_matches m where m.status in ('invited', 'running') and r.id in (m.room_a, m.room_b))
  order by ord
  limit least(p_limit, 50)) x
$$;

-- ================================================================ messaging
create or replace function open_conversation(p_other uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := me();
  v_low uuid := least(v_uid, p_other);
  v_high uuid := greatest(v_uid, p_other);
  v_id uuid;
  v_policy dm_policy;
  v_other_follows_me boolean;
begin
  perform require_active(v_uid);
  if p_other = v_uid then raise exception 'cannot_message_self'; end if;
  if not exists (select 1 from profiles where id = p_other and account_status in ('active', 'warned')) then raise exception 'user_not_found'; end if;
  if is_blocked_between(v_uid, p_other) then raise exception 'dm_not_allowed'; end if;
  select id into v_id from conversations where user_low = v_low and user_high = v_high;
  if v_id is not null then return v_id; end if;
  select dm_policy into v_policy from user_settings where user_id = p_other;
  v_other_follows_me := exists (select 1 from follows where follower_id = p_other and followee_id = v_uid);
  if v_policy = 'nobody' then raise exception 'dm_not_allowed'; end if;
  -- Safeguard: nobody can start a conversation with a younger user unless that user follows them.
  if not is_adult(p_other) and not v_other_follows_me then raise exception 'dm_not_allowed'; end if;
  insert into conversations (user_low, user_high, started_by, status)
  values (v_low, v_high, v_uid,
          case when v_other_follows_me then 'active'::conversation_status
               when v_policy = 'followers' then 'request'::conversation_status
               else 'active'::conversation_status end)
  on conflict (user_low, user_high) do nothing returning id into v_id;
  if v_id is null then select id into v_id from conversations where user_low = v_low and user_high = v_high; end if;
  return v_id;
end $$;

create or replace function send_dm(p_conversation uuid, p_body text, p_client_id uuid) returns dm_messages
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := me();
  c conversations;
  v_other uuid;
  v_body text := btrim(p_body);
  v_msg dm_messages;
  v_recent bigint;
begin
  perform require_active(v_uid);
  select * into v_msg from dm_messages where sender_id = v_uid and client_id = p_client_id;
  if found then return v_msg; end if;
  select * into c from conversations where id = p_conversation for update;
  if not found or v_uid not in (c.user_low, c.user_high) then raise exception 'conversation_not_found'; end if;
  v_other := case when v_uid = c.user_low then c.user_high else c.user_low end;
  if is_blocked_between(v_uid, v_other) then raise exception 'dm_not_allowed'; end if;
  if c.status = 'declined' then raise exception 'dm_not_allowed'; end if;
  if c.status = 'request' then
    if v_uid = c.started_by then
      if exists (select 1 from dm_messages where conversation_id = c.id and sender_id = v_uid) then
        raise exception 'awaiting_acceptance';
      end if;
    else
      update conversations set status = 'active' where id = c.id;
    end if;
  end if;
  if v_body is null or char_length(v_body) = 0 then raise exception 'message_empty'; end if;
  if char_length(v_body) > 1000 then raise exception 'message_too_long'; end if;
  select count(*) into v_recent from dm_messages where sender_id = v_uid and created_at > now() - rate_window('dm_rate');
  perform check_rate(v_recent, 'dm_rate');
  insert into dm_messages (conversation_id, sender_id, body, client_id) values (c.id, v_uid, v_body, p_client_id) returning * into v_msg;
  update conversations set last_message_at = v_msg.created_at, last_message_preview = left(v_body, 80),
    low_last_read_id = case when v_uid = user_low then v_msg.id else low_last_read_id end,
    high_last_read_id = case when v_uid = user_high then v_msg.id else high_last_read_id end
  where id = c.id;
  perform notify(v_other, 'message', v_uid, jsonb_build_object('conversation_id', c.id, 'preview', left(v_body, 80)));
  return v_msg;
end $$;

create or replace function respond_message_request(p_conversation uuid, p_accept boolean) returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  update conversations set status = case when p_accept then 'active'::conversation_status else 'declined'::conversation_status end
  where id = p_conversation and status = 'request' and started_by <> v_uid and v_uid in (user_low, user_high);
  if not found then raise exception 'conversation_not_found'; end if;
end $$;

create or replace function mark_conversation_read(p_conversation uuid, p_last_id bigint) returns void
language sql security definer set search_path = public as $$
  update conversations set
    low_last_read_id = case when me() = user_low then greatest(low_last_read_id, p_last_id) else low_last_read_id end,
    high_last_read_id = case when me() = user_high then greatest(high_last_read_id, p_last_id) else high_last_read_id end
  where id = p_conversation and me() in (user_low, user_high)
$$;

create or replace function list_conversations(p_requests boolean default false, p_limit int default 30, p_offset int default 0)
returns table (id uuid, status conversation_status, is_request_to_me boolean, other_id uuid, other_username text,
               other_display_name text, other_avatar_url text, last_message_at timestamptz, last_message_preview text,
               unread_count bigint, other_last_read_id bigint)
language sql stable security definer set search_path = public as $$
  select c.id, c.status, c.status = 'request' and c.started_by <> me(), o.id, o.username::text, o.display_name, o.avatar_url,
         c.last_message_at, c.last_message_preview,
         (select count(*) from dm_messages m where m.conversation_id = c.id and m.sender_id <> me()
            and m.id > case when me() = c.user_low then c.low_last_read_id else c.high_last_read_id end),
         case when me() = c.user_low then c.high_last_read_id else c.low_last_read_id end
  from conversations c join profiles o on o.id = case when me() = c.user_low then c.user_high else c.user_low end
  where me() in (c.user_low, c.user_high) and c.last_message_at is not null and c.status <> 'declined'
    and not exists (select 1 from blocks b where b.blocker_id = me() and b.blocked_id = o.id)
    and ((p_requests and c.status = 'request' and c.started_by <> me()) or (not p_requests and not (c.status = 'request' and c.started_by <> me())))
  order by c.last_message_at desc limit least(p_limit, 100) offset p_offset
$$;

create or replace function mark_notifications_read(p_up_to bigint) returns void
language sql security definer set search_path = public as $$
  update notifications set read_at = now() where user_id = me() and id <= p_up_to and read_at is null
$$;

-- SERVICE ROLE ONLY. The push edge function claims a batch of unsent notifications with their device tokens.
create or replace function claim_push_batch(p_limit int default 200)
returns table (notification_id bigint, user_id uuid, type text, payload jsonb, actor_name text, tokens text[])
language plpgsql security definer set search_path = public as $$
begin
  return query
  with batch as (
    select n.id from notifications n where n.pushed_at is null and n.created_at > now() - interval '1 hour'
    order by n.id limit p_limit for update skip locked)
  update notifications n set pushed_at = now() from batch where n.id = batch.id
  returning n.id, n.user_id, n.type, n.payload,
    (select display_name from profiles where id = n.actor_id),
    (select array_agg(token) from push_tokens t where t.user_id = n.user_id);
end $$;

-- ================================================================ fan clubs
create or replace function upsert_fan_club(p_name text, p_badge_label text, p_benefits text, p_announcement text, p_join_cost int)
returns fan_clubs language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); c fan_clubs;
begin
  perform require_active(v_uid);
  if not (select is_creator from profiles where id = v_uid) then raise exception 'creators_only'; end if;
  if p_join_cost < 0 or p_join_cost > 100000 then raise exception 'invalid_join_cost'; end if;
  insert into fan_clubs (creator_id, name, badge_label, benefits, announcement, join_cost_coins)
  values (v_uid, btrim(p_name), btrim(p_badge_label), coalesce(p_benefits, ''), coalesce(p_announcement, ''), coalesce(p_join_cost, 0))
  on conflict (creator_id) do update set name = excluded.name, badge_label = excluded.badge_label, benefits = excluded.benefits,
    announcement = excluded.announcement, join_cost_coins = excluded.join_cost_coins
  returning * into c;
  return c;
end $$;

create or replace function join_fan_club(p_club uuid, p_idempotency_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); c fan_clubs; v_tx uuid; v_beans bigint;
begin
  perform require_active(v_uid);
  select * into c from fan_clubs where id = p_club;
  if not found then raise exception 'club_not_found'; end if;
  if c.creator_id = v_uid then raise exception 'cannot_join_own_club'; end if;
  if is_blocked_between(v_uid, c.creator_id) then raise exception 'blocked'; end if;
  if exists (select 1 from fan_club_members where club_id = p_club and user_id = v_uid) then
    return jsonb_build_object('joined', true, 'duplicate', true) || my_balances(v_uid);
  end if;
  if c.join_cost_coins > 0 then
    if not is_adult(v_uid) then raise exception 'paid_gifts_age_restricted'; end if;
    insert into ledger_transactions (kind, actor_id, idempotency_key, reference)
    values ('gift', v_uid, 'club:' || p_idempotency_key, jsonb_build_object('fan_club_id', c.id, 'recipient_id', c.creator_id))
    on conflict do nothing returning id into v_tx;
    if v_tx is null then return jsonb_build_object('joined', true, 'duplicate', true) || my_balances(v_uid); end if;
    perform 1 from wallet_accounts where id = wallet_account(v_uid, 'user_coins') for update;
    perform post_entry(v_tx, wallet_account(v_uid, 'user_coins'), -c.join_cost_coins, 'coin');
    perform post_entry(v_tx, wallet_account(null, 'sys_coin_revenue'), c.join_cost_coins, 'coin');
    v_beans := c.join_cost_coins::bigint * cfg_int('creator_bean_share_bps') / 10000;
    if v_beans > 0 then
      perform post_entry(v_tx, wallet_account(null, 'sys_bean_issuance'), -v_beans, 'bean');
      perform post_entry(v_tx, wallet_account(c.creator_id, 'creator_beans_pending'), v_beans, 'bean');
      insert into bean_settlements (transaction_id, creator_id, beans, release_at)
      values (v_tx, c.creator_id, v_beans, now() + make_interval(days => cfg_int('settlement_hold_days')::int));
    end if;
  end if;
  insert into fan_club_members (club_id, user_id, contribution_coins) values (p_club, v_uid, c.join_cost_coins);
  return jsonb_build_object('joined', true, 'duplicate', false) || my_balances(v_uid);
end $$;

create or replace function leave_fan_club(p_club uuid) returns void
language sql security definer set search_path = public as $$
  delete from fan_club_members where club_id = p_club and user_id = me()
$$;

create or replace function fan_club_detail(p_club uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('club', to_jsonb(c),
    'creator', jsonb_build_object('id', p.id, 'username', p.username, 'display_name', p.display_name, 'avatar_url', p.avatar_url),
    'member_count', (select count(*) from fan_club_members where club_id = c.id),
    'me', (select to_jsonb(m) from fan_club_members m where m.club_id = c.id and m.user_id = auth.uid()),
    'top_members', (select coalesce(jsonb_agg(x), '[]') from (
        select u.id, u.username, u.display_name, u.avatar_url, m.contribution_coins, m.loyalty_level
        from fan_club_members m join profiles u on u.id = m.user_id
        where m.club_id = c.id and u.account_status <> 'deleted' order by m.contribution_coins desc limit 50) x))
  from fan_clubs c join profiles p on p.id = c.creator_id where c.id = p_club
$$;

-- ================================================================ rankings & events
create or replace function period_start(p_period text) returns timestamptz
language sql stable as $$
  select (date_trunc(case p_period when 'daily' then 'day' when 'weekly' then 'week' else 'month' end,
                     now() at time zone 'Africa/Addis_Ababa')) at time zone 'Africa/Addis_Ababa'
$$;

create or replace function get_rankings(p_board text, p_period text, p_limit int default 50)
returns table (rank bigint, user_id uuid, username text, display_name text, avatar_url text, level int, score bigint)
language sql stable security definer set search_path = public as $$
  with scores as (
    select recipient_id as uid, sum(coins_spent + promo_coins_spent) as score from gift_transactions
    where p_board = 'creators' and created_at >= period_start(p_period) group by recipient_id
    union all
    select sender_id, sum(coins_spent + promo_coins_spent) from gift_transactions
    where p_board = 'supporters' and created_at >= period_start(p_period) group by sender_id
    union all
    select case when winner = 'a' then host_a else host_b end, count(*) from pk_matches
    where p_board = 'pk' and status = 'finished' and winner in ('a', 'b') and finished_at >= period_start(p_period)
    group by 1)
  select rank() over (order by s.score desc), p.id, p.username::text, p.display_name, p.avatar_url, p.level, s.score
  from scores s join profiles p on p.id = s.uid
  where p.account_status in ('active', 'warned') and s.score > 0
  order by s.score desc limit least(p_limit, 100)
$$;

create or replace function list_events() returns setof events
language sql stable security definer set search_path = public as $$
  select * from events where published and ends_at > now() - interval '7 days' order by starts_at
$$;

create or replace function join_event(p_event uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform require_active(me());
  if not exists (select 1 from events where id = p_event and published and ends_at > now()) then raise exception 'event_closed'; end if;
  insert into event_participants (event_id, user_id) values (p_event, me()) on conflict do nothing;
end $$;

create or replace function event_leaderboard(p_event uuid, p_limit int default 50)
returns table (rank bigint, user_id uuid, username text, display_name text, avatar_url text, score bigint)
language sql stable security definer set search_path = public as $$
  with e as (select * from events where id = p_event and published),
  scores as (
    select ep.user_id as uid,
      case e.metric
        when 'gift_coins_received' then (select coalesce(sum(coins_spent + promo_coins_spent), 0) from gift_transactions g
                                         where g.recipient_id = ep.user_id and g.created_at between e.starts_at and e.ends_at)
        when 'gift_coins_sent' then (select coalesce(sum(coins_spent + promo_coins_spent), 0) from gift_transactions g
                                     where g.sender_id = ep.user_id and g.created_at between e.starts_at and e.ends_at)
        else (select count(*) from pk_matches m where m.status = 'finished' and m.finished_at between e.starts_at and e.ends_at
              and ((m.winner = 'a' and m.host_a = ep.user_id) or (m.winner = 'b' and m.host_b = ep.user_id)))
      end as score
    from event_participants ep, e where ep.event_id = e.id)
  select rank() over (order by s.score desc), p.id, p.username::text, p.display_name, p.avatar_url, s.score
  from scores s join profiles p on p.id = s.uid where p.account_status in ('active', 'warned')
  order by s.score desc limit least(p_limit, 100)
$$;

-- ================================================================ scheduled maintenance
create or replace function run_maintenance() returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_room uuid; v_match uuid; n_rooms int := 0; n_pk int := 0; n_settled int;
begin
  -- Rooms whose host stopped sending heartbeats (crash, lost network) are ended.
  for v_room in select id from live_rooms where status = 'live'
                and last_heartbeat_at < now() - make_interval(secs => cfg_int('room_heartbeat_timeout_seconds')::int) loop
    perform end_room_internal(v_room, 'host_timeout'); n_rooms := n_rooms + 1;
  end loop;
  -- Rooms that never confirmed publishing are failed, never shown as live.
  update live_rooms set status = 'failed', ended_at = now(), end_reason = 'never_published'
  where status = 'preparing' and created_at < now() - interval '2 minutes';
  -- Guests who disconnected and did not come back free their seat.
  update room_participants set left_at = now(), seat_index = null,
    role = case when role = 'guest' then 'audience'::participant_role else role end
  where left_at is null and role <> 'host'
    and last_seen_at < now() - make_interval(secs => cfg_int('presence_timeout_seconds')::int);
  update seat_requests set status = 'expired', responded_at = now() where status = 'pending' and created_at < now() - interval '2 minutes';
  update pk_matches set status = 'expired' where status = 'invited' and invited_at < now() - interval '60 seconds';
  for v_match in select id from pk_matches where status = 'running' and ends_at <= now() loop
    perform pk_close(v_match, false); n_pk := n_pk + 1;
  end loop;
  perform recompute_viewers(id) from live_rooms where status = 'live';
  n_settled := settle_due_beans(1000);
  return jsonb_build_object('rooms_ended', n_rooms, 'pk_closed', n_pk, 'beans_settled', n_settled);
end $$;
