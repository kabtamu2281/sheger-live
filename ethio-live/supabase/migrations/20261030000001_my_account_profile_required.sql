-- my_account() returned SQL null for a signed-in user who has not finished the profile step, so the app
-- could not decode it and never showed the profile screen. Raise profile_required, which the app expects.
create or replace function my_account() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare v_uid uuid := me(); v jsonb;
begin
  select jsonb_build_object(
      'profile', to_jsonb(p),
      'private', jsonb_build_object('date_of_birth', up.date_of_birth, 'kyc_status', up.kyc_status,
                                    'terms_version', up.terms_version, 'is_adult', is_adult(v_uid)),
      'settings', to_jsonb(s),
      'staff_role', (select role from staff_members where user_id = v_uid and active))
    into v
    from profiles p join user_private up on up.user_id = p.id left join user_settings s on s.user_id = p.id
    where p.id = v_uid;
  if v is null then raise exception 'profile_required'; end if;
  return v;
end $$;
