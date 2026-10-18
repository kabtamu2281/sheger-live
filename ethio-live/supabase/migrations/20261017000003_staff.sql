-- Ethio Live: staff (admin dashboard) functions.
-- Roles: support < moderator < admin < superadmin for trust & safety; finance is a separate track.
-- Every staff action is written to the append-only audit log in the same transaction.
-- Financial actions additionally require a multi-factor session (JWT aal = aal2).

create or replace function staff_role_of(p_user uuid) returns staff_role
language sql stable set search_path = public as $$
  select role from staff_members where user_id = p_user and active
$$;

-- Raises unless the caller holds one of the given roles (superadmin always passes).
create or replace function require_staff(p_roles staff_role[], p_mfa boolean default false) returns staff_role
language plpgsql stable set search_path = public as $$
declare v_role staff_role := staff_role_of(auth.uid());
begin
  if v_role is null then raise exception 'staff_only'; end if;
  if v_role <> 'superadmin' and not (v_role = any (p_roles)) then raise exception 'insufficient_staff_role'; end if;
  if p_mfa and coalesce(auth.jwt() ->> 'aal', 'aal1') <> 'aal2' then raise exception 'mfa_required'; end if;
  return v_role;
end $$;

create or replace function staff_whoami() returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('user_id', auth.uid(), 'role', staff_role_of(auth.uid()),
                            'aal', coalesce(auth.jwt() ->> 'aal', 'aal1'))
$$;

-- Initial provisioning. Not executable by app users (see grants); run once with the service role or psql:
--   select bootstrap_superadmin('owner@example.com');
-- It refuses to run if a superadmin already exists, so it cannot be used to take over a live system.
create or replace function bootstrap_superadmin(p_email text) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_uid uuid;
begin
  if exists (select 1 from staff_members where role = 'superadmin' and active) then raise exception 'superadmin_exists'; end if;
  select id into v_uid from auth.users where lower(email) = lower(p_email);
  if v_uid is null then raise exception 'user_not_found: sign up in the app or dashboard first'; end if;
  if not exists (select 1 from profiles where id = v_uid) then raise exception 'profile_required: finish app signup first'; end if;
  insert into staff_members (user_id, role, created_by) values (v_uid, 'superadmin', null);
  perform audit(null, 'staff.bootstrap_superadmin', 'user', v_uid::text, jsonb_build_object('email', p_email));
  return v_uid;
end $$;

create or replace function staff_set_role(p_user uuid, p_role staff_role, p_active boolean) returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me();
begin
  perform require_staff(array['superadmin']::staff_role[], true);
  if p_user = v_uid then raise exception 'cannot_change_own_role'; end if;
  insert into staff_members (user_id, role, active, created_by) values (p_user, p_role, p_active, v_uid)
  on conflict (user_id) do update set role = excluded.role, active = excluded.active;
  perform audit(v_uid, 'staff.set_role', 'user', p_user::text, jsonb_build_object('role', p_role, 'active', p_active));
end $$;

create or replace function staff_list_members() returns table (user_id uuid, username text, role staff_role, active boolean, created_at timestamptz)
language sql stable security definer set search_path = public as $$
  select s.user_id, p.username::text, s.role, s.active, s.created_at from staff_members s join profiles p on p.id = s.user_id
  where require_staff(array['admin']::staff_role[]) is not null order by s.created_at
$$;

-- ---------------------------------------------------------------- users
create or replace function staff_search_users(p_query text, p_status account_status default null, p_limit int default 50)
returns table (id uuid, username text, display_name text, account_status account_status, age_band age_band, level int,
               is_creator boolean, is_verified boolean, follower_count int, created_at timestamptz, open_reports bigint)
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['support', 'moderator', 'admin', 'finance']::staff_role[]);
  return query
  select p.id, p.username::text, p.display_name, p.account_status, p.age_band, p.level, p.is_creator, p.is_verified,
         p.follower_count, p.created_at,
         (select count(*) from reports r where r.target_user_id = p.id and r.status in ('open', 'reviewing', 'escalated'))
  from profiles p
  where (p_query is null or p_query = '' or p.username::text ilike '%' || p_query || '%' or p.display_name ilike '%' || p_query || '%'
         or p.id::text = p_query)
    and (p_status is null or p.account_status = p_status)
  order by p.created_at desc limit least(p_limit, 200);
