-- Ethio Live: read functions the admin dashboard needs for tables that clients cannot select directly
-- (platform_config, ledger_adjustment_requests) or can only partly see (unavailable gifts, inactive packages,
-- unpublished events, other users' wallet accounts).

create or replace function staff_config() returns table (key text, value jsonb, description text, updated_at timestamptz, updated_by uuid)
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['admin', 'finance']::staff_role[]);
  return query select c.key, c.value, c.description, c.updated_at, c.updated_by from platform_config c order by c.key;
end $$;

create or replace function staff_catalog() returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['admin', 'finance']::staff_role[]);
  return jsonb_build_object(
    'gifts', (select coalesce(jsonb_agg(to_jsonb(g) order by g.sort_order, g.id), '[]') from gifts g),
    'coin_packages', (select coalesce(jsonb_agg(to_jsonb(c) order by c.sort_order, c.id), '[]') from coin_packages c));
end $$;

create or replace function staff_events() returns setof events
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['admin', 'moderator']::staff_role[]);
  return query select * from events order by starts_at desc limit 200;
end $$;

create or replace function staff_wallet_accounts(p_user uuid) returns table (id uuid, kind wallet_kind, balance bigint, updated_at timestamptz)
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['finance']::staff_role[]);
  perform audit(auth.uid(), 'staff.view_wallet', 'user', p_user::text, '{}');
  return query select w.id, w.kind, w.balance, w.updated_at from wallet_accounts w where w.owner_id = p_user order by w.kind;
end $$;

create or replace function staff_adjustments(p_status text default 'pending')
returns table (id uuid, created_at timestamptz, account_id uuid, owner_username text, account_kind wallet_kind, amount bigint,
               unit text, reason text, requested_by uuid, requested_by_username text, approved_by uuid, status text)
language plpgsql stable security definer set search_path = public as $$
begin
  perform require_staff(array['finance']::staff_role[]);
  return query select a.id, a.created_at, a.account_id, p.username::text, w.kind, a.amount, a.unit, a.reason,
                      a.requested_by, rq.username::text, a.approved_by, a.status
  from ledger_adjustment_requests a
  join wallet_accounts w on w.id = a.account_id
  left join profiles p on p.id = w.owner_id
  join profiles rq on rq.id = a.requested_by
  where a.status = p_status order by a.created_at limit 200;
end $$;

grant execute on function staff_config(), staff_catalog(), staff_events(), staff_wallet_accounts(uuid), staff_adjustments(text)
  to authenticated, service_role;
