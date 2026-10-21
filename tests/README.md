# Prosedur Pengujian Validasi & Regresi (Fase 3)

Dokumen ini mendefinisikan strategi dan matriks pengujian untuk memvalidasi efektivitas patch keamanan **F-03** serta memastikan tidak ada regresi fungsional pada komponen `android.onboarding` di dalam `com.android.settings`.

---

## 1. Matriks Skenario Pengujian

| ID Uji | Kategori | Skenario Pengujian | Input / Kondisi | Ekspektasi Hasil |
|---|---|---|---|---|
| **SEC-01** | *Negative* | Panggilan dari aplikasi tanpa izin (*zero-permission app*) | UID `10xxx`, deklarasi paket palsu / unverified | Ditolak seketika dengan `SecurityException`. Tidak ada state yang dialokasikan. |
| **SEC-02** | *Negative* | Panggilan dengan payload identifier melebihi batas | `COMPONENT` atau `NODE_ID` > 256 karakter | Ditolak dengan `IllegalArgumentException`. |
| **SEC-03** | *Negative* | Panggilan dengan token tidak terdaftar | Token acak / tidak ada di `nodeRegistry` | Ditolak dengan `IllegalArgumentException`. State manager **tidak** mencatat kegagalan. |
| **SEC-04** | *Negative* | Upaya penundaan ekstrem (*unbounded delay*) | `delayFor = PT24H` | Durasi otomatis dipangkas (*clamped*) menjadi maksimal 60 detik (60.000 ms). |
| **SEC-05** | *Negative* | Upaya saturasi konkurensi tugas tertunda | Memanggil > 5 tugas tertunda serentak dari UID yang sama | Panggilan ke-6 ditolak dengan `IllegalStateException`. |
| **MEM-01** | *Negative* | Uji ketahanan memori terhadap 1.000 panggilan berturut-turut | 1.000 panggilan dengan token invalid & string 200 KB | PSS dan Java Heap tetap stabil (< 35 MB, delta memori ~0 MB). Tidak terjadi crash OOM. |
| **FUNC-01**| *Positive* | Panggilan onboarding resmi dari SetupWizard | UID sah / ditandatangani platform, token valid | Tugas berhasil dibuat, didaftarkan, dan dijalankan dengan normal. |
| **BOUND-01**| *Boundary* | Panggilan dengan panjang identifier tepat pada batas | Panjang `COMPONENT` = 256 karakter | Diterima dan diproses jika token sah. |
| **BOUND-02**| *Boundary* | Pengisian kapasitas cache state manager | Memasukkan 150 entri tugas sah | Kapasitas cache tertahan maksimal 100 entri; 50 entri tertua dievakuasi (*LRU evicted*). |

---

## 2. Struktur Berkas Pengujian

* **`tests/OnboardingTaskManagerSecurityTest.kt`**: Test suite berbasis JUnit dan Mockito yang mengotomatisasi pengujian skenario SEC-01 s/d SEC-05, FUNC-01, dan BOUND-01/02.
* **`pocs/POC-A17-F03-MEMDOS/evidence/post-patch-verification.log`**: Log verifikasi metrik memori dan Binder call trace setelah patch diterapkan.

---

## 3. Instruksi Menjalankan Uji Otomatis

### A. Menjalankan Unit Test AOSP
```bash
# Jalankan test suite keamanan via Atest
atest SettingsRoboTests:OnboardingTaskManagerSecurityTest
```

### B. Menjalankan Uji Ketahanan Heap Dinamis (ADB)
```bash
# Pantau penggunaan memori proses com.android.settings
adb shell dumpsys meminfo com.android.settings | grep -E "Java Heap|Native Heap|TOTAL PSS"

# Jalankan ulang skenario pengujian eksploitasi
# Pastikan tidak ada exception fatal dan alokasi memori tidak bertumbuh liar
```
