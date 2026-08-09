# Projekt: Radio Samochodowe (Native Android / Kotlin)

Projekt zakłada stworzenie od zera nowej, w 100% natywnej aplikacji na system Android, używającej języka Kotlin. Aplikacja będzie odtwarzać strumienie radiowe (w tle) i wizualnie nawiązywać do projektu "Radio Dell" (Glassmorphism). Głównym celem jest bezproblemowe działanie w samochodzie (wsparcie dla sterowania z kierownicy i wyświetlania piosenek na ekranie samochodu).

## Zależności i Architektura
1. **Język:** Kotlin
2. **Interfejs (UI):** Jetpack Compose (pozwala na szybkie tworzenie nowoczesnych interfejsów, w tym rozmycia tła i gradientów w stylu Glassmorphism).
3. **Odtwarzanie Audio:** `androidx.media3:media3-exoplayer`
4. **Praca w tle:** `MediaLibraryService` / `MediaSession` – niezbędne, aby Android nie ubijał aplikacji po zgaszeniu ekranu i aby przesyłała metadane (tytuły) do samochodu.
5. **Odczyt tytułów (ICY Metadata):** ExoPlayer ma wbudowane zdarzenie `onMetadata`, które pozwala w czasie rzeczywistym "wyciągać" tytuły aktualnie granych piosenek bezpośrednio ze strumienia Shoutcast/Icecast (np. z serwerów RMF).

## Ustalenia z Sesji Wstępnej (2026-08-09)
1. Zrezygnujemy z przycisku PAUSE (tak jak na Dellu), zostawiamy PLAY.
2. Zamiast przycisku Reload, zrealizujemy odświeżanie strumienia poprzez pociągnięcie kręcącego się logo w dół/górę (gest).
3. Pakiet aplikacji: `com.seweryn.radiocar`
4. Aplikacja pozbawiona usypiacza i timera.

## Proponowane Główne Komponenty

---

### [Konfiguracja Projektu]
#### `app/build.gradle.kts`
Dodanie zależności do Media3 (ExoPlayer), Jetpack Compose oraz uprawnień do internetu i pracy w tle (Foreground Service).
#### `AndroidManifest.xml`
Dodanie niezbędnych uprawnień: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `BLUETOOTH`.

### [Logika Odtwarzacza (Backend Aplikacji)]
#### `RadioPlayerService.kt`
Usługa działająca w tle, rozszerzająca `MediaSessionService`. Będzie trzymać instancję ExoPlayera, zarządzać playlistą (naszymi stacjami) i wystawiać tzw. "Sesję Mediów", która rozmawia przez Bluetooth z radiem w samochodzie.
#### `MetadataListener.kt`
Komponent podpięty pod odtwarzacz, wyłapujący w locie tagi ICY (nazwa piosenki, wykonawca) i podający je dalej do interfejsu i samochodu.

### [Interfejs Użytkownika (Frontend)]
#### `MainActivity.kt`
Główny punkt wejścia. Obsługa uprawnień i połączenie z usługą odtwarzacza w tle (`MediaController`).
#### `RadioScreen.kt`
Główny widok (Jetpack Compose) wzorowany na "Radio Dell". Znajdzie się tu m.in.:
- Rozmyte, dynamiczne tło.
- Duże, kręcące się logo stacji (reagujące na gest swipe góra/dół do odświeżania).
- Przyciski (Play, Następna, Poprzednia).
- Karuzela / lista stacji.
- Wyświetlanie aktualnego wykonawcy (z metadanych ICY).

---
*Sesja zakończona na utworzeniu lokalnego katalogu i repozytorium gita. Plan wdrożenia został zaakceptowany i umieszczony jako wiedza początkowa projektu.*
