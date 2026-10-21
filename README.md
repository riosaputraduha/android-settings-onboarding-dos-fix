# Remediasi Kerentanan F-03: Onboarding Task Manager Heap Exhaustion DoS

Repositori ini berisi dokumentasi analisis, pembuktian konsep (*Proof-of-Concept* / PoC), dan rancangan perbaikan (*patch*) untuk kerentanan **F-03** pada komponen `android.onboarding` yang berjalan di dalam proses `com.android.settings` (UID 1000).

## Ringkasan Kerentanan

| Parameter | Detail |
|---|---|
| **ID Temuan** | F-03 |
| **Tingkat Keparahan** | MEDIUM (Local Denial of Service) |
| **Status Akhir** | **RESOLVED / MITIGATED** |
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
- `docs/` : Laporan teknis temuan F-03, Laporan Akhir Remediasi (`FINAL_REPORT.md`), serta Panduan Build & Verifikasi Emulator (`GUIDE_BUILD_AND_VERIFY.md`).
- `pocs/` : Kode aplikasi PoC (`app/`), skrip pengujian, dan log bukti uji memori di emulator (`evidence/live-emulator-test.log`).
- `patches/` : Rekomendasi perubahan kode dan berkas patch untuk mitigasi.
- `tests/` : Test suite unit/integrasi dan prosedur verifikasi kestabilan memori.

## Rencana Mitigasi
- [x] Analisis akar masalah & reproduksi (Fase 1)
- [x] Implementasi fail-fast caller signature check via `Binder.getCallingUid()` (Fase 2)
- [x] Batasi panjang string parameter `COMPONENT` dan `NODE_ID` (Fase 2)
- [x] Terapkan LRU cache berkapasitas tetap pada `OnboardingTaskStateManager` (Fase 2)
- [x] Berikan batas waktu atas (*clamp*) pada `delayFor` dan kuota task per UID (Fase 2)
- [x] Verifikasi uji regresi dan kestabilan heap (Fase 3)
- [x] Penyusunan dokumentasi dan laporan akhir remediasi (Fase 4)
- [x] Validasi dinamis & logging memori pada target emulator runtime Android 17 (Fase 5)

## Hasil Remediasi & Panduan Teknis
- 📄 Laporan akhir mitigasi & metrik pengujian: 👉 [docs/FINAL_REPORT.md](docs/FINAL_REPORT.md)
- 🛠️ Panduan instalasi tools, kompilasi patch AOSP & uji emulator: 👉 [docs/GUIDE_BUILD_AND_VERIFY.md](docs/GUIDE_BUILD_AND_VERIFY.md)
- 📊 Bukti log uji runtime emulator nyata: 👉 [pocs/POC-A17-F03-MEMDOS/evidence/live-emulator-test.log](pocs/POC-A17-F03-MEMDOS/evidence/live-emulator-test.log)