end $$;

create or replace function staff_user_detail(p_user uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_role staff_role;
begin
  v_role := require_staff(array['support', 'moderator', 'admin', 'finance']::staff_role[]);
  perform audit(auth.uid(), 'staff.view_user', 'user', p_user::text, '{}');
  return jsonb_build_object(
    'profile', (select to_jsonb(p) from profiles p where id = p_user),
    'private', case when v_role in ('admin', 'superadmin', 'finance') then
       (select jsonb_build_object('age', user_age(p_user), 'kyc_status', kyc_status, 'terms_version', terms_version,
                                  'deletion_requested_at', deletion_requested_at)
        from user_private where user_id = p_user) end,
    'balances', case when v_role in ('finance', 'superadmin', 'admin') then my_balances(p_user) end,
    'moderation_history', (select coalesce(jsonb_agg(to_jsonb(m) order by m.created_at desc), '[]') from moderation_actions m where target_user_id = p_user),
    'reports_against', (select count(*) from reports where target_user_id = p_user),
    'recent_rooms', (select coalesce(jsonb_agg(x), '[]') from (select id, title, status, started_at, ended_at, end_reason
                     from live_rooms where host_id = p_user order by created_at desc limit 10) x));
end $$;

create or replace function staff_moderate(p_user uuid, p_kind moderation_kind, p_reason text, p_hours int,
                                          p_report uuid default null, p_room uuid default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); v_role staff_role; v_id uuid; v_until timestamptz;
begin
  v_role := require_staff(array['moderator', 'admin']::staff_role[]);
  if p_kind = 'ban' and v_role not in ('admin', 'superadmin') then raise exception 'insufficient_staff_role'; end if;
  if char_length(coalesce(p_reason, '')) < 5 then raise exception 'reason_required'; end if;
  if staff_role_of(p_user) is not null and v_role <> 'superadmin' then raise exception 'cannot_moderate_staff'; end if;
  v_until := case when p_hours is not null and p_hours > 0 then now() + make_interval(hours => p_hours) end;
  insert into moderation_actions (moderator_id, target_user_id, kind, reason, report_id, room_id, expires_at)
  values (v_uid, p_user, p_kind, p_reason, p_report, p_room, v_until) returning id into v_id;
  case p_kind
    when 'warn' then update profiles set account_status = 'warned' where id = p_user and account_status = 'active';
    when 'suspend' then update profiles set account_status = 'suspended', status_until = v_until where id = p_user;
    when 'ban' then update profiles set account_status = 'banned', status_until = null where id = p_user;
    when 'broadcast_ban' then update profiles set broadcast_banned_until = coalesce(v_until, 'infinity') where id = p_user;
    when 'restore' then update profiles set account_status = 'active', status_until = null, broadcast_banned_until = null where id = p_user;
    else null;
  end case;
  if p_kind in ('suspend', 'ban', 'broadcast_ban', 'end_stream') then
    perform end_room_internal(r.id, 'moderation:' || p_kind) from live_rooms r where r.host_id = p_user and r.status in ('preparing', 'live');
  end if;
  if p_kind = 'remove_content' and p_room is not null then
    update room_messages set removed_at = now() where room_id = p_room and sender_id = p_user and removed_at is null;
  end if;
  if p_report is not null then
    update reports set status = 'actioned', resolved_at = now(), resolution = p_kind::text || ': ' || p_reason, assigned_to = v_uid
    where id = p_report;
  end if;
  insert into notifications (user_id, type, payload)
  values (p_user, 'moderation', jsonb_build_object('action_id', v_id, 'kind', p_kind, 'reason', p_reason, 'until', v_until));
  perform audit(v_uid, 'moderation.' || p_kind, 'user', p_user::text,
                jsonb_build_object('reason', p_reason, 'hours', p_hours, 'report_id', p_report, 'action_id', v_id));
  return v_id;
end $$;

create or replace function staff_end_room(p_room uuid, p_reason text) returns void
language plpgsql security definer set search_path = public as $$
declare v_host uuid;
begin
  perform require_staff(array['moderator', 'admin']::staff_role[]);
  select host_id into v_host from live_rooms where id = p_room;
  perform staff_moderate(v_host, 'end_stream', p_reason, null, null, p_room);
end $$;

-- ---------------------------------------------------------------- reports & appeals
create or replace function staff_report_queue(p_status report_status default 'open', p_limit int default 100)
returns table (id uuid, created_at timestamptz, reason report_reason, priority int, target_type report_target, target_id text,
               target_user_id uuid, target_username text, reporter_username text, details text, evidence jsonb,
               status report_status, prior_actions bigint)
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['moderator', 'admin', 'support']::staff_role[]);
  return query
  select r.id, r.created_at, r.reason, r.priority, r.target_type, r.target_id, r.target_user_id, t.username::text,
         rp.username::text, r.details,
         -- Evidence (which can contain private message text) is visible to moderators and above only.
         case when staff_role_of(auth.uid()) in ('moderator', 'admin', 'superadmin') then r.evidence else '{}'::jsonb end,
         r.status, (select count(*) from moderation_actions m where m.target_user_id = r.target_user_id)
  from reports r left join profiles t on t.id = r.target_user_id join profiles rp on rp.id = r.reporter_id
  where r.status = p_status order by r.priority, r.created_at limit least(p_limit, 500);
