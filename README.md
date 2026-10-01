# EchoNote

Yapay zekâ destekli, gerçek zamanlı senkronize not uygulaması.
Kotlin Multiplatform + Compose Multiplatform; hedefler: **Android** ve **Desktop (JVM)**.

- Notlar Supabase'de saklanır ve Realtime ile cihazlar arasında anlık senkronlanır.
- Gemini ile not genişletme / özetleme, her değişiklik geri alınabilir.
- Markdown yazarken anlık vurgulanır.

## Kurulum

### 1. API anahtarları

`Secrets.kt` git'e **girmez** (`.gitignore`'da). Şablondan oluştur:

```bash
cp shared/src/commonMain/kotlin/com/echonote/echonote/Secrets.kt.example \
   shared/src/commonMain/kotlin/com/echonote/echonote/Secrets.kt
```

Sonra değerleri doldur:

| Alan | Nereden |
|---|---|
| `GEMINI_API_KEY` | https://aistudio.google.com/apikey |
| `SUPABASE_URL` | Supabase Dashboard → Settings → API → Project URL |
| `SUPABASE_ANON_KEY` | Supabase Dashboard → Settings → API → anon/public |

Alanları **boş bırakırsan** uygulama çevrimdışı moda düşer: sahte AI + bellek içi not
deposu ile çalışır. Bu modda notlar her kapanışta sıfırlanır.

### 2. Supabase

[`supabase/schema.sql`](supabase/schema.sql) dosyasını Dashboard → SQL Editor'de çalıştır. Bu:

1. `notes` tablosunu oluşturur,
2. tabloyu **Realtime yayınına ekler** — bu adım atlanırsa uygulama hiç canlı güncelleme
   almaz ve bunu sessizce yapar,
3. **RLS teşhis sorgularını** içerir.

> **Güvenlik:** anon anahtarı istemciye gömülüdür ve herkese açıktır. RLS kapalıysa bu
> anahtarı eline geçiren herkes tüm notları okuyup silebilir. `schema.sql`'in 3. bölümü
> mevcut durumu raporlar; gerçek kilitleme Supabase Auth eklendiğinde yapılacak
> (aynı dosyanın 4. bölümünde taslak hazır).

## Çalıştırma

IDE'nin run widget'ındaki hazır konfigürasyonları kullanabilir ya da:

- **Android:** `./gradlew :androidApp:assembleDebug`
- **Desktop:**
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Normal: `./gradlew :desktopApp:run`

## Testler

```bash
./gradlew :shared:jvmTest :shared:testAndroidHostTest
```

`NotesViewModelTest`, senkron katmanının kritik davranışlarını kilitler: Realtime
akışının hatadan sonra yeniden abone olması, kapanışta bekleyen yazmaların boşaltılması,
ağda askıda yazma sırasında eklenen harflerin kaybolmaması, iyimser silmenin bayat uzak
yankıyla geri dirilmemesi.

## Mimari

```
shared/src/commonMain/kotlin/com/echonote/echonote/
├─ App.kt                  responsive kabuk (800dp eşiği: split-pane / tam ekran yığın)
├─ NotesViewModel.kt        tek state holder: iyimser güncelleme, debounce'lu kayıt,
│                          last-write-wins birleştirme, Realtime retry
├─ NoteListPane.kt          liste + senkron göstergesi
├─ NoteEditorPane.kt        başlık, AI eylemleri, editör
├─ Banners.kt               SyncStatusChip + ErrorBanner
├─ MarkdownHighlighter.kt   VisualTransformation ile inline Markdown vurgu
├─ Theme.kt                 dark glassmorphism sistemi
├─ SaveCoordinator.kt       kapanışta bekleyen yazmaları boşaltma kancası
├─ data/                    NotesRepository + InMemory & Supabase gerçeklemeleri
├─ ai/                      AiService + Mock & Gemini gerçeklemeleri
└─ model/                   Note, zaman damgası yardımcıları
```

`shared/src/{androidMain,jvmMain}` yalnızca platform `expect/actual`'larını içerir;
UI'ın tamamı ortak koddadır.

---

[Kotlin Multiplatform hakkında daha fazla](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
