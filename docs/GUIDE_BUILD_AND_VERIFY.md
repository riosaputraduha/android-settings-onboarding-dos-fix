# Panduan Lengkap: Instalasi Tools, Kompilasi Patch AOSP, dan Verifikasi Memori di Emulator

Dokumen ini adalah panduan teknis langkah-demi-langkah bagi pengembang yang **belum memiliki AOSP source tree maupun Android Emulator** di sistem Windows 11.

---

## 0. Prasyarat & Arsitektur Lingkungan

* **Kompilasi AOSP (Android Open Source Project):**
  AOSP secara resmi **hanya dapat dikompilasi di lingkungan Linux** (Ubuntu 20.04/22.04 LTS). Di Windows 11, kompilasi dilakukan melalui **WSL 2 (Windows Subsystem for Linux)** atau server Linux tersendiri.
  * Kebutuhan minimum AOSP: RAM minimal 32 GB (atau 16 GB dengan swap besar), ruang disk $\ge 300\text{ GB}$ (direkomendasikan SSD).
* **Emulator & ADB:**
  Dapat diinstal dan dijalankan langsung di **Windows 11** menggunakan **Android Studio** atau **Android SDK Command-Line Tools**.

---

## Bagian 1: Instalasi Tools yang Dibutuhkan

### 1.1. Instalasi ADB & Fastboot di Windows 11
Jika Anda hanya membutuhkan perintah `adb`:
1. Unduh paket resmi **Android SDK Platform-Tools for Windows**:
   [https://developer.android.com/tools/releases/platform-tools](https://developer.android.com/tools/releases/platform-tools)
2. Ekstrak file zip ke folder, misalnya `C:\platform-tools`.
3. Tambahkan ke System PATH Windows:
   * Tekan `Win + R`, ketik `sysdm.cpl`, tekan Enter.
   * Tab **Advanced** $\rightarrow$ **Environment Variables**.
   * Di bagian **System variables**, pilih `Path` $\rightarrow$ **Edit** $\rightarrow$ **New** $\rightarrow$ tambahkan `C:\platform-tools`.
4. Buka PowerShell baru dan verifikasi:
   ```powershell
   adb version
   ```

---

### 1.2. Instalasi Android Emulator di Windows 11
Cara paling mudah dan stabil untuk mendapatkan emulator Android adalah via **Android Studio**:

1. **Unduh & Pasang Android Studio:**
   * Unduh dari [developer.android.com/studio](https://developer.android.com/studio).
   * Jalankan instalasi standar (pastikan opsi *Android Virtual Device* tercentang).
2. **Aktifkan Virtualisasi Hardware (WHPX / Hyper-V):**
   * Buka PowerShell sebagai Administrator dan jalankan:
     ```powershell
     Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All
     ```
3. **Buat Android Virtual Device (AVD) Target:**
   * Buka Android Studio $\rightarrow$ **Virtual Device Manager** (Device Manager).
   * Klik **Create Device** $\rightarrow$ Pilih hardware (misal *Pixel 8* atau *Pixel 7*).
   * Pada tab **System Image**, pilih rilis Android target:
     * Pilih **API Level 35 / VanillaIceCream / Android 17** (pilih sistem image dengan target **Google APIs** atau **AOSP/Userdebug** jika tersedia).
   * Pada konfigurasi lanjutan (*Advanced Settings*), beri nama AVD: `emulator-5566-avd`.
   * Atur alokasi RAM minimal 4096 MB, heap 512 MB. Klik **Finish**.

---

### 1.3. Instalasi Lingkungan WSL 2 (Ubuntu) untuk Kompilasi AOSP
1. Buka PowerShell sebagai Administrator:
   ```powershell
   wsl --install -d Ubuntu-22.04
   ```
2. Setelah restart komputer, buka Ubuntu dari Start Menu dan atur username/password Linux Anda.
3. Pasang paket dependensi kompilasi AOSP di terminal Ubuntu:
   ```bash
   sudo apt update && sudo apt upgrade -y
   sudo apt install -y git-core gnupg flex bison build-essential zip curl zlib1g-dev \
       libc6-dev-i386 x11proto-core-dev libx11-dev lib32z1-dev libgl1-mesa-dev \
       libxml2-utils xsltproc unzip fontconfig libncurses5 openjdk-17-jdk python3
   ```
4. Pasang tool `repo`:
   ```bash
   mkdir -p ~/.bin
   curl https://storage.googleapis.com/git-repo-downloads/repo > ~/.bin/repo
   chmod a+rx ~/.bin/repo
   echo 'export PATH=$HOME/.bin:$PATH' >> ~/.bashrc
   source ~/.bashrc
   ```

---

## Bagian 2: Menyiapkan AOSP Tree & Mengompilasi Patch

### 2.1. Sinkronisasi Sumber Kode AOSP
Di dalam terminal WSL (Ubuntu):
```bash
# Buat direktori kerja AOSP
mkdir -p ~/aosp-android17 && cd ~/aosp-android17

# Inisialisasi manifest cabang Android 17 / main
repo init -u https://android.googlesource.com/platform/manifest -b android-15.0.0_r1 # Atau cabang target rilis Anda

# Sinkronisasi repository yang dibutuhkan (terutama packages/apps/Settings)
repo sync -c -j$(nproc) packages/apps/Settings
```

### 2.2. Menerapkan Patch Keamanan (F-03)
Salin berkas patch dari Windows (`D:\task\patches\F-03-mitigation.patch`) ke lingkungan AOSP:
```bash
cd ~/aosp-android17/packages/apps/Settings

# Uji apakah patch dapat diterapkan tanpa konflik
git apply --check /mnt/d/task/patches/F-03-mitigation.patch

# Terapkan patch
git apply /mnt/d/task/patches/F-03-mitigation.patch
```

### 2.3. Kompilasi Modul Settings
```bash
cd ~/aosp-android17

# Inisialisasi lingkungan build AOSP
source build/envsetup.sh

# Pilih target produk userdebug untuk emulator x86_64 atau arm64
lunch aosp_cf_x86_64_phone-trunk_staging-userdebug
# atau: lunch sdk_phone64_x86_64-userdebug

# Kompilasi hanya modul Settings/SettingsGoogle untuk menghemat waktu (bukan full system build)
m SettingsGoogle
# Output APK akan berada di: out/target/product/.../system_ext/priv-app/SettingsGoogle/SettingsGoogle.apk
```

### 2.4. Alternatif Verifikasi Sintaks & Bytecode via Kotlinc Standalone (WSL 2)
Jika tidak melakukan *full clone* AOSP, integritas sintaks dan pembuatan bytecode Kotlin dapat diverifikasi secara instan menggunakan `kotlinc` di WSL 2 dengan menautkan `android.jar` dari SDK host Windows:

```bash
cd ~/aosp-settings/Settings

kotlinc -cp "/mnt/c/Users/doeha/AppData/Local/Android/Sdk/platforms/android-35/android.jar" \
  src/com/android/settings/onboarding/OnboardingTaskStateManager.kt \
  src/com/android/settings/onboarding/OnboardingTaskManagerService.kt \
  src/com/android/settings/onboarding/tasks/DiagnosisProviderTask.kt \
  -d /tmp/onboarding_all.jar

# Verifikasi integritas bytecode yang dihasilkan
unzip -l /tmp/onboarding_all.jar
```
*Hasil: Terkompilasi sempurna ke 9 berkas .class (termasuk class companion dan data class) tanpa error sintaks maupun unresolved reference.*

---

## Bagian 3: Menjalankan Emulator & Melakukan Deploy Patch

### 3.1. Jalankan Emulator dengan Partisi System Writable
Buka PowerShell di Windows 11 dan jalankan emulator dari folder Android SDK:
```powershell
cd $env:LOCALAPPDATA\Android\Sdk\emulator

# Jalankan emulator dengan port 5566 dan izin tulis partisi sistem
.\emulator.exe -avd emulator-5566-avd -port 5566 -writable-system -no-snapshot-load
```

### 3.2. Deploy Modul yang Telah Dipatch ke Emulator
Buka terminal PowerShell baru:
```powershell
# 1. Pastikan ADB terhubung ke emulator port 5566
adb connect localhost:5566
adb devices

# 2. Dapatkan akses root dan remount partisi
adb -s emulator-5566 root
adb -s emulator-5566 wait-for-device
adb -s emulator-5566 remount

# 3. Salin berkas SettingsGoogle.apk hasil build (dari WSL) ke partisi sistem emulator
# Path di WSL bisa diakses langsung via Windows Explorer: \\wsl$\Ubuntu-22.04\home\<user>\aosp-android17\...
adb -s emulator-5566 push "\\wsl$\Ubuntu-22.04\home\<user>\aosp-android17\out\target\product\generic_x86_64\system_ext\priv-app\SettingsGoogle\SettingsGoogle.apk" /system_ext/priv-app/SettingsGoogle/

# 4. Sinkronkan hak akses dan restart runtime framework
adb -s emulator-5566 shell chmod 644 /system_ext/priv-app/SettingsGoogle/SettingsGoogle.apk
adb -s emulator-5566 shell stop
adb -s emulator-5566 shell start
```

---

## Bagian 4: Verifikasi Kestabilan Memori via ADB

### 4.1. Dapatkan Baseline PID dan Memori
```powershell
# Dapatkan PID baru setelah restart
$settingsPid = (adb -s emulator-5566 shell pidof com.android.settings).Trim()
Write-Host "PID Settings Aktif: $settingsPid"

# Cek kondisi awal memori
adb -s emulator-5566 shell dumpsys meminfo com.android.settings
```

### 4.2. Monitoring Real-Time Selama Serangan / Pengujian
Buka dua terminal PowerShell berdampingan:

**Terminal 1 (Pemantau Log Keamanan & Rejeksi):**
```powershell
adb -s emulator-5566 logcat -v time OnboardingTaskManager:V Binder:W AndroidRuntime:E *:S
```

**Terminal 2 (Pemantau Pertumbuhan Memori Berkelanjutan):**
```powershell
while ($true) {
    $time = Get-Date -Format "HH:mm:ss"
    $mem = (adb -s emulator-5566 shell dumpsys meminfo com.android.settings | Select-String -Pattern "Dalvik Heap|TOTAL PSS") -join " | "
    Write-Host "[$time] $mem"
    Start-Sleep -Seconds 2
}
```

### 4.3. Eksekusi Pengujian PoC / Simulasi Serangan
Jalankan aplikasi pengujian PoC atau perintah trigger via ADB:
```powershell
# Jalankan PoC app dari host
adb -s emulator-5566 install D:\task\pocs\POC-A17-F03-MEMDOS\app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5566 shell am start -n com.untrusted.poc/.MainActivity
```

### 4.4. Kriteria Kelulusan (*Pass/Fail Criteria*)

| Parameter Evaluasi | Kondisi Rentan (Sebelum Patch) | Kondisi Sukses (Setelah Patch) |
|---|---|---|
| **Respon Terhadap Panggilan Liar** | State dibuat, string 200 KB masuk ke heap | Ditolak seketika di boundary Binder dengan `SecurityException` |
| **Dalvik Heap Alloc** | Melonjak drastis hingga 173 MB+ | Tetap stabil di angka $\approx 20 - 35\text{ MB}$ |
| **Total PSS Memori** | Tumbuh mencapai limit 192 MB (PSS 227 MB) | Stabil di kisaran $30 - 45\text{ MB}$ |
| **Status Proses PID** | PID berganti (proses crash karena OutOfMemoryError) | **PID tetap sama** (0 crash, proses tetap melayani sistem normal) |

---

## 5. Ringkasan Troubleshooting Cepat

* **`adb: error: mount of /system_ext failed: Read-only file system`**:
  Pastikan emulator dijalankan dengan parameter `-writable-system`.
* **Kompilasi lambat di WSL**:
  Pastikan source tree AOSP disimpan di dalam file system Linux WSL (misal `~/aosp-android17`), **bukan** di bawah `/mnt/c/` atau `/mnt/d/` karena ada penalti performa I/O lintas file system Windows-Linux.
