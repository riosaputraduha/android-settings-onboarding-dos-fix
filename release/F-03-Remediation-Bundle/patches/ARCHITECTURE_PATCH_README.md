# Dokumentasi Rancangan Mitigasi & Patch (Fase 2)

Dokumen ini menjelaskan rancangan arsitektur perbaikan dan implementasi kode untuk menutup kerentanan **F-03** (*Onboarding Task Manager Heap Exhaustion Denial-of-Service*) pada komponen `android.onboarding` di dalam host proses `com.android.settings` (UID 1000).

---

## 1. Analisis Celah & Prinsip Perbaikan

| Vektor Serangan | Kondisi Rentan (Sebelum Patch) | Perilaku Aman (Setelah Patch) |
|---|---|---|
| **Verifikasi Pemanggil** | Mengandalkan deklarasi nama paket dari pemanggil; paket allowlist tidak diverifikasi tanda tangannya jika belum terpasang. | Validasi ketat menggunakan UID pemanggil via `Binder.getCallingUid()` dan verifikasi sertifikat platform/tanda tangan digital sebelum alokasi objek apa pun. |
| **Alokasi State Token** | State dialokasikan dan status `Failure("Task cannot be found.")` disimpan untuk token yang tidak dikenali. | *Fail-fast*: Token diverifikasi ke daftar *registered nodes* terlebih dahulu. Jika tidak valid, langsung lemparkan `IllegalArgumentException` tanpa menyentuh *state manager*. |
| **Ukuran Identifier** | String `COMPONENT` dan `NODE_ID` diterima tanpa batas ukuran (dapat dieksploitasi dengan payload ~200 KB per panggilan). | Diberikan batasan panjang maksimum (*input clamping*), maksimal 256 karakter per atribut. |
| **Parameter Penundaan** | Parameter `delayFor` pada `DiagnosisProviderTask` diterima tanpa batas atas (misal `PT24H`). | Durasi `delayFor` dibatasi (*clamped*) maksimal 60 detik, serta pembatasan kuota konkurensi tugas tertunda per UID (maksimal 5 tugas). |
| **Kapasitas Penyimpanan State** | Map di `OnboardingTaskStateManager` bersifat *unbounded* dan hanya dibersihkan saat `close()`. | Menggunakan struktur *Bounded LRU Cache* (maksimal 100 entri) dengan batas waktu kedaluwarsa (*TTL* 10 menit) untuk mencegah akumulasi memori liar. |

---

## 2. Rincian Implementasi Kode

### A. Autentikasi Dini & Validasi Input (`OnboardingTaskManagerService.kt`)
* Mengambil UID pemanggil asli melalui `Binder.getCallingUid()`.
* Memastikan UID pemanggil cocok dengan paket terdaftar yang memiliki izin platform atau sertifikat tanda tangan yang sah (`PackageManager.checkSignatures()`).
* Melakukan validasi panjang parameter `COMPONENT` dan `NODE_ID` (maksimal `MAX_IDENTIFIER_LENGTH = 256`).
* Memeriksa apakah token terdaftar di node konfigurasi sebelum memanggil `createAndInitTask()`.

### B. Pengendalian Memori State (`OnboardingTaskStateManager.kt`)
* Mengganti koleksi map tak terbatas (`ConcurrentHashMap`) dengan struktur `LruCache` berkapasitas tetap (maksimum 100 entri).
* Menerapkan timestamp pada setiap entri state untuk pembersihan otomatis saat masa hidup tugas telah melebihi ambang batas (*TTL eviction*).
* Menghapus pencatatan state untuk tugas yang berstatus penolakan seketika (*transient failures*).

### C. Pembatasan Eksekusi Diagnostik (`DiagnosisProviderTask.kt`)
* Membatasi nilai penundaan:
  ```kotlin
  val clampedDelayMillis = delayFor.toMillis().coerceIn(0L, MAX_DELAY_MILLIS) // MAX_DELAY_MILLIS = 60_000L
  ```
* Melacak jumlah tugas tertunda per UID menggunakan *atomic counter*. Jika kuota terlampaui, pemanggilan ditolak dengan `IllegalStateException("Concurrent task limit exceeded for UID")`.

---

## 3. Berkas Patch

Patch AOSP lengkap tersimpan pada berkas:
👉 **`patches/F-03-mitigation.patch`**

Format patch ini dapat diterapkan ke pohon kode sumber AOSP / SettingsGoogle menggunakan perintah standar:
```bash
git apply patches/F-03-mitigation.patch
```
