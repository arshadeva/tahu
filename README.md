# Otacix — by Oscar Mandala

Pemutar musik Android (Kotlin + Jetpack Compose + Media3) dengan tampilan **liquid glass ala iOS**, warna dominan **biru & kuning**.
Semua fitur mengikuti `index.html` versi web (Ocalix), dijadikan aplikasi native.

## Cara dapat APK (tanpa Android Studio)
1. Buat repo baru di GitHub, upload seluruh isi folder ini (bukan zip-nya).
2. Buka tab **Actions** → workflow **Build APK** jalan otomatis (atau klik *Run workflow*).
3. Selesai (±5 menit) → buka run-nya → bagian **Artifacts** → unduh **Otacix-APK** → pasang `app-debug.apk`.

Atau buka folder ini di Android Studio (Koala/Ladybug) → Run.

## Fitur
- Beranda: salam, lagu cepat, streak harian, mood, Andalanmu, rak Lagi Tren / Santai Sore / Hits Global
- Cari: pencarian YouTube (cadangan: preview iTunes 30 detik), riwayat pencarian
- Pustaka: playlist, Favorit, Riwayat, buat/ganti nama/hapus playlist
- Kamu: statistik, artis teratas, cadangkan/pulihkan/reset (format JSON sama dengan versi web)
- Poster Studio: 3 tata letak, geser kartu, ukuran/putar, latar foto, ekspor PNG 1080×1920
- Mini player + full player: seek, acak, ulang, suka, tambah playlist, timer tidur
- **Putar di latar belakang**: layanan media (notifikasi + kunci layar), lanjut saat layar mati
- Splash screen + ikon dari logo Otacix

## Fitur tambahan (tidak ada di versi web)
- **Efek Nightcore / Slowed** (kecepatan + nada) satu ketukan
- **Timer tidur dengan fade-out** (volume mengecil pelan 12 detik terakhir)
- **Goyang untuk ganti lagu**
- **Kualitas audio**: Hemat data / Seimbang / Tinggi
- **Tes latar belakang**: diagnosis kenapa lagu tidak bisa diputar

## HP spek rendah vs spek atas (Kamu → Mode Glass: Otomatis / Ringan / Penuh)
- **Ringan** (otomatis di RAM < 5 GB / Android lama): latar statis, tanpa animasi, tanpa bayangan, gambar kecil, cache hemat
- **Penuh** (otomatis di RAM ≥ 5 GB): aurora animasi, blur nyata (Android 12+), bayangan, kilau kaca mengikuti kemiringan HP, nav pill memantul

## Catatan
- Sumber audio sama dengan versi web: YouTube langsung → instance Piped → (pencarian) preview iTunes. Jika YouTube mengubah API, gunakan **Tes latar belakang**.
- Terinspirasi arsitektur Metrolist (Innertube + Media3). Kode Metrolist tidak disalin; jika kamu menggabungkan kode Metrolist (GPL-3.0), proyekmu juga wajib GPL-3.0.
