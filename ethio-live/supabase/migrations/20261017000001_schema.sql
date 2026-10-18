-- Ethio Live: core schema.
-- All money-like values are integers (coins, beans, minor currency units). Never floats.
-- Clients never write these tables directly: every write goes through a SECURITY DEFINER
-- function in 20261017000002_functions.sql, and RLS (20261017000003_rls.sql) only grants reads.

create extension if not exists citext;
create extension if not exists pgcrypto;
create extension if not exists pg_trgm;

-- ---------------------------------------------------------------- enums
create type account_status as enum ('active', 'warned', 'suspended', 'banned', 'deleted');
create type age_band as enum ('minor', 'adult');
create type room_mode as enum ('video', 'audio', 'multi_guest', 'gaming');
create type room_status as enum ('preparing', 'live', 'ended', 'failed', 'banned');
create type room_audience as enum ('public', 'followers');
create type participant_role as enum ('host', 'guest', 'audience', 'moderator');
create type seat_request_kind as enum ('request', 'invite');
create type seat_request_status as enum ('pending', 'accepted', 'rejected', 'cancelled', 'expired');
create type report_target as enum ('user', 'room', 'room_message', 'dm_message');
create type report_reason as enum ('spam', 'harassment', 'hate', 'nudity', 'violence', 'minor_safety',
                                   'scam', 'impersonation', 'self_harm', 'illegal', 'other');
create type report_status as enum ('open', 'reviewing', 'actioned', 'dismissed', 'escalated');
create type dm_policy as enum ('everyone', 'followers', 'nobody');
create type conversation_status as enum ('active', 'request', 'declined');
create type wallet_kind as enum (
  'user_coins',            -- purchased coins (spendable)
  'user_promo',            -- promotional credits (spendable on gifts, never withdrawable)
  'creator_beans_pending', -- beans earned from gifts, in the settlement hold period
  'creator_beans',         -- settled beans, eligible for withdrawal requests
  'payout_hold',           -- beans locked by an open payout request
  'sys_coin_issuance',     -- system: source of purchased coins (may go negative)
  'sys_promo_issuance',    -- system: source of promotional coins (may go negative)
  'sys_coin_revenue',      -- system: coins spent on gifts
  'sys_bean_issuance',     -- system: source of creator beans (may go negative)
  'sys_bean_redeemed'      -- system: beans paid out
);
create type ledger_kind as enum ('gift', 'purchase', 'promo_grant', 'settlement', 'payout_request',
                                 'payout_paid', 'payout_rejected', 'refund', 'adjustment');
create type payment_provider as enum ('google_play', 'chapa', 'telebirr');
create type payment_status as enum ('created', 'pending', 'paid', 'failed', 'refunded', 'cancelled');
create type payout_status as enum ('requested', 'under_review', 'approved', 'rejected', 'paid', 'failed');
create type kyc_status as enum ('none', 'pending', 'verified', 'rejected');
create type pk_status as enum ('invited', 'running', 'finished', 'declined', 'cancelled', 'expired');
create type pk_side as enum ('a', 'b');
create type staff_role as enum ('support', 'moderator', 'finance', 'admin', 'superadmin');
create type moderation_kind as enum ('warn', 'mute', 'suspend', 'ban', 'broadcast_ban', 'end_stream',
                                     'remove_content', 'restore');
create type appeal_status as enum ('open', 'upheld', 'overturned');

-- ---------------------------------------------------------------- config
create table platform_config (
  key text primary key,
  value jsonb not null,
  description text not null default '',
  updated_at timestamptz not null default now(),
  updated_by uuid
);

