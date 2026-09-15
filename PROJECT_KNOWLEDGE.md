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
* **Bluetooth AVRCP & Wyświetlacz Samochodowy (BMW iDrive):**
  * **Odsłuch metadanych ICY w locie i serwis RDS Eurozet:**
    * Wbudowane kanały ICY: `onMetadata` (`IcyInfo`, `IcyHeaders`) oraz `onMediaMetadataChanged` formatowane z czyszczeniem znaków BOM i prefiksów.
    * Główne stacje Eurozet (np. Antyradio slot 1, Radio ZET slot 9) nie transmitują ICY w strumieniu internetowym – aplikacja pobiera dane o utworze na żywo w tle z endpointu `https://rds.eurozet.pl/reader/var/antyradio.json` (co 15 sekund, ~35 KB/godz.).
    * 👤 **Wykonawca (ikona człowieka w BMW):**
      * Gdy łączy się ze strumieniem: `"Connecting"`
      * Gdy połączone i gra: `"Playing"` (dopóki brak wykonawcy), a po pobraniu nazwa wykonawcy (np. `"Breakout"`)
      * Gdy zapauzowane / zatrzymane: `"Paused"` lub `"Stopped"`
    * 💿 **Płyta (ikona płyty w BMW):**
      * W czasie łączenia: nazwa stacji ze slotu (np. `"Antyradio"`, `"Radio ZET"`)
      * Po połączeniu: nazwa stacji pobrana ze strumienia ICY/RDS (np. `"ANTYRADIO"`), lub fallback do slotu
    * 🎵 **Utwór (ikona nutek w BMW):**
      * W czasie łączenia / dopóki brak utworu: nazwa stacji
      * Po pobraniu utworu: tytuł utworu (np. `"Kiedy Bylem Malym Chlopcem"`)
* **Karta Odtwarzacza w Panelu Powiadomień Androida (Media Notification / Notification Shade):**
  * Integracja z `androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession)`.
  * Po ściągnięciu belki z góry ekranu telefonu wyświetla się pełnoprawna karta systemowa ze sterowaniem:
    * Przyciski: Poprzednia stacja (`⏮`), Odtwarzaj / Pauza (`▶` / `⏸`), Następna stacja (`⏭`).
    * Okładka / logo stacji lub albumu ładowane asynchronicznie przez Coil.
    * Kliknięcie w kartę przywraca aplikację na pierwszy plan.
* **Automatyczne odświeżanie po zmianie stacji (`CustomForwardingPlayer`):**
  * Opakowanie `ExoPlayer` w `CustomForwardingPlayer` przechwytujący listenery MediaSession i rozsyłający `dispatchMetadataChanged()`.
  * Zapobiega zacinaniu się wyświetlacza w aucie po przełączeniu stacji.
* **Sterowanie z kierownicy i radia samochodowego (Next/Prev Slot):**
  * Deklaracja i obsługa `COMMAND_SEEK_TO_NEXT` i `COMMAND_SEEK_TO_PREVIOUS`.
  * Obsługa `onMediaButtonEvent` dla kodów `KEYCODE_MEDIA_NEXT` i `KEYCODE_MEDIA_PREVIOUS`.
* **Rejestracja w Androidzie jako odtwarzacz muzyczny:**
  * `android:appCategory="audio"` w `AndroidManifest.xml`.
  * `intent-filter` dla `android.intent.category.APP_MUSIC` oraz `android.media.action.MEDIA_PLAY_FROM_SEARCH`.
  * Odbiornik `MediaButtonReceiver`. Dzięki temu aplikacja pojawia się w procedurach Samsunga (*Tryby i procedury* / automatyzacje po połączeniu z autem).
* **Dynamiczny nagłówek urządzenia Bluetooth ([BluetoothDeviceTracker.kt](file:///g:/PROJEKTY_ANTY/Radio_Car/app/src/main/java/com/seweryn/radiocar/util/BluetoothDeviceTracker.kt)):**
  * Automatyczne wykrywanie podłączonego urządzenia audio Bluetooth (A2DP / Handsfree / BLE).
  * Inteligentne formatowanie nazw (np. `BMW - 6784 ghy` -> `BMW`, `JBL 678857` -> `JBL Audio`, `BMW X3` -> `BMW X3`).
  * Gdy brak połączenia z urządzeniem audio: nagłówek wyświetla `"SEWER'S MOBILE RADIO"`.
* **Dopasowanie do pasków nawigacyjnych:**
  * Wykorzystanie `WindowInsets` i `.navigationBarsPadding()` na ekranie głównym – kafelki slotów stacji nie wchodzą pod przyciski funkcyjne telefonu ani belkę gestów.

---

## 📂 Struktura Plików Projektu
```
g:/PROJEKTY_ANTY/Radio_Car/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml                  # Uprawnienia, appCategory="audio", APP_MUSIC, ForegroundService
│   │   ├── java/com/seweryn/radiocar/
│   │   │   ├── MainActivity.kt                  # Punkt startowy Compose, uprawnienia Bluetooth/Powiadomień, edgeToEdge
│   │   │   ├── util/
│   │   │   │   └── BluetoothDeviceTracker.kt    # Wykrywanie i estetyczne formatowanie podłączonych urządzeń BT
│   │   │   ├── data/
│   │   │   │   ├── model/Station.kt             # Model stacji (id, name, streamUrl, logoUrl, icon)
│   │   │   │   └── repository/
│   │   │   │       ├── StationRepository.kt     # Zapis SharedPreferences JSON, slot swap
│   │   │   │       └── RadioSearchRepository.kt # Integracja z Radio-Browser API
│   │   │   ├── service/
│   │   │   │   └── RadioPlayerService.kt        # ForwardingPlayer, AVRCP next/prev, ICY metadata, BMW metadata
│   │   │   ├── ui/
│   │   │   │   ├── RadioViewModel.kt            # MediaController, BT state, nawigacja, wyszukiwanie
│   │   │   │   ├── screens/RadioScreen.kt       # Główny ekran, navigationBarsPadding, dynamiczny nagłówek
│   │   │   │   ├── components/
│   │   │   │   │   ├── VinylCover.kt            # Płyta winylowa, gesty, poświata zielona/czerwona
│   │   │   │   │   ├── StationSlotsCarousel.kt  # Karuzela 10 gniazd (edycja przez long-click, czyste kafelki bez ołówków)
│   │   │   │   │   ├── EditStationDialog.kt     # Modal edycji, search online, zamiana
│   │   │   │   │   ├── ResetDefaultsConfirmationDialog.kt # Modal przywracania 10 stacji fabrycznych
│   │   │   │   │   └── GlassmorphicCard.kt      # Stylistyczny kontener szklany
│   │   │   │   └── theme/                       # Kolory (AccentGreen, AccentRed), typografia, motyw
├── gradle/
│   └── libs.versions.toml                       # Media3, Compose BOM, Coil, Kotlin Serialization
├── gradle.properties                            # Ścieżka do JDK 17 i konfiguracja cache
├── RadioCar-v1.0-debug.apk                       # Aktualny gotowy pakiet instalacyjny w katalogu głównym
└── PROJECT_KNOWLEDGE.md                         # Bieżąca wiedza i kompendium projektu
```