end $$;

create or replace function staff_resolve_report(p_report uuid, p_status report_status, p_resolution text) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform require_staff(array['moderator', 'admin']::staff_role[]);
  if p_status = 'escalated' then
    update reports set status = 'escalated', priority = 1, assigned_to = null where id = p_report;
  else
    update reports set status = p_status, resolution = p_resolution, resolved_at = now(), assigned_to = auth.uid() where id = p_report;
  end if;
  perform audit(auth.uid(), 'report.' || p_status, 'report', p_report::text, jsonb_build_object('resolution', p_resolution));
end $$;

create or replace function staff_appeals(p_status appeal_status default 'open')
returns table (id uuid, created_at timestamptz, user_id uuid, username text, message text, action_kind moderation_kind,
               action_reason text, action_at timestamptz, status appeal_status)
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['moderator', 'admin']::staff_role[]);
  return query select a.id, a.created_at, a.user_id, p.username::text, a.message, m.kind, m.reason, m.created_at, a.status
  from appeals a join moderation_actions m on m.id = a.moderation_action_id join profiles p on p.id = a.user_id
  where a.status = p_status order by a.created_at;
end $$;

create or replace function staff_decide_appeal(p_appeal uuid, p_overturn boolean, p_note text) returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); a appeals; m moderation_actions;
begin
  perform require_staff(array['moderator', 'admin']::staff_role[]);
  select * into a from appeals where id = p_appeal for update;
  select * into m from moderation_actions where id = a.moderation_action_id;
  -- Four-eyes rule: the moderator who took the action cannot decide its appeal.
  if m.moderator_id = v_uid then raise exception 'cannot_review_own_action'; end if;
  update appeals set status = case when p_overturn then 'overturned'::appeal_status else 'upheld'::appeal_status end,
    decided_by = v_uid, decision_note = p_note, decided_at = now() where id = p_appeal;
  if p_overturn then
    perform staff_moderate(a.user_id, 'restore', 'Appeal overturned: ' || coalesce(p_note, ''), null);
  end if;
  insert into notifications (user_id, type, payload)
  values (a.user_id, 'moderation', jsonb_build_object('appeal_id', a.id, 'overturned', p_overturn, 'note', p_note));
  perform audit(v_uid, 'appeal.decided', 'appeal', p_appeal::text, jsonb_build_object('overturned', p_overturn));
end $$;

-- ---------------------------------------------------------------- live monitoring
create or replace function staff_live_rooms() returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['support', 'moderator', 'admin']::staff_role[]);
  return (select coalesce(jsonb_agg(card order by viewers desc), '[]') from (select room_card_json(r) || jsonb_build_object('total_gift_coins', r.total_gift_coins,
      'open_reports', (select count(*) from reports x where x.target_type = 'room' and x.target_id = r.id::text and x.status = 'open'))
      as card, r.viewer_count as viewers
    from live_rooms r where r.status = 'live') x);
end $$;