insert into platform_config (key, value, description) values
  ('min_signup_age', '16', 'Minimum age to create an account'),
  ('adult_age', '18', 'Age required to broadcast, buy coins, send paid gifts and withdraw'),
  ('creator_bean_share_bps', '5000', 'Beans credited to the creator per 10000 purchased coins spent (basis points)'),
  ('promo_bean_share_bps', '0', 'Beans credited per 10000 promotional coins spent (0 = promo gifts earn nothing)'),
  ('settlement_hold_days', '7', 'Days a bean stays pending before it becomes eligible for withdrawal'),
  ('payout_min_beans', '20000', 'Minimum beans per withdrawal request'),
  ('bean_to_etb_minor', '4000', 'ETB cents paid per 100 beans, before fees (100 beans = ETB 40.00)'),
  ('payout_fee_bps', '200', 'Payout processing fee in basis points'),
  ('payout_second_approval_beans', '200000', 'Payouts at or above this need a second finance approver'),
  ('room_message_rate', '{"count": 5, "seconds": 10}', 'Max chat messages per user per room per window'),
  ('dm_rate', '{"count": 20, "seconds": 60}', 'Max direct messages per user per window'),
  ('free_gifts_per_day', '20', 'Free gifts a user can send per day'),
  ('pk_default_seconds', '300', 'Default PK battle length'),
  ('pk_points_per_coin', '1', 'PK points per purchased or promo coin spent during a match'),
  ('presence_timeout_seconds', '45', 'Participants without a heartbeat for this long are no longer counted'),
  ('room_heartbeat_timeout_seconds', '60', 'Live rooms whose host has not sent a heartbeat for this long are ended');

create or replace function cfg_int(p_key text) returns bigint
language sql stable as $$ select (value #>> '{}')::bigint from platform_config where key = p_key $$;

-- ---------------------------------------------------------------- users
create table reserved_usernames (username citext primary key);
insert into reserved_usernames values ('admin'), ('administrator'), ('ethiolive'), ('ethio_live'), ('support'),
  ('moderator'), ('official'), ('staff'), ('system'), ('help'), ('security'), ('root'), ('bigo'), ('bigolive');

create table profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username citext not null unique check (username ~ '^[a-zA-Z0-9_.]{3,24}$'),
  display_name text not null check (char_length(display_name) between 1 and 40),
  bio text not null default '' check (char_length(bio) <= 300),
  avatar_url text,
  language text not null default 'en' check (language in ('en', 'am', 'om', 'ti', 'so', 'sw', 'fr', 'ar')),
  interests text[] not null default '{}',
  country text not null default 'ET',
  age_band age_band not null,
  level int not null default 1,
  xp bigint not null default 0,
  is_creator boolean not null default false,
  is_verified boolean not null default false,
  account_status account_status not null default 'active',
  status_until timestamptz,
  broadcast_banned_until timestamptz,
  private_account boolean not null default false,
  follower_count int not null default 0,
  following_count int not null default 0,
  total_gift_coins_received bigint not null default 0,
  rtc_uid int generated always as identity unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index profiles_username_trgm on profiles using gin ((username::text) gin_trgm_ops);
create index profiles_display_name_trgm on profiles using gin (display_name gin_trgm_ops);

-- Private details, readable only by the owner (and staff through audited functions).
create table user_private (
  user_id uuid primary key references profiles (id) on delete cascade,
  date_of_birth date not null,
  terms_version text not null,
  terms_accepted_at timestamptz not null default now(),
  kyc_status kyc_status not null default 'none',
  kyc_reference text,
  payout_method jsonb,
  deletion_requested_at timestamptz
);

create table user_settings (
  user_id uuid primary key references profiles (id) on delete cascade,
  dm_policy dm_policy not null default 'everyone',
  notify_follows boolean not null default true,
  notify_live_start boolean not null default true,
  notify_gifts boolean not null default true,
  notify_messages boolean not null default true,
  notify_pk boolean not null default true,
  notify_guest_invites boolean not null default true,
  show_in_nearby boolean not null default false,
  show_gift_highlights boolean not null default true,
  recommendations_personalized boolean not null default true,
  updated_at timestamptz not null default now()
);

create table creator_levels (
  level int primary key,
  min_xp bigint not null unique,
  title text not null
);
insert into creator_levels values
  (1, 0, 'Newcomer'), (2, 500, 'Rising'), (3, 2000, 'Bright'), (4, 6000, 'Shining'), (5, 15000, 'Star'),
  (6, 40000, 'Superstar'), (7, 100000, 'Legend'), (8, 250000, 'Icon');

create table badges (
  id text primary key,
  name text not null,
  description text not null,
  icon_key text not null
);
insert into badges values
  ('first_live', 'First Live', 'Completed a first broadcast', 'badge_mic'),
  ('pk_winner', 'PK Winner', 'Won a PK battle', 'badge_trophy'),
  ('generous', 'Generous', 'Sent 10,000 coins in gifts', 'badge_heart'),
  ('verified_creator', 'Verified Creator', 'Identity verified creator', 'badge_check');

create table user_badges (
  user_id uuid references profiles (id) on delete cascade,
  badge_id text references badges (id),
  awarded_at timestamptz not null default now(),
  primary key (user_id, badge_id)
);

create table push_tokens (
  token text primary key,
  user_id uuid not null references profiles (id) on delete cascade,
  platform text not null default 'android',
  updated_at timestamptz not null default now()
);
create index push_tokens_user on push_tokens (user_id);

-- ---------------------------------------------------------------- social graph
create table follows (
  follower_id uuid references profiles (id) on delete cascade,
  followee_id uuid references profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, followee_id),
  check (follower_id <> followee_id)
);
create index follows_followee on follows (followee_id, created_at desc);

