# To dos

## 🔴 Kritisch (Priorität 0)
- [ ] Unit Tests hinzufügen (aktuell 0% Coverage für 84 Quelldateien)
- [ ] N+1 Query Problem in `TransactionService.calculateAllBalances()` beheben
- [ ] Custom Exception Hierarchy erstellen (`EntityNotFoundException`, `UnauthorizedOperationException`, `ValidationException`)

## 🟠 Hoch (Priorität 1)
- [ ] `UserService.registerUser()` und `authenticate()` optimieren (aktuell laden alle User in Memory)
- [ ] Null Safety in `FinanceMapper` verbessern (null-checks vor nested object access)
- [ ] StandingOrder Debtor-Daten normalisieren (aktuell JSON-String statt Relation)
- [ ] WG-Mitgliedschaft in Standing Order create/update validieren

## 🟡 Mittel (Priorität 2)
- [ ] EAGER zu LAZY Fetch Strategy ändern (alle Entity-Relationen)
- [ ] `CleaningScheduleService` in kleinere Services aufteilen; Clock/Time Provider injizieren
- [ ] `@Transactional` konsistent anwenden (class-level `readOnly=true`)
- [ ] Controller Workflow-Logik in bestehende Services verschieben

## Refactoring (in Progress)
- [x] Finance-Controller auf View DTOs umgestellt
- [x] Session Boundary mit Snapshot-IDs implementiert
- [x] `WG` Felder gekapselt (private + accessors, LAZY collections)
- [x] Transaction create/update validiert WG-Mitgliedschaft
- [ ] `FinanceMapper` pure machen (Repository-Zugriff/JSON-Parsing in Services)
- [ ] UI-Utilities zentralisieren (Dialogs, Currency Formatting, Navigation)

## Features
- [ ] WG verlassen mit negativem Balance blockieren
- [ ] Transactions: Wording für Single-Debtor verbessern
- [ ] Transaction History: Transaktionen ehemaliger Mitglieder anzeigen
- [ ] Notifications für Cleaning Schedule und Transaktionen

## UI/Style
- [ ] Duplicate Transaction History Header fixen
- [ ] Icons und Dialog-Styling verbessern

## Sicherheit
- [x] Password Hashing mit BCrypt implementiert
- [ ] `SecureRandom` statt `Random` für Invite Codes
- [ ] Admin-Override für Content-Moderation

## Testing
- [ ] Tests für Balances, Settlements, Cleaning Schedule Generation
- [ ] Integration Tests für kritische Workflows

## Extras
- [ ] Settings: Währungsauswahl
- [ ] Shopping List: Payment Link (optional)