-- ---------------------------------------------------------------- catalog & config
create or replace function staff_upsert_gift(p jsonb) returns gifts
language plpgsql security definer set search_path = public as $$
declare g gifts;
begin
  perform require_staff(array['admin']::staff_role[]);
  insert into gifts (id, name, name_am, category, price_coins, artwork_key, animation_key, is_animated_fullscreen, available, sort_order, event_id)
  values (p ->> 'id', p ->> 'name', p ->> 'name_am', p ->> 'category', (p ->> 'price_coins')::int, p ->> 'artwork_key',
          p ->> 'animation_key', coalesce((p ->> 'is_animated_fullscreen')::boolean, false), coalesce((p ->> 'available')::boolean, true),
          coalesce((p ->> 'sort_order')::int, 0), nullif(p ->> 'event_id', '')::uuid)
  on conflict (id) do update set name = excluded.name, name_am = excluded.name_am, category = excluded.category,
    price_coins = excluded.price_coins, artwork_key = excluded.artwork_key, animation_key = excluded.animation_key,
    is_animated_fullscreen = excluded.is_animated_fullscreen, available = excluded.available, sort_order = excluded.sort_order,
    event_id = excluded.event_id, updated_at = now()
  returning * into g;
  perform audit(auth.uid(), 'catalog.gift_upsert', 'gift', g.id, p);
  return g;
end $$;

-- Economic settings (shares, conversion, fees) need finance + MFA; other settings need admin.
create or replace function staff_set_config(p_key text, p_value jsonb) returns void
language plpgsql security definer set search_path = public as $$
declare v_old jsonb;
begin
  if p_key in ('creator_bean_share_bps', 'promo_bean_share_bps', 'bean_to_etb_minor', 'payout_fee_bps', 'payout_min_beans',
               'payout_second_approval_beans', 'settlement_hold_days', 'pk_points_per_coin') then
    perform require_staff(array['finance']::staff_role[], true);
  else
    perform require_staff(array['admin']::staff_role[]);
  end if;
  select value into v_old from platform_config where key = p_key;
  if v_old is null then raise exception 'unknown_config_key'; end if;
  update platform_config set value = p_value, updated_at = now(), updated_by = auth.uid() where key = p_key;
  perform audit(auth.uid(), 'config.set', 'config', p_key, jsonb_build_object('old', v_old, 'new', p_value));
end $$;

create or replace function staff_upsert_coin_package(p jsonb) returns coin_packages
language plpgsql security definer set search_path = public as $$
declare c coin_packages;
begin
  perform require_staff(array['finance']::staff_role[], true);
  insert into coin_packages (id, coins, bonus_promo_coins, price_minor, currency, play_product_id, active, sort_order)
  values (p ->> 'id', (p ->> 'coins')::int, coalesce((p ->> 'bonus_promo_coins')::int, 0), (p ->> 'price_minor')::bigint,
          coalesce(p ->> 'currency', 'ETB'), nullif(p ->> 'play_product_id', ''), coalesce((p ->> 'active')::boolean, true),
          coalesce((p ->> 'sort_order')::int, 0))
  on conflict (id) do update set coins = excluded.coins, bonus_promo_coins = excluded.bonus_promo_coins,
    price_minor = excluded.price_minor, currency = excluded.currency, play_product_id = excluded.play_product_id,
    active = excluded.active, sort_order = excluded.sort_order
  returning * into c;
  perform audit(auth.uid(), 'catalog.coin_package_upsert', 'coin_package', c.id, p);
  return c;
end $$;

create or replace function staff_upsert_event(p jsonb) returns events
language plpgsql security definer set search_path = public as $$
declare e events;
begin
  perform require_staff(array['admin']::staff_role[]);
  insert into events (id, title, description, rules, reward_description, metric, banner_key, starts_at, ends_at, published, created_by)
  values (coalesce(nullif(p ->> 'id', '')::uuid, gen_random_uuid()), p ->> 'title', p ->> 'description', p ->> 'rules',
          p ->> 'reward_description', p ->> 'metric', coalesce(p ->> 'banner_key', 'event_default'),
          (p ->> 'starts_at')::timestamptz, (p ->> 'ends_at')::timestamptz, coalesce((p ->> 'published')::boolean, false), auth.uid())
  on conflict (id) do update set title = excluded.title, description = excluded.description, rules = excluded.rules,
    reward_description = excluded.reward_description, metric = excluded.metric, banner_key = excluded.banner_key,
    starts_at = excluded.starts_at, ends_at = excluded.ends_at, published = excluded.published
  returning * into e;
  perform audit(auth.uid(), 'event.upsert', 'event', e.id::text, p);
  return e;
