# Projekt: Radio Samochodowe (Native Android / Kotlin) — Radio Car

Projekt w 100% natywnej aplikacji Android napisanej w języku Kotlin (Jetpack Compose). Aplikacja służy jako odtwarzacz stacji radiowych online zoptymalizowany pod kątem jazdy samochodem (obsługa w tle, sterowanie z kierownicy Bluetooth, przesyłanie tytułów utworów na wyświetlacz samochodowy AVRCP, estetyka Glassmorphism inspirowana projektem „Radio Dell”).

Pakiet aplikacji: `com.seweryn.radiocar`

---

## 📱 Środowisko Developerskie i Emulacja (Quick-Start na Start Nowej Sesji)

### 1. Ścieżki i Zmienne Środowiskowe
* **JDK 17:** `C:\Users\admin\.jdks\jdk-17.0.20.1+1` (Eclipse Temurin JDK 17).
  * Skonfigurowane w `gradle.properties`: `org.gradle.java.home=C:/Users/admin/.jdks/jdk-17.0.20.1+1` oraz `org.gradle.java.installations.auto-download=false`.
* **Android SDK & ADB:** `C:\Users\admin\AppData\Local\Android\Sdk\platform-tools\adb.exe` (dostępne w zmiennej `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`).
* **Wirtualny telefon (Emulator Android Studio):**
  * Aktywny emulator: `emulator-5554` (Android 15/16 / API 36, rozdzielczość 1080x2400).
  * Emulator uruchamiany jest z poziomu Android Studio (Device Manager) lub komendą CLI:
    ```powershell
    & "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd <NAZWA_AVD>
    ```

### 2. Szybkie Komendy do Budowania, Wgrywania i Testowania
* **Kompilacja i instalacja debug APK na emulatorze jednym poleceniem:**
  ```powershell
  $env:JAVA_HOME="C:\Users\admin\.jdks\jdk-17.0.20.1+1"
  .\gradlew installDebug
  ```
* **Uruchomienie / Restart aplikacji na emulatorze przez ADB:**
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.seweryn.radiocar/.MainActivity
  ```
* **Przeładowanie z poziomu Android Studio:**
  * **`Ctrl + F10`** – Apply Changes and Restart Activity (błyskawiczne odświeżenie bez reinstalacji).
  * **`Shift + F10`** – Rerun app (pełna rekompilacja i start).
* **Zrzut ekranu emulatora do weryfikacji:**
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell screencap -p /sdcard/screen.png
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" pull /sdcard/screen.png ./screen.png
  ```
* **Podgląd logów radia (playback i błędy strumieni):**
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s RadioPlayerService:D ExoPlayerImplInternal:E
  ```

---

## 🎧 Architektura Aplikacji i Zaimplementowane Moduły

### 1. Backend Audio & Praca w Tle ([RadioPlayerService.kt](file:///g:/PROJEKTY_ANTY/Radio_Car/app/src/main/java/com/seweryn/radiocar/service/RadioPlayerService.kt))
* Działa jako **Foreground Service** z powiadomieniem systemowym (`MediaSessionService`), dzięki czemu system Android nie ubija odtwarzacza po wygaszeniu ekranu.
* **Kodeki i protokoły strumieniowania:**
  * MP3, AAC, ADTS, OGG, Opus, FLAC.
  * HLS (`.m3u8` – np. Polskie Radio Czwórka) dzięki `androidx.media3:media3-exoplayer-hls`.
  * DASH (`.mpd`) dzięki `androidx.media3:media3-exoplayer-dash`.
* **Nieszyfrowane strumienie HTTP i przekierowania:**
  * W [AndroidManifest.xml](file:///g:/PROJEKTY_ANTY/Radio_Car/app/src/main/AndroidManifest.xml) włączono `android:usesCleartextTraffic="true"`, co pozwala na odtwarzanie serwerów radiowych Shoutcast/Icecast na nieszyfrowanych portach `http://` (stacje zagraniczne i lokalne, np. Arrow Classic Rock, Hard Rock Radio FM).
  * `DefaultHttpDataSource.Factory` ma włączone `setAllowCrossProtocolRedirects(true)` oraz stały nagłówek `User-Agent: "RadioCar/1.0 (Linux; Android; ExoPlayer)"`.
* **Bluetooth AVRCP & Wyświetlacz Samochodowy:**
  * Odsłuch metadanych ICY w locie (`onMediaMetadataChanged`). Tagi `Wykonawca - Tytuł` są parsowane i przekazywane do `MediaMetadata` sesji, co pozwala na wyświetlanie nazwy utworu na ekranie radia samochodowego / zegarach.
  * Sterowanie z przycisków kierownicy (Play, Stop, Next, Prev) natywnie przekierowywane do `MediaSession`.
  * Automatyczne wznawianie po połączeniu z Bluetooth (`onPlaybackResumption`).

