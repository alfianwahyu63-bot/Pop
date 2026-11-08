# POP Studio Android — proyek APK (offline)

Proyek Android asli yang membungkus editor POP Studio V7 menjadi aplikasi dengan WebView, tanpa koneksi internet. **Ini adalah source project, bukan file APK yang sudah di-compile.**

## Fitur
- Editor POP lengkap: import background/foto dari galeri, periode, deskripsi, harga coret dan promo.
- Grid 1×2, 2×2, 3×2 dan ukuran kustom; shape rounded, layout dan floating live preview.
- Ukuran gambar **tinggi dan lebar terpisah** sesuai V7; posisi gambar kiri/kanan/atas/bawah/tengah, outline teks.
- Galeri HP via file chooser Android.
- Export PNG/JPG dan simpan proyek JSON ke **Downloads/POP Studio/** melalui MediaStore.
- Cetak / Save as PDF via Android Print.
- Autosave IndexedDB lokal dalam aplikasi (data tersimpan selama aplikasi tidak dihapus).
- Bisa dipakai offline; Android 10+.

## Cara bikin APK di GitHub Actions (tanpa Android Studio)

1. Buat repo GitHub kosong, misalnya `pop-studio-android` (jika repo sudah ada, buat folder proyek terpisah atau gunakan branch khusus).
2. Ekstrak ZIP ini. **Unggah seluruh isinya** ke root repo, sehingga misalnya `.github/workflows/build-apk.yml`, `app/build.gradle`, `app/src/main/...` ada di path yang benar. Jangan unggah ZIP-nya saja ke repo.
3. Setelah commit/push ke branch `main`, buka tab **Actions** → **Build POP Studio APK**. Workflow bisa berjalan otomatis; atau pilih **Run workflow**.
4. Kalau status build ✅ hijau, buka run terbaru, scroll ke **Artifacts**, unduh **POP-Studio-APK-Android**.
5. Ekstrak ZIP artifact hasil GitHub → ada `POP-Studio-v1.0.0.apk`. Buka APK dari Android lalu izinkan instalasi aplikasi dari sumber tersebut bila diminta.
6. APK terpasang bernama **POP Studio**.

Catatan: GitHub Actions menandatangani debug APK memakai debug key sementara. Pada build baru, key bisa berubah, sehingga Android mungkin meminta uninstall versi sebelumnya. **Selalu Simpan Proyek → backup JSON sebelum uninstall**. Untuk distribusi dan pembaruan permanen, buat release signing dengan keystore pribadi yang disimpan aman sebagai GitHub Secrets / di komputer sendiri.

## Cara build di Android Studio
1. Buka direktori proyek ini di Android Studio (File → Open).
2. Gunakan JDK 17, Gradle 8.9, Android SDK platform 35.
3. Tunggu sync selesai, lalu menu Build → Build Bundle(s)/APK(s) → Build APK(s).
4. APK debug ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Mengubah editor HTML ke versi berikutnya
Cukup timpa satu file: `app/src/main/assets/index.html`, lalu commit. Jika nanti memperbarui aplikasi dengan keystore berbeda, Android akan menolak update (tidak masalah dengan desainnya). 

## Teknologi dan privasi
- Android Java + WebView, tanpa library pihak ketiga.
- Tidak meminta akses internet, kamera, kontak, lokasi, atau izin penyimpanan luas.
- Pilihan gambar menggunakan galeri Android. File hasil desain disimpan di folder Downloads.
- Semua template dan gambar contoh disimpan sebagai data inline dalam HTML.

## Catatan build
Lingkungan pembuat paket sumber ini tidak memiliki Android SDK/Gradle, sehingga belum ada APK hasil compile yang bisa diuji langsung. Proyek disiapkan untuk build otomatis di GitHub Actions / Android Studio.