create table blocks (
  blocker_id uuid references profiles (id) on delete cascade,
  blocked_id uuid references profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
create index blocks_blocked on blocks (blocked_id);

-- ---------------------------------------------------------------- live rooms
create table live_rooms (
  id uuid primary key default gen_random_uuid(),
  host_id uuid not null references profiles (id),
  mode room_mode not null,
  title text not null check (char_length(title) between 1 and 60),
  category text not null check (category in ('music', 'chat', 'dance', 'gaming', 'education', 'comedy', 'culture', 'other')),
  language text not null default 'en',
  hashtags text[] not null default '{}',
  cover_url text,
  audience room_audience not null default 'public',
  max_seats int not null default 1 check (max_seats in (1, 2, 4, 6, 9)),
  channel_name text not null unique default ('el_' || replace(gen_random_uuid()::text, '-', '')),
  status room_status not null default 'preparing',
  viewer_count int not null default 0,
  peak_viewers int not null default 0,
  total_gift_coins bigint not null default 0,
  created_at timestamptz not null default now(),
  started_at timestamptz,
  ended_at timestamptz,
  last_heartbeat_at timestamptz not null default now(),
  end_reason text
);
create index live_rooms_live on live_rooms (status, viewer_count desc) where status = 'live';
create index live_rooms_host on live_rooms (host_id, created_at desc);
create index live_rooms_title_trgm on live_rooms using gin (title gin_trgm_ops);
-- A host can have at most one active room.
create unique index live_rooms_one_active_per_host on live_rooms (host_id) where status in ('preparing', 'live');

create table stream_sessions (
  room_id uuid primary key references live_rooms (id) on delete cascade,
  host_id uuid not null references profiles (id),
  started_at timestamptz not null,
  ended_at timestamptz,
  duration_seconds int,
  peak_viewers int not null default 0,
  gift_coins bigint not null default 0,
  new_followers int not null default 0
);

create table room_participants (
  room_id uuid references live_rooms (id) on delete cascade,
  user_id uuid references profiles (id) on delete cascade,
  role participant_role not null default 'audience',
  seat_index int check (seat_index between 0 and 8),
  mic_muted boolean not null default false,
  camera_off boolean not null default false,
  muted_by_host boolean not null default false,
  joined_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  left_at timestamptz,
  kicked_at timestamptz,
  primary key (room_id, user_id)
);
create unique index room_participants_seat on room_participants (room_id, seat_index)
  where seat_index is not null and left_at is null;
create index room_participants_presence on room_participants (room_id, last_seen_at) where left_at is null;

create table seat_requests (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references live_rooms (id) on delete cascade,
  user_id uuid not null references profiles (id) on delete cascade,
  kind seat_request_kind not null,
  status seat_request_status not null default 'pending',
  created_at timestamptz not null default now(),
  responded_at timestamptz
);
create unique index seat_requests_one_pending on seat_requests (room_id, user_id) where status = 'pending';

create table room_moderators (
  room_id uuid references live_rooms (id) on delete cascade,
  user_id uuid references profiles (id) on delete cascade,
  primary key (room_id, user_id)
);

create table room_mutes (
  room_id uuid references live_rooms (id) on delete cascade,
  user_id uuid references profiles (id) on delete cascade,
  muted_by uuid not null references profiles (id),
  until timestamptz not null,
  primary key (room_id, user_id)
);

create table room_messages (
  id bigint generated always as identity primary key,
  room_id uuid not null references live_rooms (id) on delete cascade,
  sender_id uuid references profiles (id) on delete set null,
  kind text not null default 'chat' check (kind in ('chat', 'system', 'gift', 'join', 'pk')),
  body text not null check (char_length(body) between 1 and 300),
  meta jsonb not null default '{}',
  created_at timestamptz not null default now(),
  removed_at timestamptz
);
create index room_messages_room on room_messages (room_id, id desc);
create index room_messages_rate on room_messages (sender_id, room_id, created_at desc);

create table recently_viewed (
  user_id uuid references profiles (id) on delete cascade,
  room_id uuid references live_rooms (id) on delete cascade,
  viewed_at timestamptz not null default now(),
  primary key (user_id, room_id)
);

-- ---------------------------------------------------------------- messaging
create table conversations (
  id uuid primary key default gen_random_uuid(),
  user_low uuid not null references profiles (id) on delete cascade,
  user_high uuid not null references profiles (id) on delete cascade,
  started_by uuid not null references profiles (id),
  status conversation_status not null default 'active',
  last_message_at timestamptz,
  last_message_preview text,
  low_last_read_id bigint not null default 0,
  high_last_read_id bigint not null default 0,
  created_at timestamptz not null default now(),
  unique (user_low, user_high),
  check (user_low < user_high)
);
create index conversations_low on conversations (user_low, last_message_at desc);
create index conversations_high on conversations (user_high, last_message_at desc);

create table dm_messages (
  id bigint generated always as identity primary key,
  conversation_id uuid not null references conversations (id) on delete cascade,
  sender_id uuid not null references profiles (id) on delete cascade,
  body text not null check (char_length(body) between 1 and 1000),
  client_id uuid not null,
  created_at timestamptz not null default now(),
  unique (sender_id, client_id)
);
create index dm_messages_conv on dm_messages (conversation_id, id desc);
create index dm_messages_rate on dm_messages (sender_id, created_at desc);

create table notifications (
  id bigint generated always as identity primary key,
  user_id uuid not null references profiles (id) on delete cascade,
  type text not null check (type in ('follow', 'live_start', 'gift', 'message', 'pk_invite', 'guest_invite',
                                     'moderation', 'account', 'payout', 'event')),
  actor_id uuid references profiles (id) on delete set null,
  payload jsonb not null default '{}',
  read_at timestamptz,
  pushed_at timestamptz,
  created_at timestamptz not null default now()
);
create index notifications_user on notifications (user_id, id desc);

-- ---------------------------------------------------------------- gifts, wallet, ledger
create table gifts (
  id text primary key check (id ~ '^[a-z0-9_]{2,40}$'),
  name text not null,
  name_am text not null,
  category text not null check (category in ('free', 'basic', 'premium', 'event', 'seasonal', 'creator_favorite')),
  price_coins int not null check (price_coins >= 0),
  artwork_key text not null,
  animation_key text not null,
  is_animated_fullscreen boolean not null default false,
  available boolean not null default true,
  event_id uuid,
  sort_order int not null default 0,
  updated_at timestamptz not null default now(),
  check ((category = 'free') = (price_coins = 0))
);

create table wallet_accounts (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid references profiles (id),
  kind wallet_kind not null,
  balance bigint not null default 0,
  updated_at timestamptz not null default now(),
  -- Only system issuance accounts may go negative; every user-facing balance is >= 0.
  check (balance >= 0 or kind in ('sys_coin_issuance', 'sys_promo_issuance', 'sys_bean_issuance')),
  check ((owner_id is null) = (kind::text like 'sys\_%'))
);
create unique index wallet_accounts_owner_kind on wallet_accounts (owner_id, kind) where owner_id is not null;
create unique index wallet_accounts_system_kind on wallet_accounts (kind) where owner_id is null;
insert into wallet_accounts (owner_id, kind)
select null, k::wallet_kind from unnest(array['sys_coin_issuance', 'sys_promo_issuance', 'sys_coin_revenue',
                                               'sys_bean_issuance', 'sys_bean_redeemed']) k;

create table ledger_transactions (
  id uuid primary key default gen_random_uuid(),
  kind ledger_kind not null,
  actor_id uuid,
  idempotency_key text not null,
  reference jsonb not null default '{}',
  created_at timestamptz not null default now(),
  unique (kind, actor_id, idempotency_key)
);

create table ledger_entries (
  id bigint generated always as identity primary key,
  transaction_id uuid not null references ledger_transactions (id),
  account_id uuid not null references wallet_accounts (id),
  amount bigint not null check (amount <> 0),
  unit text not null check (unit in ('coin', 'bean')),
  balance_after bigint not null,
  created_at timestamptz not null default now()
);
create index ledger_entries_account on ledger_entries (account_id, id desc);
create index ledger_entries_tx on ledger_entries (transaction_id);

create table gift_transactions (
  id uuid primary key references ledger_transactions (id),
  sender_id uuid not null references profiles (id),
  recipient_id uuid not null references profiles (id),
  room_id uuid references live_rooms (id),
  gift_id text not null references gifts (id),
  quantity int not null check (quantity between 1 and 999),
  coins_spent bigint not null,
  promo_coins_spent bigint not null default 0,
  beans_earned bigint not null,
  pk_match_id uuid,
  pk_side pk_side,
  created_at timestamptz not null default now()
);
create index gift_tx_room on gift_transactions (room_id, created_at desc);
create index gift_tx_recipient on gift_transactions (recipient_id, created_at desc);
create index gift_tx_sender on gift_transactions (sender_id, created_at desc);

-- Free gifts never touch the ledger; they are rate limited per day.
create table free_gift_sends (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references profiles (id),
  recipient_id uuid not null references profiles (id),
  room_id uuid references live_rooms (id),
  gift_id text not null references gifts (id),
  idempotency_key text not null,
  created_at timestamptz not null default now(),
  unique (sender_id, idempotency_key)
);
create index free_gift_sends_daily on free_gift_sends (sender_id, created_at desc);

create table bean_settlements (
  transaction_id uuid primary key references ledger_transactions (id),
  creator_id uuid not null references profiles (id),
  beans bigint not null check (beans > 0),
  release_at timestamptz not null,
  settled_at timestamptz
);
create index bean_settlements_due on bean_settlements (release_at) where settled_at is null;

create table coin_packages (
  id text primary key,
  coins int not null check (coins > 0),
  bonus_promo_coins int not null default 0 check (bonus_promo_coins >= 0),
  price_minor bigint not null check (price_minor > 0),
  currency text not null default 'ETB',
  play_product_id text unique,
  active boolean not null default true,
  sort_order int not null default 0
);

create table payment_orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles (id),
  provider payment_provider not null,
  package_id text not null references coin_packages (id),
  coins int not null,
  bonus_promo_coins int not null default 0,
  amount_minor bigint not null,
  currency text not null,
  status payment_status not null default 'created',
  provider_ref text,
  checkout_url text,
  failure_reason text,
  ledger_transaction_id uuid references ledger_transactions (id),
  created_at timestamptz not null default now(),
  verified_at timestamptz,
  unique (provider, provider_ref)
);
create index payment_orders_user on payment_orders (user_id, created_at desc);

