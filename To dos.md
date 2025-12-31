# To dos

## Refactoring (next)
- Continue moving controller workflow logic into existing services (finance/cleaning/shopping), no new facade classes.
- Split `CleaningScheduleService` into smaller services; inject a clock/time provider.
- Make `FinanceMapper` pure (move repo access + JSON parsing into services, surface parse errors).
- Validate WG membership in standing order create/update flows (creditor/debtors) before saving.
- Centralize UI utilities (dialogs, currency formatting, navigation) and lock encoding settings.

## Features
- Leaving WG: block leaving with negative balance; allow leaving with positive balance but warn.
- Transactions: improve wording for single-debtor case (e.g., "Daniel paid for [User]").
- Transaction history: include transactions of former members.
- Notifications for cleaning schedule and transactions.

## UI/Style
- Fix duplicate Transaction History header.
- Improve icons and dialog window styling.

## Data/Security
- Password hashing/encryption.
- Access handling and validation.

## Testing
- Add tests for balances, settlements, and cleaning schedule generation/rotation.

## Extras
- Settings: currency selection.
- Shopping list: payment link (optional).
