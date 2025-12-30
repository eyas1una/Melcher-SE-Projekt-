# Refactoring Status

Scope: unbiased snapshot of structural debt and refactor priorities with focus on separation of responsibilities, eliminating redundancy, and aligning to the existing layered rules.

## Current Snapshot
- Layers exist (JavaFX UI -> services -> repositories), but controllers still orchestrate business flows and data shaping.
- DTO mappers are present, yet services often return entities to the UI and mix mapping with persistence, blurring boundaries.
- Domain entities expose mutable state directly (e.g., WG fields), making invariants hard to enforce. No automated tests were found.

## Key Findings
### Architecture and Separation
- Finance UI controllers (`src/main/java/com/group_2/ui/finance/TransactionsController.java`, `TransactionHistoryController.java`) are very large and include business workflows (settlements, credit transfers, validation, filtering) that belong in services/facades. They also construct dialogs and perform data shaping inline, creating redundancy.
- Services mix concerns: `src/main/java/com/group_2/service/finance/TransactionService.java` and `src/main/java/com/group_2/service/shopping/ShoppingListService.java` both handle persistence and DTO assembly. There is no clear application/service layer boundary versus presentation adapters.
- `src/main/java/com/group_2/util/SessionManager.java` stores the full `User` entity, leading to stale state, hidden lazy-loading risks, and tight coupling of UI to JPA. Controllers manually refresh it in places instead of using a centralized session view model.
- Navigation and controller hand-offs are repeated (`applicationContext.getBean` after `loadScene` in multiple controllers) instead of a central navigation/helper, increasing coupling between controllers.

### Domain Model and Invariants
- `src/main/java/com/group_2/model/WG.java` exposes public fields (`name`, `rooms`, `mitbewohner`) and uses EAGER collections; services mutate fields directly (`WGService`), bypassing invariants. Entities lack `equals/hashCode`, making list operations unreliable.
- Authentication is ad-hoc and insecure: `src/main/java/com/group_2/service/core/UserService.java` scans all users with plaintext passwords for login and duplicate checks. No hashing, dedicated queries, or validation exist.
- Finance and cleaning services rely on direct access to `wg.mitbewohner` and other public collections, so membership checks are implicit or absent.

### Finance Domain
- `src/main/java/com/group_2/service/finance/TransactionService.java` does not validate that creditor/debtors belong to the same WG and performs repeated repository lookups per debtor (N+1). Permission checks rely on controller-side logic.
- Settlement/credit-transfer flows are implemented in `TransactionsController` as multiple transaction creations rather than an atomic, validated domain operation. There is no shared abstraction for “settlement” versus “expense”, risking inconsistent balances.
- `src/main/java/com/group_2/dto/finance/FinanceMapper.java` performs repository access and JSON parsing inside the mapper, mixing data access, mapping, and parsing concerns; parse errors are swallowed.

### Cleaning Domain
- `src/main/java/com/group_2/service/cleaning/CleaningScheduleService.java` is very large and handles scheduling rules, queue maintenance, template management, membership sync, and DTO conversion in one class. This makes it hard to test, reason about, or extend. Time handling is hard-coded to `LocalDate.now()`, hindering deterministic testing.
- Controllers (`CleaningScheduleController`, `TemplateEditorController`) still pull entities/SessionManager data directly and rebuild UI state themselves, rather than consuming small view models.

### UI Layer and Redundancy
- Dialog construction, currency formatting, and icon/text handling are repeated across finance controllers; there is no shared dialog/formatter component. Several files show garbled glyphs (currency/icons) due to encoding issues.
- UI relies on JPA entities from `SessionManager` instead of DTOs/view models, tying the view to persistence models and complicating caching/refresh.

### Quality and Operations
- No automated tests detected (unit, integration, or UI), so changes to scheduling/balances are unguarded.
- Global config uses `spring.jpa.hibernate.ddl-auto=update` without profile separation, which is risky beyond local development.

## Prioritized Refactors
1) Introduce clear application facades per domain (finance, cleaning, shopping) that the UI calls; move settlement, credit-transfer, and filtering logic out of controllers into services with explicit commands/queries.
2) Encapsulate domain models (private fields, accessors, invariants) and enforce WG membership checks in finance/cleaning operations. Replace direct `wg.mitbewohner` access with validated methods; add lazy fetch where appropriate.
3) Split `CleaningScheduleService` into smaller services (template management, queue/assignment engine, calendar/query service) with injected clock/time providers for testability.
4) Create a finance service for settlements vs. expenses with atomic transactional methods; consolidate DTO creation/mapping there to avoid controller duplication.
5) Introduce a session/view-model boundary: store only user/session IDs, fetch fresh data per use, and have controllers consume DTOs instead of entities.
6) Harden authentication: hashed passwords, repository-level queries for login and uniqueness, validation of inputs, and removal of plaintext comparisons.
7) Centralize shared UI utilities (dialogs, currency formatting, navigation) to remove duplicated UI glue code and fix encoding issues in finance/cleaning controllers and FXML.
8) Add tests around critical flows (balance calculations, settlement commands, cleaning task generation/rotation, sharing logic) using mocks or an in-memory DB to lock behavior before further refactors.

## Progress (current iteration)
- Session boundary tightened: `SessionManager` now stores only a lightweight snapshot (IDs + basic name/WG info) and fetches fresh entities on demand. Compatibility helpers remain to avoid breaking controllers.
- Finance UI controllers (`TransactionsController`, `TransactionHistoryController`) now rely on session IDs for balance/history flows instead of holding onto cached `User` entities; interactions fetch fresh data per use.
- Introduced core view models (`UserSummaryDTO`, `WgSummaryDTO`) and mappers plus finance view DTOs (`TransactionViewDTO`, `TransactionSplitViewDTO`, `BalanceViewDTO`) alongside mapper support, ready for controllers/services to consume without exposing entities.
- Finance controllers are being moved to the new view DTOs: `TransactionHistoryController` now consumes view DTOs and the history fetch uses `getTransactionsForUserView`; `TransactionsController` uses `BalanceViewDTO`.
- Began pulling settlement logic into services: `TransactionService` now exposes `settleBalance` and `transferCredit`; `TransactionsController` delegates to these service methods.
- Standing order dialog now uses session IDs for action permissions and WG selection, calling `StandingOrderService.getActiveStandingOrdersDTO(wgId)` instead of passing entities.
- Standing orders moved toward view DTOs: service provides `getActiveStandingOrdersView`, mapper builds nested user summaries, and `StandingOrdersDialogController` consumes the view DTOs, relying solely on IDs for permissions and WG lookup.
- Standing order creation now respects provided WG IDs in the service (fallback to creator WG with validation).
- Added view-returning standing order mutations (`createStandingOrderView`, `updateStandingOrderView`) and the dialog now calls these, keeping UI fully on view DTOs.
- Encoding clean-up started: `TransactionHistoryController` uses ASCII-safe currency labels and button text (removed garbled symbols).
- Next up: introduce shared DTO/view-model definitions for the UI layer and route controllers through them (reducing direct entity exposure), then move settlement/filter logic into dedicated services.

## Follow-ups Needed
- Decide target encoding (UTF-8) and update IDE/settings to stop producing garbled glyphs in controllers/FXML.
- Agree on the DTO/view-model contract for the UI so services can stop returning entities.