### 2. Zarządzanie Gniazdami Stacji i Wyszukiwarka Online
* **Model i pamięć ([StationRepository.kt](file:///g:/PROJEKTY_ANTY/Radio_Car/app/src/main/java/com/seweryn/radiocar/data/repository/StationRepository.kt)):**
  * 10 gniazd stacji zapisywanych trwale w `SharedPreferences` w formacie JSON (`radio_car_prefs.xml`).
  * Początkowo załadowane stacje z projektu referencyjnego `Radio_Dell` (Antyradio, Antyradio Classic Rock, Greatest, Radio 357, RMF Rock, RMF Rock + Fakty, Polskie Radio Czwórka + wolne gniazda).
  * **Inteligentna zamiana gniazd (Swap):** Jeśli edytujemy gniazdo i wybierzemy inny zajęty numer gniazda docelowego, stacje zamieniają się miejscami bez nadpisywania/kasowania.
* **Wyszukiwarka Stacji Online ([RadioSearchRepository.kt](file:///g:/PROJEKTY_ANTY/Radio_Car/app/src/main/java/com/seweryn/radiocar/data/repository/RadioSearchRepository.kt)):**
  * Integracja z bazą **Radio-Browser API** (`de1.api.radio-browser.info`).
  * Wyszukiwanie po nazwie, haśle lub filtrach gatunkowych (Rock, Metal, Pop, Jazz, Electronic, Polska, News).
  * Wybór stacji w oknie [EditStationDialog.kt](file:///g:/PROJEKTY_ANTY/Radio_Car/app/src/main/java/com/seweryn/radiocar/ui/components/EditStationDialog.kt) automatycznie wypełnia nazwę, bezpośredni stream URL, adres logotypu i pasujące emoji.

### 3. Płyta Winylowa i Gest Sterowania ([VinylCover.kt](file:///g:/PROJEKTY_ANTY/Radio_Car/app/src/main/java/com/seweryn/radiocar/ui/components/VinylCover.kt))
* **Czysty wygląd bez nałożonych symboli:** Płyta nie posiada sztucznych ikon play/pauzy – pozostaje czystą grafiką winylową.
* **Dotknięcie płyty:** Działa jako natychmiastowy przełącznik **Play / Stop**.
* **Gest przeciągnięcia (swipe góra/dół):** Wymusza reconnect ze strumieniem radiowym.
* **Dynamiczna poświata neonowa:**
  * **Zielona (`AccentGreen` `#00E676`)** – gdy płyta wiruje i strumień jest odtwarzany.
  * **Czerwona (`AccentRed` `#FF2A4B`)** – gdy odtwarzanie jest zatrzymane (STOP).
  * Płynne przejście koloru za pomocą `animateColorAsState(tween(500))`.
* **Domyślne wirujące logo (`DefaultStationLogo`):** Gdy stacja nie ma loga lub wystąpi błąd sieci/404, na środku płyty kręci się stylowe logo zastępcze z ikoną radia i skróconą nazwą stacji, zabarwione pod bieżący stan (zielony/czerwony).

### 4. Dolna Karuzela Gniazd ([StationSlotsCarousel.kt](file:///g:/PROJEKTY_ANTY/Radio_Car/app/src/main/java/com/seweryn/radiocar/ui/components/StationSlotsCarousel.kt))
* **Automatyczne centrowanie odtwarzanej stacji:**
  * Po zmianie stacji (przyciskami Poprzednia/Następna, z kierownicy lub dotknięciem kafelka), lista na dole płynnie centruje aktywne gniazdo (`animateScrollToItem`) w osi ekranu.
  * Pierwsze gniazdo (#1) przylega do lewej krawędzi, a ostatnie gniazdo do prawej krawędzi ekranu (zgodnie z naturalnym zakresem przewijania).
* **Edycja gniazda:** Kliknięcie ikony ołówka lub długie przytrzymanie kafelka otwiera modal edycji z wyszukiwarką online i opcją zamiany miejscami.

---

## 📂 Struktura Plików Projektu
```
g:/PROJEKTY_ANTY/Radio_Car/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml                  # Uprawnienia, ForegroundService, usesCleartextTraffic
│   │   ├── java/com/seweryn/radiocar/
│   │   │   ├── MainActivity.kt                  # Punkt startowy Compose, uprawnienia
│   │   │   ├── data/
│   │   │   │   ├── model/Station.kt             # Model stacji (id, name, streamUrl, logoUrl, icon)
│   │   │   │   └── repository/
│   │   │   │       ├── StationRepository.kt     # Zapis SharedPreferences JSON, slot swap
│   │   │   │       └── RadioSearchRepository.kt # Integracja z Radio-Browser API
│   │   │   ├── service/
│   │   │   │   └── RadioPlayerService.kt        # ExoPlayer MediaSessionService, ICY metadata, DataSource
│   │   │   ├── ui/
│   │   │   │   ├── RadioViewModel.kt            # MediaController, nawigacja, wyszukiwanie
│   │   │   │   ├── screens/RadioScreen.kt       # Główny ekran samochodu Glassmorphic
│   │   │   │   ├── components/
│   │   │   │   │   ├── VinylCover.kt            # Płyta winylowa, gesty, poświata zielona/czerwona
│   │   │   │   │   ├── StationSlotsCarousel.kt  # Karuzela 10 gniazd z centrowaniem
│   │   │   │   │   ├── EditStationDialog.kt     # Modal edycji, search online, zamiana
│   │   │   │   │   └── GlassmorphicCard.kt      # Stylistyczny kontener szklany
│   │   │   │   └── theme/                       # Kolory (AccentGreen, AccentRed), typografia, motyw
├── gradle/
│   └── libs.versions.toml                       # Media3, Compose BOM, Coil, Kotlin Serialization
├── gradle.properties                            # Ścieżka do JDK 17
└── PROJECT_KNOWLEDGE.md                         # Bieżąca wiedza i kompendium projektu
```