create table payment_webhook_events (
  id bigint generated always as identity primary key,
  provider payment_provider not null,
  event_ref text not null,
  signature_valid boolean not null,
  payload jsonb not null,
  received_at timestamptz not null default now(),
  unique (provider, event_ref)
);

create table payout_requests (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references profiles (id),
  beans bigint not null check (beans > 0),
  gross_etb_minor bigint not null,
  fee_etb_minor bigint not null,
  net_etb_minor bigint not null,
  method jsonb not null,
  status payout_status not null default 'requested',
  hold_transaction_id uuid not null references ledger_transactions (id),
  first_approver uuid,
  second_approver uuid,
  rejection_reason text,
  provider_ref text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (first_approver is null or second_approver is null or first_approver <> second_approver)
);
create index payout_requests_status on payout_requests (status, created_at);

-- ---------------------------------------------------------------- PK battles
create table pk_matches (
  id uuid primary key default gen_random_uuid(),
  room_a uuid not null references live_rooms (id),
  room_b uuid not null references live_rooms (id),
  host_a uuid not null references profiles (id),
  host_b uuid not null references profiles (id),
  status pk_status not null default 'invited',
  duration_seconds int not null check (duration_seconds between 60 and 1800),
  invited_at timestamptz not null default now(),
  started_at timestamptz,
  ends_at timestamptz,
  finished_at timestamptz,
  score_a bigint not null default 0,
  score_b bigint not null default 0,
  winner text check (winner in ('a', 'b', 'draw')),
  check (room_a <> room_b)
);
create unique index pk_one_active_a on pk_matches (room_a) where status in ('invited', 'running');
create unique index pk_one_active_b on pk_matches (room_b) where status in ('invited', 'running');

