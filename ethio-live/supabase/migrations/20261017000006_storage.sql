-- Ethio Live: media storage. Profile pictures and stream covers are public-read; each user may write only
-- inside their own folder (<user id>/...), only images, at most 2 MB (the app compresses to ~200 KB first).
do $$
begin
  if not exists (select 1 from pg_namespace where nspname = 'storage') then
    raise notice 'storage schema not present (plain PostgreSQL test run); skipping storage setup';
    return;
  end if;
  insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types) values
    ('avatars', 'avatars', true, 2097152, array['image/jpeg', 'image/png', 'image/webp']),
    ('covers', 'covers', true, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
  on conflict (id) do update set public = excluded.public, file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

  execute $p$ create policy media_read on storage.objects for select using (bucket_id in ('avatars', 'covers')) $p$;
  execute $p$ create policy media_insert_own on storage.objects for insert to authenticated
    with check (bucket_id in ('avatars', 'covers') and (storage.foldername(name))[1] = auth.uid()::text) $p$;
  execute $p$ create policy media_update_own on storage.objects for update to authenticated
    using (bucket_id in ('avatars', 'covers') and (storage.foldername(name))[1] = auth.uid()::text) $p$;
  execute $p$ create policy media_delete_own on storage.objects for delete to authenticated
    using (bucket_id in ('avatars', 'covers') and (storage.foldername(name))[1] = auth.uid()::text) $p$;
end $$;
