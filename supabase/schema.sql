-- EchoNote — Supabase şeması ve güvenlik teşhisi
-- Supabase Dashboard → SQL Editor'de çalıştırılır. Bölüm 1-2 idempotenttir.

-- ---------------------------------------------------------------------------
-- 1. notes tablosu
-- ---------------------------------------------------------------------------
-- Kolonlar com.echonote.echonote.model.Note ile birebir eşleşir.
-- updated_at + device_id, ViewModel'deki last-write-wins çakışma çözümü içindir.

create table if not exists public.notes (
    id          uuid primary key,
    title       text        not null default '',
    content     text        not null default '',
    updated_at  timestamptz not null default now(),
    device_id   text        not null default ''
);

-- Liste sorgusu her zaman updated_at'e göre sıralanır.
create index if not exists notes_updated_at_idx on public.notes (updated_at desc);

-- ---------------------------------------------------------------------------
-- 2. Realtime
-- ---------------------------------------------------------------------------
-- SupabaseNotesRepository.observeNotes() selectAsFlow kullanıyor; tablo Realtime
-- yayınına ekli DEĞİLSE uygulama hiç canlı güncelleme almaz (sessizce).

alter publication supabase_realtime add table public.notes;

-- Silme olaylarının payload'ında eski satırın tamamı gelsin (selectAsFlow'un
-- silinen kaydı listeden düşürebilmesi için birincil anahtar yeterli, ama
-- REPLICA IDENTITY FULL teşhisi kolaylaştırır):
alter table public.notes replica identity full;

-- ---------------------------------------------------------------------------
-- 3. GÜVENLİK TEŞHİSİ — önce bunu çalıştır
-- ---------------------------------------------------------------------------
-- anon anahtarı istemciye gömülü ve herkese açıktır. RLS kapalıysa bu anahtarı
-- eline geçiren HERKES tüm notları okuyabilir, değiştirebilir ve silebilir.

-- 3a. RLS açık mı?
select schemaname, tablename, rowsecurity as rls_acik
from pg_tables
where schemaname = 'public' and tablename = 'notes';

-- 3b. Hangi politikalar tanımlı? (boş dönerse ve 3a true ise tablo tamamen kapalıdır)
select policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname = 'public' and tablename = 'notes';

-- Beklenen durum, Adım 4 (Auth) tamamlanana kadar:
--   rls_acik = false  → tablo korumasız. Tek kullanıcılı kişisel kullanımda
--                       risk "anahtarı sızdırmamak"la sınırlı; APK'yı kimseyle
--                       paylaşma ve anon anahtarını herkese açık repoya koyma.
--   rls_acik = true   → politika yoksa uygulama HİÇ veri okuyamaz (boş liste).

-- ---------------------------------------------------------------------------
-- 4. ADIM 4 TASLAĞI — Auth ile gerçek kilitleme (henüz UYGULANMADI)
-- ---------------------------------------------------------------------------
-- Aşağıdaki blok, uygulamaya Supabase Auth eklendikten SONRA çalıştırılmalı.
-- Şimdi çalıştırılırsa uygulama veri okuyamaz hale gelir (user_id boş kalır).
--
-- alter table public.notes add column if not exists user_id uuid
--     references auth.users (id) on delete cascade;
--
-- -- Mevcut satırları kendi hesabına devret (UUID'yi Dashboard → Authentication'dan al):
-- update public.notes set user_id = '<SENIN-USER-UUID>' where user_id is null;
-- alter table public.notes alter column user_id set not null;
--
-- alter table public.notes enable row level security;
--
-- create policy "kendi notlarini okur" on public.notes
--     for select using (auth.uid() = user_id);
-- create policy "kendi notlarini ekler" on public.notes
--     for insert with check (auth.uid() = user_id);
-- create policy "kendi notlarini guncekler" on public.notes
--     for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
-- create policy "kendi notlarini siler" on public.notes
--     for delete using (auth.uid() = user_id);
--
-- Ayrıca değerlendirilecek: updated_at'i istemci saati yerine sunucu saatinden
-- damgalamak (cihaz saatleri kayarsa last-write-wins yanlış tarafı seçiyor):
--
-- create or replace function public.notes_touch_updated_at()
-- returns trigger language plpgsql as $$
-- begin
--     new.updated_at := now();
--     return new;
-- end $$;
--
-- create trigger notes_touch_updated_at before insert or update on public.notes
--     for each row execute function public.notes_touch_updated_at();
