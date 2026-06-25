# NewBudget Agent Instructions

## Project Context
- This is a Java 21 Maven desktop app using JavaFX, SQLite, and OpenCSV.
- Primary goal: monthly budget tracking from CSV-imported actuals plus manually entered budgets.
- Keep the first user experience focused on one month at a time.

## Core Product Rules
- Spreadsheet columns must remain: Category, Actual Amt, Budget Amt, Difference, Balance.
- Difference is always `budget - actual`.
- Balance is running cumulative per category across months: cumulative budgets minus cumulative actuals up to the selected month.
- Income category type is fixed and cannot be reassigned.
- Mandatory and Discretionary category type changes are allowed.
- Category classification is persisted per category per month (month-scoped), not globally.

## Data and Persistence Rules
- Use SQLite file `data/budget.db`.
- Preserve and evolve existing schema with backward-compatible migrations when possible.
- Treat month keys as `yyyy-MM` consistently.
- `monthly_classifications` is month-scoped and authoritative for selected month rendering.
- CSV re-import for the same month should replace imported actuals for that month unless explicitly changed by user requirements.

## CSV Import Rules
- Parse month from the date range header in export files (e.g., `3/1/2026 through 3/31/2026`).
- Parse category hierarchy using indentation depth.
- Skip blank lines, section headers, and TOTAL rows.
- Preserve category hierarchy and sort order.
- Mark parent categories as rollups when they have children.

## UI and Interaction Rules
- Build UI layout and control instantiation with FXML files; Java code should use controllers for behavior rather than constructing the scene graph directly.
- Keep three sections in the month view: Income, Mandatory, Discretionary.
- Budget Amt is editable and persists immediately to SQLite.
- Provide category move actions only between Mandatory and Discretionary.
- Do not allow move actions for Income rows.
- After any budget/classification/import change, refresh the month snapshot from persistence.

## Coding Guidelines
- Prefer small services with clear responsibilities:
  - data package (`com.newbudget.data`): SQL and storage concerns only
  - service layer: aggregation and business rules
  - UI layer: view state and event handling
- Keep all direct database access (`java.sql`, SQL statements, connection handling) inside `com.newbudget.data` only.
- Avoid hardcoding month logic in UI; compute in service/repository.
- Keep JavaFX table row models immutable where possible, except editable budget property.
- Add focused unit tests when touching parser, month aggregation, or classification behavior.

## Validation Checklist
- `mvn test` passes.
- Importing `Mar2026.csv` populates actuals and hierarchy.
- Editing budget updates Difference and Balance correctly after reload.
- Moving a category between Mandatory and Discretionary affects only selected month.
- Loading a different month confirms classification remains independent per month.

## Safe Change Practices
- Preserve existing file and package layout unless a task requires structural change.
- Do not introduce unrelated refactors when implementing a feature fix.
- Keep dependency additions minimal and justified.
