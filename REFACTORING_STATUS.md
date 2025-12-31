# Refactoring Status

Scope: unbiased snapshot of structural debt and refactor priorities with focus on separation of responsibilities, eliminating redundancy, and aligning to the existing layered rules.

## Current Snapshot
- Layered architecture exists (JavaFX UI -> services -> repositories), but controllers still orchestrate workflows and shape data in multiple domains.
- Finance UI is largely on view DTOs; cleaning UI uses DTOs; other areas mostly rely on DTOs and summary snapshots instead of entities.
- Session boundary is improved (snapshot IDs + summary data), and controllers now use `UserSessionDTO` instead of `getCurrentUser()`.
- `WG` is now encapsulated (private fields + accessors) with LAZY collections and id-based `equals/hashCode`, reducing accidental state mutation.
- Mapping layers still mix access and parsing concerns (notably finance).
- Automated tests are still missing.

## Key Findings
### Architecture and Separation
- Finance controllers still contain dialog construction and UI-specific validation, and some filtering/permission logic remains in the UI.
- Services still mix persistence and view-mapping concerns; no dedicated facade layer exists, though more UI logic is delegated into services.
- Navigation/controller hand-offs are repeated (`applicationContext.getBean` after `loadScene`) rather than centralized.
- Session snapshot is used across core/cleaning/finance/shopping controllers, but WG entities are still passed around in some flows instead of IDs.

### Domain Model and Invariants
- `WG` fields are now encapsulated and collections are LAZY; invariants still need enforcement at service boundaries.
- Authentication remains ad-hoc: `UserService` scans all users with plaintext passwords for login/uniqueness.
- WG membership validation is present in `TransactionService` create/update, but standing order create/update still lacks explicit WG membership checks before persistence.

### Finance Domain
- `TransactionService` now validates WG membership and uses repository member lookup; netting/settlement is still implemented as multiple transactions without a dedicated abstraction.
- `FinanceMapper` performs repository access and JSON parsing inside the mapper and swallows parsing errors.

### Cleaning Domain
- `CleaningScheduleService` remains large and combines scheduling rules, queue maintenance, template handling, and DTO conversion.
- Time handling is still tied to `LocalDate.now()`, limiting testability.

### UI Layer and Redundancy
- Dialog construction, currency formatting, and navigation remain duplicated; encoding issues were partially addressed but not fully standardized.
- UI controllers are mostly DTO-based, but shared dialog/conversion logic is still embedded in controllers.

### Quality and Operations
- No automated tests detected (unit, integration, or UI).
- Global config uses `spring.jpa.hibernate.ddl-auto=update` without profile separation.

## Progress (current iteration)
- Session boundary tightened: `SessionManager` now stores only a snapshot (IDs + basic user/WG data) and provides refresh helpers.
- Core view models added (`UserSummaryDTO`, `WgSummaryDTO`) with a mapper to reduce direct entity exposure.
- Finance view DTOs added (`TransactionViewDTO`, `TransactionSplitViewDTO`, `BalanceViewDTO`) and adopted in finance controllers.
- `TransactionHistoryController` now consumes view DTOs and fetches history via `getTransactionsForUserView`.
- `TransactionsController` now uses `BalanceViewDTO` from `calculateAllBalancesView`.
- Standing order flows moved to view DTOs: services return `StandingOrderViewDTO`, and the dialog uses view-based create/update APIs.
- Transaction create/update now validates WG membership (creator/creditor/debtors) and avoids `wg.mitbewohner` access.
- Cleaning schedule UI uses DTOs and session snapshot IDs for loading tasks.
- Core household setup facade added (`HouseholdSetupService`) to keep core UI controllers from calling cleaning services directly.
- `WG` fields are now private with accessors, collections are LAZY, and equality is id-based.
- Member list retrieval moved into domain services; finance/cleaning/shopping controllers now call their own services instead of `WGService`.
- Finance balance/credit filtering moved into `TransactionService` for UI reuse.

## Follow-ups Needed
- Continue moving controller workflow logic into existing services (finance, cleaning, shopping), no new facade classes.
- Split `CleaningScheduleService` into focused services and inject a clock/time provider.
- Audit LAZY collection access boundaries and ensure reads happen inside service transactions.
- Validate WG membership in standing order create/update flows (creditor/debtors) before saving.
- Refactor `FinanceMapper` to remove repository access/JSON parsing and report parsing errors.
- Harden authentication (hashing, repository queries, input validation).
- Centralize shared UI utilities (dialogs, currency formatting, navigation) and lock encoding settings.
- Add tests around balances, settlements, cleaning task generation/rotation, and membership rules.
