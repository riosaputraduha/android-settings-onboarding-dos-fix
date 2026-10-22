# Panduan Lengkap & Terverifikasi: Setup Tools, Kompilasi Patch AOSP, dan Verifikasi Memori di Emulator

Dokumen ini adalah panduan teknis operasional yang **telah teruji dan terbukti berhasil (*verified & works*)** di lingkungan Windows 11 host dan WSL 2 (Kali/Ubuntu). Panduan ini memandu seluruh proses dari penyiapan emulator, pembangunan aplikasi pengujian PoC (*Proof-of-Concept*), pengujian kestabilan memori real-time via ADB, hingga kompilasi dan validasi patch AOSP.

---

## 1. Arsitektur Lingkungan & Spesifikasi Sistem

* **Host OS:** Windows 11 Pro 64-bit
* **Android Emulator:** AVD `Pixel_7` (Android 17 / API level 35/VanillaIceCream, userdebug runtime), terhubung sebagai serial `emulator-5554`
* **Android SDK:** Terpasang di `$env:LOCALAPPDATA\Android\Sdk`
  * Platform-Tools (`adb.exe` version 35.0.2+)
  * Emulator Engine (`emulator.exe` version 35.2.10+)
  * Android API Platform Jar: `platforms\android-35\android.jar`
* **IDE & JDK:** Android Studio Ladybug / Koala dengan JBR 17/21
* **Linux Subsystem (WSL 2):** Kali Linux / Ubuntu 22.04 LTS
  * Git 2.53.0+
  * Kotlin Compiler (`kotlinc` 2.4.x via SDKMAN / apt)
  * Shallow clone repository `platform/packages/apps/Settings` di `~/aosp-settings/Settings`

---

## 2. Bagian 1: Menjalankan Emulator Android 17 (Host Windows 11)

### 2.1. Menemukan Nama AVD yang Tersedia
Buka PowerShell di Windows 11 dan periksa daftar AVD yang terpasang:
```powershell
cd $env:LOCALAPPDATA\Android\Sdk\emulator
.\emulator.exe -list-avds
```
*Contoh output yang valid:* `Pixel_7`

### 2.2. Menjalankan Emulator dengan Partisi System Writable
Jalankan emulator menggunakan flag `-writable-system` dan `-no-snapshot-load` agar image sistem dapat dimodifikasi jika diperlukan:
```powershell
cd $env:LOCALAPPDATA\Android\Sdk\emulator
.\emulator.exe -avd Pixel_7 -writable-system -no-snapshot-load
```
> **Catatan Operasional:**
> * Emulator secara default akan otomatis mengikat port 5554 dan terdeteksi di ADB sebagai `emulator-5554`.
> * **Jangan** menjalankan `adb connect localhost:5566` jika emulator telah otomatis aktif di port 5554, karena koneksi akan ditolak (*connection refused*).

### 2.3. Verifikasi Koneksi ADB
Buka jendela PowerShell baru:
```powershell
adb devices
```
**Output yang diharapkan:**
```text
List of devices attached
emulator-5554   device
```

---

## 3. Bagian 2: Membangun Aplikasi Pengujian PoC (`com.untrusted.poc`)

Aplikasi PoC digunakan untuk mensimulasikan penyerang zero-permission (UID 10xxx) yang membanjiri service `com.android.settings` (UID 1000).

### 3.1. Struktur & Konfigurasi Gradle yang Berhasil (*Working Config*)

Proyek PoC berada di `D:\task\pocs\POC-A17-F03-MEMDOS`.

* **`build.gradle` (Project-level):**
```groovy
plugins {
    id 'com.android.application' version '8.5.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.24' apply false
}
```

* **`app/build.gradle` (Module-level):**
```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.untrusted.poc'
    compileSdk 35

    defaultConfig {
        applicationId "com.untrusted.poc"
        minSdk 30
        targetSdk 35
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
}
```

* **`app/src/main/res/values/colors.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
```

* **`app/src/main/res/values/themes.xml` (AppCompat murni, bebas error linking AAPT):**
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.POCApp" parent="Theme.AppCompat.Light.NoActionBar">
        <item name="colorPrimary">@color/purple_500</item>
        <item name="colorPrimaryDark">@color/purple_700</item>
        <item name="colorAccent">@color/teal_200</item>
        <item name="android:windowBackground">@color/white</item>
    </style>
