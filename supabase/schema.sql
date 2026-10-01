-- EchoNote — Supabase şeması ve Row Level Security geçişi
-- Supabase Dashboard → SQL Editor'de çalıştırılır.
--
-- ╔══════════════════════════════════════════════════════════════════════════╗
-- ║  AŞAMALARI SIRAYLA ÇALIŞTIR. Sıra, hiçbir anda uygulamanın kırılmaması   ║
-- ║  için seçildi. Aşama 1 tamamlanmadan Aşama 2'ye geçme.                   ║
-- ╚══════════════════════════════════════════════════════════════════════════╝


-- ---------------------------------------------------------------------------
-- AŞAMA 0 — Yedek al (atlamayın)
-- ---------------------------------------------------------------------------
-- Dashboard → Table Editor → notes → sağ üst → Export as CSV.
-- Bu geçiş geri alınabilir, ama elde bir kopya olmadan başlanmaz.

-- Mevcut tablo (referans; zaten var olmalı):
create table if not exists public.notes (
    id          uuid primary key,
    title       text        not null default '',
    content     text        not null default '',
    updated_at  timestamptz not null default now(),
    device_id   text        not null default ''
);

create index if not exists notes_updated_at_idx on public.notes (updated_at desc);

-- Realtime: SupabaseNotesSource.observeNotes() selectAsFlow kullanıyor. Tablo yayına
-- ekli DEĞİLSE uygulama hiç canlı güncelleme almaz (ve bunu sessizce yapar).
alter publication supabase_realtime add table public.notes;
alter table public.notes replica identity full;


-- ---------------------------------------------------------------------------
-- AŞAMA 1 — Önce uygulama: kayıt ol + giriş yap (BURADA SQL ÇALIŞTIRMA)
-- ---------------------------------------------------------------------------
-- Yeni sürümü kur, uygulamada "Kayıt ol" ile hesabını oluştur ve giriş yap.
-- RLS hâlâ kapalı, yani her şey eskisi gibi çalışmalı. Doğrula:
--   • Notlar listeleniyor mu
--   • Yeni not senkron oluyor mu (gösterge "Senkron")
-- Bu aşama, giriş akışını VERİYE DOKUNMADAN test etmek içindir.

-- Kaç hesap var? Devam etmeden önce 1 dönmeli (yalnızca senin hesabın):
select count(*) as hesap_sayisi from auth.users;
select id, email, created_at from auth.users order by created_at;


-- ---------------------------------------------------------------------------
-- AŞAMA 2 — user_id kolonu + mevcut notları devret
-- ---------------------------------------------------------------------------
-- Önce nullable ekle, sonra backfill: mevcut notlar sahipsiz kalmamalı.

alter table public.notes
    add column if not exists user_id uuid references auth.users (id) on delete cascade;

-- Tüm mevcut notları ilk (= senin) hesaba devret. UUID elle yapıştırmaya gerek yok;
-- yukarıdaki hesap_sayisi 1 ise bu güvenlidir. Birden fazla hesap varsa BURAYA
-- kendi UUID'ni yazmalısın, yoksa notlar yanlış hesaba gider.
update public.notes
set user_id = (select id from auth.users order by created_at limit 1)
where user_id is null;

-- Devretme tamam mı? Sıfır dönmeli:
select count(*) as sahipsiz_not from public.notes where user_id is null;


-- ---------------------------------------------------------------------------
-- AŞAMA 3 — Zorunlu kıl + yeni satırlara otomatik sahip ata
-- ---------------------------------------------------------------------------
-- DEFAULT auth.uid() kritik: Kotlin tarafı user_id'yi hiç göndermiyor (Note modelinde
-- böyle bir alan yok). Bu default olmadan her insert, Aşama 4'teki "with check"
-- kuralına takılır ve senkron tamamen durur.

alter table public.notes alter column user_id set default auth.uid();
alter table public.notes alter column user_id set not null;

create index if not exists notes_user_id_idx on public.notes (user_id);


-- ---------------------------------------------------------------------------
-- AŞAMA 4 — RLS'i aç (asıl kilit)
-- ---------------------------------------------------------------------------
alter table public.notes enable row level security;

-- ÖNCE ESKİ GENİŞ POLİTİKALARI KALDIR.
-- Postgres'te politikalar varsayılan olarak permissive'dir ve VEYA ile birleşir:
-- "herkese her şey" diyen tek bir politika, aşağıdaki 4 kuralı tamamen anlamsız kılar.
-- (Bu projede Supabase kurulumundan kalma "anon full access" politikası vardı ve
-- RLS açıkken bile tüm notlar kimliksiz okunabiliyordu.)
drop policy if exists "anon full access" on public.notes;

-- Kalan geniş politika var mı? Aşağıdakiler dışında bir satır dönerse onu da düşür:
--   select policyname, roles, cmd from pg_policies where tablename = 'notes';

drop policy if exists "kendi notlarini okur"     on public.notes;
drop policy if exists "kendi notlarini ekler"    on public.notes;
drop policy if exists "kendi notlarini gunceller" on public.notes;
drop policy if exists "kendi notlarini siler"    on public.notes;

create policy "kendi notlarini okur" on public.notes
    for select using (auth.uid() = user_id);

create policy "kendi notlarini ekler" on public.notes
    for insert with check (auth.uid() = user_id);

create policy "kendi notlarini gunceller" on public.notes
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "kendi notlarini siler" on public.notes
    for delete using (auth.uid() = user_id);


-- ---------------------------------------------------------------------------
-- AŞAMA 5 — Doğrula
-- ---------------------------------------------------------------------------
-- rls_acik = true olmalı:
select tablename, rowsecurity as rls_acik
from pg_tables where schemaname = 'public' and tablename = 'notes';

-- 4 politika listelenmeli:
select policyname, cmd from pg_policies
where schemaname = 'public' and tablename = 'notes' order by policyname;

-- Uygulamada: notlar hâlâ görünüyor, yeni not senkron oluyor, gösterge "Senkron".
-- İkinci bir test hesabıyla giriş yapıldığında BOŞ liste görülmeli.


-- ---------------------------------------------------------------------------
-- GERİ ALMA (bir şey ters giderse)
-- ---------------------------------------------------------------------------
-- RLS'i kapatmak tüm erişimi eski hâline döndürür; veri kaybı olmaz:
--   alter table public.notes disable row level security;
--
-- Not: yerel SQLite kopyası cihazlarda durmaya devam eder, yani bu geçiş sırasında
-- ekranın boşalsa bile notların cihazında duruyor olur.


-- ---------------------------------------------------------------------------
-- SONRAKİ ADIM (henüz uygulanmadı)
-- ---------------------------------------------------------------------------
-- updated_at istemci saatinden geliyor; iki cihazın saati kayarsa last-write-wins
-- yanlış tarafı seçer. Sunucu damgasına geçmek için:
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
--
-- DİKKAT: bu, istemcinin gönderdiği updated_at'i ezer; LWW mantığının buna göre
-- gözden geçirilmesi gerekir. Ayrı bir iş olarak ele alınmalı.