end $$;

-- ---------------------------------------------------------------- finance
create or replace function staff_payment_orders(p_status payment_status default null, p_limit int default 100)
returns table (id uuid, created_at timestamptz, username text, provider payment_provider, package_id text, coins int,
               amount_minor bigint, currency text, status payment_status, provider_ref text, failure_reason text)
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['finance', 'support']::staff_role[]);
  return query select o.id, o.created_at, p.username::text, o.provider, o.package_id, o.coins, o.amount_minor, o.currency,
                      o.status, o.provider_ref, o.failure_reason
  from payment_orders o join profiles p on p.id = o.user_id
  where p_status is null or o.status = p_status order by o.created_at desc limit least(p_limit, 500);
end $$;

create or replace function staff_payouts(p_status payout_status default null)
returns table (id uuid, created_at timestamptz, creator_id uuid, username text, beans bigint, net_etb_minor bigint,
               fee_etb_minor bigint, method jsonb, status payout_status, kyc_status kyc_status, first_approver uuid,
               needs_second_approval boolean, account_age_days int, open_reports bigint)
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['finance']::staff_role[]);
  return query select pr.id, pr.created_at, pr.creator_id, p.username::text, pr.beans, pr.net_etb_minor, pr.fee_etb_minor,
    pr.method, pr.status, up.kyc_status, pr.first_approver, pr.beans >= cfg_int('payout_second_approval_beans'),
    (current_date - p.created_at::date)::int,
    (select count(*) from reports r where r.target_user_id = pr.creator_id and r.status in ('open', 'escalated'))
  from payout_requests pr join profiles p on p.id = pr.creator_id join user_private up on up.user_id = pr.creator_id
  where p_status is null or pr.status = p_status order by pr.created_at;
end $$;

create or replace function staff_review_payout(p_payout uuid, p_approve boolean, p_reason text) returns payout_requests
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); pr payout_requests; v_tx uuid;
begin
  perform require_staff(array['finance']::staff_role[], true);
  select * into pr from payout_requests where id = p_payout for update;
  if not found or pr.status not in ('requested', 'under_review') then raise exception 'payout_not_reviewable'; end if;
  if pr.creator_id = v_uid then raise exception 'cannot_review_own_payout'; end if;
  if not p_approve then
    insert into ledger_transactions (kind, actor_id, idempotency_key, reference)
    values ('payout_rejected', pr.creator_id, 'payout_reject:' || pr.id, jsonb_build_object('payout_id', pr.id, 'reason', p_reason))
    returning id into v_tx;
    perform post_entry(v_tx, wallet_account(pr.creator_id, 'payout_hold'), -pr.beans, 'bean');
    perform post_entry(v_tx, wallet_account(pr.creator_id, 'creator_beans'), pr.beans, 'bean');
    update payout_requests set status = 'rejected', rejection_reason = p_reason, updated_at = now() where id = p_payout returning * into pr;
    insert into notifications (user_id, type, payload) values (pr.creator_id, 'payout', jsonb_build_object('payout_id', pr.id, 'status', 'rejected', 'reason', p_reason));
  elsif (select kyc_status from user_private where user_id = pr.creator_id) <> 'verified' then
    raise exception 'kyc_required';
  elsif pr.beans >= cfg_int('payout_second_approval_beans') and pr.first_approver is null then
    update payout_requests set first_approver = v_uid, status = 'under_review', updated_at = now() where id = p_payout returning * into pr;
  elsif pr.first_approver = v_uid then
    raise exception 'second_approver_must_differ';
  else
    update payout_requests set status = 'approved',
      first_approver = coalesce(first_approver, v_uid),
      second_approver = case when first_approver is not null then v_uid end, updated_at = now()
    where id = p_payout returning * into pr;
    insert into notifications (user_id, type, payload) values (pr.creator_id, 'payout', jsonb_build_object('payout_id', pr.id, 'status', 'approved'));
  end if;
  perform audit(v_uid, 'payout.review', 'payout', p_payout::text, jsonb_build_object('approve', p_approve, 'reason', p_reason, 'status', pr.status));
  return pr;