</resources>
```

* **`app/src/main/AndroidManifest.xml` (Gunakan ikon sistem default):**
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:icon="@android:drawable/ic_menu_camera"
        android:label="POC-A17-F03"
        android:theme="@style/Theme.POCApp">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### 3.2. Kompilasi APK di Android Studio
1. Buka folder `D:\task\pocs\POC-A17-F03-MEMDOS` di Android Studio.
2. Tunggu hingga **Gradle Sync** selesai.
3. Klik menu atas: **Build** $\rightarrow$ **Build Bundle(s) / APK(s)** $\rightarrow$ **Build APK(s)**.
4. Berkas APK biner siap digunakan pada:
   `D:\task\pocs\POC-A17-F03-MEMDOS\app\build\outputs\apk\debug\app-debug.apk`

---

## 4. Bagian 3: Deployment & Verifikasi Kestabilan Memori Runtime via ADB

### 4.1. Instalasi dan Peluncuran Aplikasi PoC
Jalankan di PowerShell:
```powershell
# 1. Pasang APK ke emulator target
adb -s emulator-5554 install -r D:\task\pocs\POC-A17-F03-MEMDOS\app\build\outputs\apk\debug\app-debug.apk

# 2. Dapatkan PID com.android.settings
$settingsPid = (adb -s emulator-5554 shell pidof com.android.settings).Trim()
Write-Host "PID com.android.settings aktif: $settingsPid"

# 3. Luncurkan UI aplikasi PoC
adb -s emulator-5554 shell am start -n com.untrusted.poc/.MainActivity
```

### 4.2. Polling Kestabilan Memori Real-Time
Jalankan loop pemantau memori di PowerShell:
```powershell
while ($true) {
    $time = Get-Date -Format "HH:mm:ss"
    $mem = (adb -s emulator-5554 shell dumpsys meminfo com.android.settings | Select-String -Pattern "Dalvik Heap|TOTAL PSS") -join " | "
    Write-Host "[$time] $mem"
    Start-Sleep -Seconds 2
}
```

### 4.3. Hasil Pengujian Nyata yang Terbukti (*Verified Real Log*)
Ekstrak log bukti resmi dari `pocs/POC-A17-F03-MEMDOS/evidence/live-emulator-test.log`:
```text
[01:32:16] Dalvik Heap 10539  5528  4896  164 17256  8390  6293  2097 | TOTAL PSS: 42064 | TOTAL RSS: 164140
[01:32:20] Dalvik Heap 10647  9104  1428   52 17364  8390  6293  2097 | TOTAL PSS: 42060 | TOTAL RSS: 164272
[01:32:30] Dalvik Heap 10651  9108  1428   52 17368  8390  6293  2097 | TOTAL PSS: 42063 | TOTAL RSS: 164276
[01:32:46] Dalvik Heap 10650  9104  1428   52 17364  8390  6293  2097 | TOTAL PSS: 42088 | TOTAL RSS: 164300
[01:32:55] Dalvik Heap 10650  9104  1428   52 17364  8390  6293  2097 | TOTAL PSS: 42089 | TOTAL RSS: 164300
```
* **Dalvik Heap Alloc:** Stabil di **10.5 MB - 10.6 MB** (jauh di bawah batas 192 MB).
* **Total PSS:** Sangat stabil pada **~42.0 MB** dengan deviasi fluktuasi < 50 KB.
* **Integritas PID:** PID tetap 1448 (**0 crash, tidak ada OOM abort**).

---

## 5. Bagian 4: Integrasi & Kompilasi Patch AOSP di WSL 2 (Terbukti Berhasil)

Metode ini memverifikasi bahwa berkas mitigasi F-03 bersih secara sintaksis dan terkompilasi sempurna ke bytecode Android tanpa memerlukan full clone AOSP 300 GB.

### 5.1. Setup Sumber Kode AOSP Settings di WSL 2
Buka terminal WSL 2 (Kali/Ubuntu):
```bash
# Buat direktori dan lakukan shallow-clone AOSP Settings
mkdir -p ~/aosp-settings && cd ~/aosp-settings
git clone --depth 1 https://android.googlesource.com/platform/packages/apps/Settings
cd Settings
```

### 5.2. Penempatan Berkas Sumber Mitigasi
Buat struktur direktori onboarding:
```bash
mkdir -p src/com/android/settings/onboarding/tasks
```

Tempatkan ketiga berkas mitigasi berikut:
1. `src/com/android/settings/onboarding/OnboardingTaskManagerService.kt`
   * Menerapkan validasi identitas pemanggil via `Binder.getCallingUid()` dan verifikasi signature `PackageManager.checkSignatures()`.
   * Membatasi panjang string `MAX_IDENTIFIER_LENGTH = 256`.
2. `src/com/android/settings/onboarding/OnboardingTaskStateManager.kt`
   * Menggunakan `android.util.LruCache` berkapasitas 100 entri dan pembersihan otomatis (TTL 10 menit).
3. `src/com/android/settings/onboarding/tasks/DiagnosisProviderTask.kt`
   * Membatasi delay maksimal 60 detik (`MAX_DELAY_MILLIS = 60_000L`).
   * Menetapkan kuota konkurensi maksimal 5 tugas per UID.

### 5.3. Membuat Patch Bersih & Presisi
Di direktori `~/aosp-settings/Settings`:
```bash
# Stage file baru
git add src/com/android/settings/onboarding/