create table pk_score_events (
  id bigint generated always as identity primary key,
  match_id uuid not null references pk_matches (id),
  side pk_side not null,
  gift_transaction_id uuid not null unique references gift_transactions (id),
  points bigint not null check (points > 0),
  created_at timestamptz not null default now()
);
create index pk_score_events_match on pk_score_events (match_id);

-- ---------------------------------------------------------------- communities, events
create table fan_clubs (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null unique references profiles (id) on delete cascade,
  name text not null check (char_length(name) between 2 and 30),
  badge_label text not null check (char_length(badge_label) between 1 and 6),
  announcement text not null default '' check (char_length(announcement) <= 500),
  benefits text not null default '',
  join_cost_coins int not null default 0 check (join_cost_coins >= 0),
  created_at timestamptz not null default now()
);

create table fan_club_members (
  club_id uuid references fan_clubs (id) on delete cascade,
  user_id uuid references profiles (id) on delete cascade,
  contribution_coins bigint not null default 0,
  loyalty_level int not null default 1,
  joined_at timestamptz not null default now(),
  primary key (club_id, user_id)
);
create index fan_club_members_contrib on fan_club_members (club_id, contribution_coins desc);

create table events (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  description text not null,
  rules text not null,
  reward_description text not null,
  metric text not null check (metric in ('gift_coins_received', 'gift_coins_sent', 'pk_wins')),
  banner_key text not null default 'event_default',
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  published boolean not null default false,
  created_by uuid,
  created_at timestamptz not null default now(),
  check (ends_at > starts_at)
);

