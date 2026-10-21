# PoC: POC-A17-F03-MEMDOS

Aplikasi Android sederhana tanpa izin (*zero permissions*, UID `10xxx`) untuk mereproduksi kondisi Denial of Service (DoS) lokal pada proses `com.android.settings`.

## Vektor Pengujian

1. **Skenario 1 — Saturasi Kunci Panjang (*Long-Key Heap Saturation*):**
   - Mengirim 1.000 panggilan Binder dengan payload `COMPONENT` berukuran ~200 KB per panggilan.
   - Mengisi Java Heap hingga 173 MB (PSS 227 MB), mendekati batas pertumbuhan 192 MB.

2. **Skenario 2 — Penundaan Tanpa Batas (*Unbounded Delay*):**
   - Mengirim 25.000 panggilan dengan `{delayFor="PT24H"}` secara beruntun.
   - Memicu akumulasi thread/coroutine hingga terjadi `java.lang.OutOfMemoryError`.