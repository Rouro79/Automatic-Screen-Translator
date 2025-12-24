📖 ComicTranslate

ComicTranslate adalah aplikasi Android yang memungkinkan pengguna menerjemahkan teks pada komik (atau konten layar lainnya) secara real-time dengan cara screen capture, OCR, dan overlay terjemahan langsung di atas layar.

Aplikasi ini dirancang khusus untuk membantu membaca komik berbahasa asing (terutama Inggris → Indonesia) tanpa perlu screenshot manual atau berpindah aplikasi.

---

✨ Fitur Utama

📸 Screen Capture Otomatis
Mengambil tampilan layar menggunakan MediaProjection API.

🔍 OCR (Text Recognition)
Mendeteksi teks dari layar menggunakan Google ML Kit Text Recognition.

🌐 Terjemahan Otomatis
Menerjemahkan teks dari Bahasa Inggris ke Bahasa Indonesia menggunakan ML Kit Translation.

💬 Deteksi Balon Dialog
Mengelompokkan teks berdasarkan area (speech balloon) agar terjemahan lebih kontekstual.

🪟 Overlay Terjemahan
Menampilkan hasil terjemahan langsung di atas layar menggunakan sistem overlay Android.

📚 Kamus Istilah Komik
Mendukung istilah khusus seperti:

senpai, sensei

oppa, noona, hyung

dan istilah khas komik lainnya

⚡ Caching
Menghindari terjemahan berulang untuk teks yang sama agar lebih cepat dan efisien.

---

🛠️ Teknologi yang Digunakan

Bahasa: Kotlin

UI: Jetpack Compose + Material 3

OCR: Google ML Kit – Text Recognition

Translation: Google ML Kit – On-device Translation

Screen Capture: MediaProjection API

Overlay: WindowManager

Asynchronous: Kotlin Coroutines

---

📂 Struktur Project

ComicTranslate/
├── app/
│   ├── src/main/java/com/example/comictranslate/
│   │   ├── MainActivity.kt
│   │   ├── ScreenCaptureService.kt
│   │   ├── ScreenCaptureAnalyzer.kt
│   │   ├── OCRBox.kt
│   │   ├── OverlayManager.kt
│   │   ├── SpeechBalloonTranslator.kt
│   │   ├── TranslatorManager.kt
│   │   ├── CacheManager.kt
│   │   └── MyApplication.kt
│   └── src/main/assets/
│
└── build.gradle.kts

---

🔐 Permission yang Dibutuhkan

Aplikasi ini membutuhkan beberapa permission penting:

Screen Capture – untuk membaca tampilan layar

Draw Over Other Apps – untuk menampilkan overlay terjemahan

Internet (opsional) – untuk pengunduhan model ML Kit

Saat pertama kali dijalankan, aplikasi akan meminta izin-izin tersebut secara otomatis.


---

🚀 Cara Menjalankan Project

1. Clone repository ini:

git clone https://github.com/username/ComicTranslate.git

2. Buka project di Android Studio (disarankan versi terbaru).

3. Pastikan:

Minimum SDK sesuai dengan build.gradle

Google ML Kit dependencies terunduh dengan benar

4. Jalankan aplikasi di perangkat fisik

> ⚠️ Screen capture tidak bekerja optimal di emulator

5. Aktifkan:

Izin Overlay

Izin Screen Capture

---

📌 Catatan

Aplikasi ini masih menggunakan pasangan bahasa English → Indonesian (bisa dikembangkan).

Akurasi OCR tergantung kualitas teks di layar.

Cocok untuk komik digital, webtoon, dan manga scan berbahasa Inggris.

---

📄 Lisensi

Project ini bersifat open-source dan bebas dikembangkan lebih lanjut untuk keperluan edukasi dan eksperimen.
