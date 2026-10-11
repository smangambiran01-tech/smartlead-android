# SMART LEAD – Aplikasi Android

Aplikasi Android SMAN 1 Gambiran yang langsung membuka Web App **Google Apps Script** SMART LEAD
(tanpa Netlify). Satu aplikasi untuk guru, akun kelas, kepala sekolah, dan admin — tampilan mengikuti akun yang login.

Fitur aplikasi:
- Kamera untuk scan QR presensi (izin diminta otomatis).
- Lokasi untuk presensi di area sekolah.
- "Ambil foto QR" dan unggah bukti memakai kamera/galeri HP.
- Unduh CSV/Excel tersimpan di folder **Download/SMART LEAD**.
- Tautan WhatsApp, Google Maps, dan Drive dibuka di aplikasinya masing-masing.
- Layar "Tidak ada internet" dengan tombol Coba lagi.

## Membuat APK (tanpa Android Studio)

1. Buat akun di **github.com** (gratis) lalu klik **New repository** → beri nama `smartlead-android` → **Create**.
2. Klik **uploading an existing file**, seret **seluruh isi** folder ini (termasuk folder `.github`), lalu **Commit changes**.
   - Di Windows, pastikan folder `.github` ikut. Bila tidak terlihat, aktifkan *Hidden items* di File Explorer.
3. Isi kunci tanda tangan (sekali saja, agar pembaruan APK bisa dipasang tanpa uninstall):
   **Settings → Secrets and variables → Actions → New repository secret**, buat 4 secret dari folder `kunci`:
   | Nama secret | Isi (salin dari file) |
   |---|---|
   | `KEYSTORE_BASE64` | isi `KEYSTORE_BASE64.txt` |
   | `KEYSTORE_PASSWORD` | isi `KEYSTORE_PASSWORD.txt` |
   | `KEY_ALIAS` | isi `KEY_ALIAS.txt` |
   | `KEY_PASSWORD` | isi `KEY_PASSWORD.txt` |
4. Buka tab **Actions** → **Buat APK SMART LEAD** → **Run workflow**. Tunggu ±5–8 menit sampai centang hijau.
5. Buka **Releases** (kolom kanan halaman repository) → unduh **SMART_LEAD_v1.0.x.apk**.
   Link halaman Releases ini bisa dibagikan ke guru dan HP kelas.

## Memasang di HP

1. Buka file `.apk` di HP Android → izinkan **Instal aplikasi tidak dikenal** bila diminta → **Pasang**.
2. Buka SMART LEAD → login → izinkan **Kamera** dan **Lokasi** saat diminta.
3. Akun kelas terikat ke satu perangkat. Bila HP kelas sebelumnya dipakai lewat browser, admin cukup
   klik **Reset perangkat** di menu *Pengguna & Akses*.

## Mengubah pengaturan

Edit file `gradle.properties` langsung di GitHub (ikon pensil), lalu **Commit** — APK baru dibuat otomatis:
- `SMARTLEAD_URL` – alamat Web App Apps Script (berakhiran `/exec`). Ganti bila deployment baru punya URL berbeda.
  *Catatan:* memperbarui versi deployment (Kelola deployment → Edit → Versi baru) **tidak** mengubah URL, jadi APK tidak perlu dibuat ulang.
- `SMARTLEAD_NAME` – nama aplikasi di HP.
- `SMARTLEAD_VERSION` – naikkan (mis. `1.1`) saat merilis versi baru.

## Penting

- Simpan folder `kunci` di tempat aman (mis. Google Drive sekolah, jangan dibagikan). Tanpa kunci yang sama,
  HP harus uninstall dulu sebelum memasang versi baru.
- Bila Secrets belum diisi, APK tetap dibuat dengan kunci sementara (muncul peringatan kuning di Actions).
- Data tetap di Google Drive/Spreadsheet sekolah; aplikasi tidak menyimpan data di HP.
