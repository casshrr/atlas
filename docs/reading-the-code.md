# Reading Atlas

Start with one feature, not the whole project. There are no extra service interfaces, event buses,
or generic base classes to learn. Java paths below are relative to src/main/java/com/example/atlas/.

## Follow a manually added expense

1. `desktop/ExpenseEditorDialog.java` collects a draft and checks input for quick feedback.
2. `desktop/DesktopApplication.java` sends it through `AtlasApiClient`.
3. `expense/ExpenseController.java` receives and validates the HTTP request.
4. `expense/ExpenseService.java` performs the operation inside a transaction.
5. `expense/ExpenseRepository.java` reads and writes `Expense` records.

The backend remains authoritative: desktop validation does not replace API validation.

## Desktop files and their jobs

| File | Responsibility |
| --- | --- |
| DesktopApplication | Connect panels, navigate and fetch data off the UI thread |
| ExpenseFiltersPanel | Category/date drafts, presets and filter query |
| ExpenseHistoryPanel | Table rows, selection, empty state and table actions |
| ExpenseEditorDialog | Add/edit form and draft validation |
| SpendingSummaryPanel | Net totals, conversion fallback and saved settings |
| AtlasApiClient | HTTP/JSON; ExpenseOverview names the three overview responses |
| DesktopStyle | Shared appearance, dropdown presentation and money formatting |
| FolderCaptureBridge | Read iCloud files, retry failures and deliver captures |
| PhoneInboxDialog | Review captures and explain phone setup |
| ReimbursementsDialog | Record/remove received repayments |
| SpendingInsightsDialog | Present category totals and spending trends |
| DesktopLauncher / DesktopBackend | Startup window and owned backend lifecycle |

A panel does not access the database. The extracted panels do not call the API either: they
display data and notify the main window about actions. Existing dialogs retain their API workflows.

## Follow an imported payment

`FolderCaptureBridge.scan()` finds eligible files; `importFile()` checks stability; `readCapture()`
checks size and JSON. The bridge does not decide whether money is an expense.

`CaptureController` validates requests. `CaptureService.capture()` then:

- locks capture transactions;
- checks the event ID and `CaptureFingerprint` for retries;
- asks `NotificationParser` for possible values and uncertainty reasons;
- looks up saved rules through `MerchantCategoryRepository`;
- keeps a review record or creates an expense atomically.

`approve()` and `dismiss()` are separate user actions. Fingerprint encoding, status names, API
contracts, database migrations and reporting rules have not changed in this cleanup.

## Where to make a change

- Headline only: `SpendingSummaryPanel`.
- Filter controls: `ExpenseFiltersPanel`; the main window still applies the query.
- New expense field: request/entity/migration, then form and table.
- Bank text template: `NotificationParser`, plus capture tests.
- Automatic import decisions: `CaptureService`, plus capture tests.

Tests are under src/test/java/com/example/atlas/. `ExpensePanelsTests` exercises the extracted
panels on Swing's UI thread. Existing API, concurrency, reimbursement, insights, conversion and
file-import tests protect backend behavior.