end $$;

create or replace function staff_mark_payout_paid(p_payout uuid, p_provider_ref text, p_success boolean) returns payout_requests
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); pr payout_requests; v_tx uuid;
begin
  perform require_staff(array['finance']::staff_role[], true);
  select * into pr from payout_requests where id = p_payout for update;
  if not found or pr.status <> 'approved' then raise exception 'payout_not_approved'; end if;
  if p_success then
    insert into ledger_transactions (kind, actor_id, idempotency_key, reference)
    values ('payout_paid', pr.creator_id, 'payout_paid:' || pr.id, jsonb_build_object('payout_id', pr.id, 'provider_ref', p_provider_ref))
    returning id into v_tx;
    perform post_entry(v_tx, wallet_account(pr.creator_id, 'payout_hold'), -pr.beans, 'bean');
    perform post_entry(v_tx, wallet_account(null, 'sys_bean_redeemed'), pr.beans, 'bean');
    update payout_requests set status = 'paid', provider_ref = p_provider_ref, updated_at = now() where id = p_payout returning * into pr;
  else
    insert into ledger_transactions (kind, actor_id, idempotency_key, reference)
    values ('payout_rejected', pr.creator_id, 'payout_failed:' || pr.id, jsonb_build_object('payout_id', pr.id, 'provider_ref', p_provider_ref))
    returning id into v_tx;
    perform post_entry(v_tx, wallet_account(pr.creator_id, 'payout_hold'), -pr.beans, 'bean');
    perform post_entry(v_tx, wallet_account(pr.creator_id, 'creator_beans'), pr.beans, 'bean');
    update payout_requests set status = 'failed', provider_ref = p_provider_ref, updated_at = now() where id = p_payout returning * into pr;
  end if;
  insert into notifications (user_id, type, payload) values (pr.creator_id, 'payout', jsonb_build_object('payout_id', pr.id, 'status', pr.status));
  perform audit(v_uid, 'payout.' || pr.status, 'payout', p_payout::text, jsonb_build_object('provider_ref', p_provider_ref));
  return pr;
end $$;

create or replace function staff_set_kyc(p_user uuid, p_status kyc_status, p_reference text) returns void
language plpgsql security definer set search_path = public as $$
begin
  perform require_staff(array['finance']::staff_role[], true);
  update user_private set kyc_status = p_status, kyc_reference = p_reference where user_id = p_user;
  if p_status = 'verified' then
    insert into user_badges (user_id, badge_id) values (p_user, 'verified_creator') on conflict do nothing;
  end if;
  perform audit(auth.uid(), 'kyc.set', 'user', p_user::text, jsonb_build_object('status', p_status, 'reference', p_reference));
end $$;

-- Ledger corrections are never edits: one finance user requests, a different one approves, and a balanced
-- transaction against the matching system issuance account is posted.
create or replace function staff_request_adjustment(p_account uuid, p_amount bigint, p_unit text, p_reason text) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
  perform require_staff(array['finance']::staff_role[], true);
  if (select owner_id from wallet_accounts where id = p_account) is null then raise exception 'user_accounts_only'; end if;
  insert into ledger_adjustment_requests (account_id, amount, unit, reason, requested_by)
  values (p_account, p_amount, p_unit, p_reason, auth.uid()) returning id into v_id;
  perform audit(auth.uid(), 'ledger.adjustment_requested', 'adjustment', v_id::text,
                jsonb_build_object('account', p_account, 'amount', p_amount, 'unit', p_unit, 'reason', p_reason));
  return v_id;
end $$;