create table event_participants (
  event_id uuid references events (id) on delete cascade,
  user_id uuid references profiles (id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (event_id, user_id)
);

-- ---------------------------------------------------------------- trust & safety, staff
create table staff_members (
  user_id uuid primary key references profiles (id),
  role staff_role not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  created_by uuid
);

create table reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references profiles (id),
  target_type report_target not null,
  target_id text not null,
  target_user_id uuid references profiles (id),
  reason report_reason not null,
  details text not null default '' check (char_length(details) <= 1000),
  evidence jsonb not null default '{}',
  status report_status not null default 'open',
  priority int not null default 3,
  assigned_to uuid references profiles (id),
  resolution text,
  created_at timestamptz not null default now(),
  resolved_at timestamptz
);
create index reports_queue on reports (status, priority, created_at);
create index reports_target_user on reports (target_user_id);

create table moderation_actions (
  id uuid primary key default gen_random_uuid(),
  moderator_id uuid references profiles (id),
  target_user_id uuid not null references profiles (id),
  kind moderation_kind not null,
  reason text not null,
  report_id uuid references reports (id),
  room_id uuid references live_rooms (id),
  expires_at timestamptz,
  created_at timestamptz not null default now()
);
create index moderation_actions_user on moderation_actions (target_user_id, created_at desc);

