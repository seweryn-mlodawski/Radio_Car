# Session Workflow & Automatic Git Synchronization Rule

Ta reguła definiuje sztywne procedury zachowania agenta na początku sesji, na końcu sesji oraz przy rejestrowaniu zadań TODO.

## 1. Zakończenie sesji (komunikat typu "kończymy sesję", "koniec na dziś", "na dziś koniec sesji", "koniec sesji", itp.)
Gdy użytkownik komunikuje w dowolny sposób zakończenie sesji:
Agent **BEZWZGLĘDNIE, PROAKTYWNIE I AUTOMATYCZNIE** (bez czekania na osobną prośbę):
1. **Zapis lokalny & Git Status:** Sprawdza stan plików w repozytorium (`git status`).
2. **Baza wiedzy:** Uaktualnia plik `PROJECT_KNOWLEDGE.md` i/lub `TODO.md` o najważniejsze ustalenia, nowe funkcjonalności i stan projektu.
3. **Staging & Commit:**
   - Dodaje zmiany do indeksu Gita (`git add -A`).
   - Tworzy commit z czytelnym i opisowym komunikatem (np. `feat: ...`, `fix: ...`).
4. **Git Push:**
   - Wypycha zmiany na GitHub (`git push origin <branch>`).
5. **Potwierdzenie:** Informuje użytkownika o pomyślnym wypchnięciu commitów na GitHub.

## 2. Rozpoczęcie nowej sesji (komunikat typu "zaczynamy sesję", nowy czat, "cześć", "start")
Gdy rozpoczyna się nowa sesja lub użytkownik wita się / prosi o start pracy:
1. **Pliki informacyjne projektu:** Odczytaj plik bazy wiedzy projektu (`PROJECT_KNOWLEDGE.md`, `README.md`).
2. **Historia pracy:** Sprawdź historię ostatnich zmian w kodzie (`git log -n 5 --oneline`).
3. **Plik zadań:** Odczytaj plik `TODO.md`, aby zidentyfikować zaplanowane zadania i priorytety.
4. **Gotowość:** Przedstaw zwięzłe podsumowanie stanu startowego i zapytaj o priorytet na bieżącą sesję.