create or replace function staff_decide_adjustment(p_request uuid, p_approve boolean) returns void
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := me(); a ledger_adjustment_requests; v_tx uuid; v_owner uuid;
begin
  perform require_staff(array['finance']::staff_role[], true);
  select * into a from ledger_adjustment_requests where id = p_request for update;
  if not found or a.status <> 'pending' then raise exception 'adjustment_not_pending'; end if;
  if a.requested_by = v_uid then raise exception 'second_approver_must_differ'; end if;
  if p_approve then
    select owner_id into v_owner from wallet_accounts where id = a.account_id;
    insert into ledger_transactions (kind, actor_id, idempotency_key, reference)
    values ('adjustment', v_owner, 'adjust:' || a.id, jsonb_build_object('request_id', a.id, 'reason', a.reason,
                                                                         'requested_by', a.requested_by, 'approved_by', v_uid))
    returning id into v_tx;
    perform post_entry(v_tx, a.account_id, a.amount, a.unit);
    perform post_entry(v_tx, wallet_account(null, case when a.unit = 'coin' then 'sys_coin_issuance'::wallet_kind
                                                       else 'sys_bean_issuance'::wallet_kind end), -a.amount, a.unit);
  end if;
  update ledger_adjustment_requests set status = case when p_approve then 'applied' else 'rejected' end,
    approved_by = v_uid, decided_at = now(), ledger_transaction_id = v_tx where id = p_request;
  perform audit(v_uid, 'ledger.adjustment_' || case when p_approve then 'applied' else 'rejected' end, 'adjustment', p_request::text, '{}');
end $$;

-- ---------------------------------------------------------------- support, analytics, audit
create or replace function staff_support_queue(p_status text default 'open')
returns table (id uuid, created_at timestamptz, username text, topic text, message text, status text, staff_reply text)
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['support', 'admin']::staff_role[]);
  return query select s.id, s.created_at, p.username::text, s.topic, s.message, s.status, s.staff_reply
  from support_requests s left join profiles p on p.id = s.user_id where s.status = p_status order by s.created_at;
end $$;

create or replace function staff_reply_support(p_request uuid, p_reply text, p_close boolean) returns void
language plpgsql security definer set search_path = public as $$
declare v_user uuid;
begin
  perform require_staff(array['support', 'admin']::staff_role[]);
  update support_requests set staff_reply = p_reply, status = case when p_close then 'closed' else 'answered' end, updated_at = now()
  where id = p_request returning user_id into v_user;
  insert into notifications (user_id, type, payload) values (v_user, 'account', jsonb_build_object('support_request_id', p_request, 'reply', left(p_reply, 200)));
  perform audit(auth.uid(), 'support.reply', 'support_request', p_request::text, '{}');
end $$;

create or replace function staff_analytics() returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['support', 'moderator', 'admin', 'finance']::staff_role[]);
  return jsonb_build_object(
    'users_total', (select count(*) from profiles where account_status <> 'deleted'),
    'users_new_7d', (select count(*) from profiles where created_at > now() - interval '7 days'),
    'live_now', (select count(*) from live_rooms where status = 'live'),
    'viewers_now', (select coalesce(sum(viewer_count), 0) from live_rooms where status = 'live'),
    'streams_24h', (select count(*) from stream_sessions where started_at > now() - interval '24 hours'),
    'gift_coins_24h', (select coalesce(sum(coins_spent + promo_coins_spent), 0) from gift_transactions where created_at > now() - interval '24 hours'),
    'paid_orders_24h', (select count(*) from payment_orders where status = 'paid' and verified_at > now() - interval '24 hours'),
    'open_reports', (select count(*) from reports where status in ('open', 'escalated')),
    'urgent_reports', (select count(*) from reports where status in ('open', 'escalated') and priority = 1),
    'open_appeals', (select count(*) from appeals where status = 'open'),
    'pending_payouts', (select count(*) from payout_requests where status in ('requested', 'under_review')),
    'system_balances', case when staff_role_of(auth.uid()) in ('finance', 'superadmin')
                            then (select jsonb_object_agg(kind, balance) from system_account_balances) end,
    'daily_signups', (select coalesce(jsonb_agg(x order by x.day), '[]') from (
        select created_at::date as day, count(*) as n from profiles where created_at > now() - interval '30 days' group by 1) x));
end $$;

create or replace function staff_audit_log(p_limit int default 100, p_before bigint default null, p_actor uuid default null)
returns setof audit_logs language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['admin', 'finance']::staff_role[]);
  return query select * from audit_logs where (p_before is null or id < p_before) and (p_actor is null or actor_id = p_actor)
  order by id desc limit least(p_limit, 500);
end $$;