# Ekspor patch bersih langsung ke direktori task Windows
git diff --staged > /mnt/d/task/patches/F-03-mitigation.patch
```

### 5.4. Kompilasi Bytecode Menggunakan Kotlinc Standalone
Kompilasi berkas Kotlin ke dalam format JAR menggunakan `android.jar` dari SDK host Windows:

```bash
cd ~/aosp-settings/Settings

kotlinc -cp "/mnt/c/Users/doeha/AppData/Local/Android/Sdk/platforms/android-35/android.jar" \
  src/com/android/settings/onboarding/OnboardingTaskStateManager.kt \
  src/com/android/settings/onboarding/OnboardingTaskManagerService.kt \
  src/com/android/settings/onboarding/tasks/DiagnosisProviderTask.kt \
  -d /tmp/onboarding_all.jar
```

### 5.5. Verifikasi Bytecode
Periksa isi kelas di dalam arsip JAR hasil kompilasi:
```bash
unzip -l /tmp/onboarding_all.jar
```

**Output Sukses (9 kelas terkompilasi sempurna):**
```text
Archive:  /tmp/onboarding_all.jar
  Length      Date    Time    Name
---------  ---------- -----   ----
       55  1980-01-01 07:00   META-INF/MANIFEST.MF
     4269  1980-01-01 07:00   com/android/settings/onboarding/OnboardingTaskManagerService.class
     1028  1980-01-01 07:00   com/android/settings/onboarding/OnboardingTaskManagerService$Companion.class
     3363  1980-01-01 07:00   com/android/settings/onboarding/OnboardingTaskStateManager.class
      977  1980-01-01 07:00   com/android/settings/onboarding/OnboardingTaskStateManager$Companion.class
     3250  1980-01-01 07:00   com/android/settings/onboarding/OnboardingTaskStateManager$TimestampedState.class
     4377  1980-01-01 07:00   com/android/settings/onboarding/tasks/DiagnosisProviderTask.class
     1144  1980-01-01 07:00   com/android/settings/onboarding/tasks/DiagnosisProviderTask$Companion.class
       24  1980-01-01 07:00   META-INF/main.kotlin_module
---------                     -------
    18487                     9 files
```

---

## 6. Ringkasan Solusi Masalah Nyata (*Field-Tested Troubleshooting*)

| Gejala Error | Penyebab | Solusi Terverifikasi |
|---|---|---|
| `Unknown AVD name [emulator-5566-avd]` | Nama AVD yang dibuat di Android Studio berbeda | Jalankan `.\emulator.exe -list-avds` untuk melihat nama persis (misal `Pixel_7`), lalu jalankan dengan nama tersebut. |
| `adb connect localhost:5566: cannot connect` | Emulator lokal otomatis menggunakan port 5554 | Jangan jalankan `adb connect`. Gunakan langsung serial bawaan `emulator-5554`. |
| `Plugin [id: 'com.android.application'] was not found` | Versi AGP tidak didefinisikan pada plugin root | Tambahkan versi eksplisit di root `build.gradle`: `id 'com.android.application' version '8.5.2'`. |
| `failed to stat ... app-debug.apk: No such file` | Hanya melakukan Gradle Sync, belum mengompilasi APK | Jalankan menu **Build $\rightarrow$ Build Bundle(s) / APK(s) $\rightarrow$ Build APK(s)** di Android Studio. |
| `Duplicate class found in kotlin-stdlib` | Konflik versi stdlib bawaan | Tambahkan plugin `id 'org.jetbrains.kotlin.android' version '1.9.24'` dan konfigurasi `jvmTarget = '17'`. |
| `AAPT: style attribute 'attr/...' not found` | Referensi tema Material3 tidak kompatibel dengan resource library | Gunakan tema dasar `Theme.AppCompat.Light.NoActionBar` dan definisikan file `colors.xml` secara mandiri. |
| `git apply: error: corrupt patch at line 39` | Line offset tidak cocok pada patch yang diedit manual | Buat berkas di tree AOSP lalu re-generasikan patch bersih via `git diff --staged`. |
| `kotlinc: error: unresolved reference 'android'` | Kompilator standalone tidak memiliki classpath runtime Android | Tambahkan parameter `-cp "/mnt/c/Users/doeha/AppData/Local/Android/Sdk/platforms/android-35/android.jar"`. |
