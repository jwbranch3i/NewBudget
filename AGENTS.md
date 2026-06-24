# AGENTS.md

This file defines repository-level guidance for coding agents working on NewBudget.

## Purpose
- Build and maintain a JavaFX desktop budget tracker.
- Keep the primary workflow focused on one selected month at a time.
- Preserve correctness for month-scoped category classification and running balances.

## Tech Stack
- Java 21
- Maven
- JavaFX
- SQLite (`data/budget.db`)
- OpenCSV
- JUnit 5

## Product Invariants
- Required columns in the main table view:
  - Category
  - Actual Amt
  - Budget Amt
  - Difference
  - Balance
- Difference formula is always: `budget - actual`.
- Balance is cumulative by category through selected month: cumulative budgets minus cumulative actuals.
- Category classification rules:
  - Income is fixed and cannot be reassigned.
  - Mandatory and Discretionary are allowed to switch.
  - Classification is persisted per category per month (month-scoped).

## Data Rules
- Keep all direct database access in `com.newbudget.data` only.
- Do not place SQL, JDBC connection handling, or schema mutations in UI, service, or model packages.
- Use `yyyy-MM` for month keys.
- `monthly_classifications` is authoritative for classification in selected month.
- Re-importing CSV for an existing month replaces imported actuals for that month unless requirements explicitly change.
- Preserve schema compatibility when evolving persistence code.

## CSV Import Rules
- Parse month from header range text (example: `3/1/2026 through 3/31/2026`).
- Parse hierarchy from indentation depth.
- Skip blank rows, section headers, and TOTAL rows.
- Preserve hierarchy and display order.
- Mark parents as rollups when children exist.

## UI Rules
- Keep three month sections: Income, Mandatory, Discretionary.
- Budget Amt must be editable and persisted immediately.
- Provide move actions only between Mandatory and Discretionary.
- Do not allow move actions for Income rows.
- After import, budget edit, or type change, reload snapshot from persistence.

## Code Organization
- Keep clear boundaries:
  - Data package (`com.newbudget.data`): SQL and storage only.
  - Service layer: aggregation, calculations, domain rules.
  - UI layer: state/event wiring and presentation.
- Avoid embedding business logic directly in JavaFX controls where a service/repository can own it.
- Keep data and table-row models immutable where practical, except editable budget fields.

## Testing Expectations
- Run `mvn test` after meaningful changes.
- Add targeted tests when changing:
  - CSV parsing behavior
  - Month aggregation math
  - Classification persistence/switching
- At minimum, preserve tests that validate month-scoped classification behavior.

## Safe Change Policy
- Prefer focused, incremental changes.
- Avoid unrelated refactors in feature/fix work.
- Keep dependencies minimal and justified.
- Do not remove user-provided sample assets like `Mar2026.csv` or `Clipboard Image.jpg`.

## Useful Commands
- Run tests: `mvn test`
- Run app: `mvn javafx:run`
