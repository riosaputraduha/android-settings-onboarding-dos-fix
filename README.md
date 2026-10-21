# Remediasi Kerentanan F-03: Onboarding Task Manager Heap Exhaustion DoS

Repositori ini berisi dokumentasi analisis, pembuktian konsep (*Proof-of-Concept* / PoC), dan rancangan perbaikan (*patch*) untuk kerentanan **F-03** pada komponen `android.onboarding` yang berjalan di dalam proses `com.android.settings` (UID 1000).

## Ringkasan Kerentanan

| Parameter | Detail |
|---|---|
| **ID Temuan** | F-03 |
| **Tingkat Keparahan** | MEDIUM (Local Denial of Service) |
| **Komponen Terdampak** | `android.onboarding` task manager, host `com.android.settings` |
| **Target Build** | Android 17 userdebug (`SettingsGoogle` system_ext build) |
| **Hak Akses Pemanggil** | Zero-permission (UID 10xxx) |
| **Dampak** | Exhaustion memori Java Heap hingga memicu `OutOfMemoryError` dan crash pada proses sistem UID 1000 |

## Akar Masalah (*Root Cause*)
1. Alokasi entri state pada `OnboardingTaskStateManager` dilakukan sebelum token diverifikasi.
2. Identitas pemanggil dipercaya hanya berdasarkan deklarasi nama paket tanpa verifikasi tanda tangan digital/sertifikat UID pemanggil.
3. Struktur penyimpanan state tidak memiliki batas (*unbounded*) dan tidak ada mekanisme kedaluwarsa/eviction.
4. Parameter `delayFor` pada `DiagnosisProviderTask` tidak memiliki batas atas (*unbounded clamp*), menahan coroutine/thread di memori tanpa batas waktu.

## Struktur Repositori
- `docs/` : Laporan teknis lengkap temuan F-03.
- `pocs/` : Kode aplikasi PoC reproduksi eksploitasi saturasi heap dan unbounded delay.
- `patches/` : Rekomendasi perubahan kode dan berkas patch untuk mitigasi.

## Rencana Mitigasi
- [x] Analisis akar masalah & reproduksi (Fase 1)
- [x] Implementasi fail-fast caller signature check via `Binder.getCallingUid()` (Fase 2)
- [x] Batasi panjang string parameter `COMPONENT` dan `NODE_ID` (Fase 2)
- [x] Terapkan LRU cache berkapasitas tetap pada `OnboardingTaskStateManager` (Fase 2)
- [x] Berikan batas waktu atas (*clamp*) pada `delayFor` dan kuota task per UID (Fase 2)
- [ ] Verifikasi uji regresi dan kestabilan heap (Fase 3)