create table appeals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles (id),
  moderation_action_id uuid not null references moderation_actions (id),
  message text not null check (char_length(message) between 10 and 2000),
  status appeal_status not null default 'open',
  decided_by uuid references profiles (id),
  decision_note text,
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  unique (moderation_action_id)
);

create table support_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references profiles (id),
  topic text not null check (topic in ('account', 'payment', 'payout', 'safety', 'bug', 'other')),
  message text not null check (char_length(message) between 5 and 4000),
  status text not null default 'open' check (status in ('open', 'answered', 'closed')),
  staff_reply text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table ledger_adjustment_requests (
  id uuid primary key default gen_random_uuid(),
  account_id uuid not null references wallet_accounts (id),
  amount bigint not null check (amount <> 0),
  unit text not null check (unit in ('coin', 'bean')),
  reason text not null check (char_length(reason) >= 10),
  requested_by uuid not null references profiles (id),
  approved_by uuid references profiles (id),
  status text not null default 'pending' check (status in ('pending', 'applied', 'rejected')),
  ledger_transaction_id uuid references ledger_transactions (id),
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  check (approved_by is null or approved_by <> requested_by)
);

-- Append-only audit trail. UPDATE/DELETE/TRUNCATE are rejected by trigger for every role.
create table audit_logs (
  id bigint generated always as identity primary key,
  actor_id uuid,
  action text not null,
  target_type text,
  target_id text,
  details jsonb not null default '{}',
  created_at timestamptz not null default now()
);
create index audit_logs_actor on audit_logs (actor_id, id desc);
create index audit_logs_target on audit_logs (target_type, target_id);

create or replace function audit_logs_immutable() returns trigger language plpgsql as $$
begin
  raise exception 'audit_logs is append-only';
end $$;
create trigger audit_logs_no_update before update or delete on audit_logs
  for each row execute function audit_logs_immutable();
create trigger audit_logs_no_truncate before truncate on audit_logs
  for each statement execute function audit_logs_immutable();

-- Ledger rows are immutable too: corrections are new, balanced transactions.
create or replace function ledger_immutable() returns trigger language plpgsql as $$
begin
  raise exception 'ledger rows are immutable; post a correcting transaction instead';
end $$;
create trigger ledger_entries_no_update before update or delete on ledger_entries
  for each row execute function ledger_immutable();
create trigger ledger_transactions_no_update before update or delete on ledger_transactions
  for each row execute function ledger_immutable();

-- Every ledger transaction must balance to zero per unit when its database transaction commits.
-- SECURITY DEFINER: the deferred check runs at COMMIT under the caller's role, where RLS would hide system legs.
create or replace function ledger_check_balanced() returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_bad record;
begin
  select unit, sum(amount) as total into v_bad
  from ledger_entries where transaction_id = new.transaction_id
  group by unit having sum(amount) <> 0 limit 1;
  if found then
    raise exception 'ledger transaction % is unbalanced for unit % (sum %)', new.transaction_id, v_bad.unit, v_bad.total;
  end if;
  return null;
end $$;
create constraint trigger ledger_entries_balanced after insert on ledger_entries
  deferrable initially deferred for each row execute function ledger_check_balanced();
