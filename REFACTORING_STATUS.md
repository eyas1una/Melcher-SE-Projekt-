# Refactoring Status

**Letzte Aktualisierung:** 2025-12-31

## Aktuelle Übersicht

### Architektur
| Bereich              | Status | Details                                   |
| -------------------- | ------ | ----------------------------------------- |
| Layered Architecture | ✅      | JavaFX UI → Services → Repositories       |
| DTO-basierte API     | ✅      | Finance/Cleaning/Core auf DTOs umgestellt |
| Session Management   | ✅      | Snapshot-IDs statt Entity-Referenzen      |
| Entity Encapsulation | ✅      | `WG` mit private fields, LAZY collections |
| Test Coverage        | ❌      | **0% - Keine Unit Tests vorhanden**       |

### Kritische Probleme (aus Codebase-Scan 2025-12-31)

#### 🔴 Kritisch
1. **Keine Unit Tests** - 84 Quelldateien ohne Test-Coverage
2. **N+1 Query Problem** - `TransactionService.calculateAllBalances()` führt O(n) DB-Aufrufe pro Member aus
3. **60+ RuntimeException** - Keine custom Exception-Hierarchy

#### 🟠 Hoch
4. **UserService Performance** - `registerUser()` und `authenticate()` laden alle User in Memory
5. **EAGER Fetch Overuse** - Alle Entity-Relationen EAGER, lädt unnötig viele Daten
6. **Null Safety** - `FinanceMapper.toDTO()` prüft nested objects nicht auf null
7. **JSON statt Relation** - StandingOrder speichert Debtor-Daten als JSON-String

#### 🟡 Mittel
8. **Inkonsistente @Transactional** - Einige Query-Methoden haben Annotation, andere nicht
9. **Thread Safety** - `SessionManager` Singleton mit mutable state ohne Synchronization
10. **Entity equals()** - `WG.equals()` basiert nur auf ID (null für unpersisted entities)

---

## Abgeschlossene Refactorings

### Session & DTOs ✅
- `SessionManager` speichert nur Snapshot (IDs + basic data)
- Core View Models: `UserSummaryDTO`, `WgSummaryDTO`, `UserSessionDTO`
- Finance View DTOs: `TransactionViewDTO`, `BalanceViewDTO`, `StandingOrderViewDTO`
- Cleaning DTOs: `CleaningTaskDTO`, `CleaningTaskTemplateDTO`, `RoomDTO`

### Finance Domain ✅
- `TransactionService` validiert WG-Mitgliedschaft (creator/creditor/debtors)
- `TransactionHistoryController` konsumiert View DTOs
- `TransactionsController` nutzt `BalanceViewDTO`
- Standing Order Flows auf View DTOs umgestellt

### Cleaning Domain ✅
- Cleaning Schedule UI nutzt DTOs und Session Snapshot IDs
- `CleaningScheduleService` delegiert an fokussierte Sub-Services

### Core Domain ✅
- `WG` Felder private mit Accessors
- Collections LAZY, id-based `equals/hashCode`
- Member-Listen werden über Domain Services abgerufen

---

## Nächste Schritte (Priorität)

### P0 - Kritisch
```
[ ] Unit Tests hinzufügen (Services zuerst)
[ ] N+1 Query in calculateAllBalances() fixen
[ ] Custom Exceptions erstellen
```

### P1 - Hoch
```
[ ] UserService Email-Query optimieren (existsByEmail)
[ ] UserService Authentication Query optimieren
[ ] FinanceMapper null safety
[ ] StandingOrder Debtor-Daten normalisieren
```

### P2 - Mittel
```
[ ] EAGER → LAZY Fetch Strategy
[ ] CleaningScheduleService aufteilen
[ ] Clock/Time Provider injizieren
[ ] @Transactional konsistent anwenden
```

### P3 - Niedrig
```
[ ] Mixed Naming (German/English) bereinigen
[ ] ObjectMapper als Bean injizieren
[ ] Structured Logging hinzufügen
```

---

## Bekannte technische Schulden

| Schuld             | Risiko | Aufwand | Empfehlung                 |
| ------------------ | ------ | ------- | -------------------------- |
| Keine Tests        | Hoch   | Hoch    | Sofort beginnen            |
| N+1 Queries        | Hoch   | Mittel  | Batch-Query implementieren |
| Generic Exceptions | Mittel | Mittel  | Exception Hierarchy        |
| EAGER Fetching     | Mittel | Mittel  | Schrittweise umstellen     |
| JSON Debtor Data   | Mittel | Hoch    | Normalisieren wenn Zeit    |

---

## Sicherheitsaspekte

| Check                     | Status                            |
| ------------------------- | --------------------------------- |
| Password Hashing (BCrypt) | ✅                                 |
| Email Uniqueness          | ⚠️ Ineffizient (lädt alle User)    |
| WG Membership Validation  | ✅ Transactions, ❌ Standing Orders |
| Invite Code Generation    | ⚠️ `Random` statt `SecureRandom`   |
| Input Validation          | ⚠️ Keine Obergrenze für Beträge    |

---

*Siehe [ERROR_REPORT.md](./ERROR_REPORT.md) für vollständige Details aller gefundenen Probleme.*
