# Next phase: iPhone transaction capture

The product goal is to capture spending with consent, without a separate integration for every
bank. Mac-side capture/review is now implemented. Physical iPhone capture is not yet configured
or tested. No native iPhone app or signed Shortcut is included.

## Implemented local-first prototype

The user's iPhone runs iOS 26.6.1. Start with the selected-card Wallet Transaction automation,
not arbitrary bank notifications. The notification trigger described below belongs to iOS 27.
Inspect the real Wallet input before mapping its properties; a card tap is not proof of settlement.

Flow: iPhone Shortcut → private iCloud folder → FolderCaptureBridge → CaptureController →
CaptureService → either a review record or an Expense. The bundled guide is
`src/main/resources/iphone-setup.txt`, also available in iPhone inbox → Setup instructions.

The bridge reads stable top-level JSON files, bounded to 64 KiB, without modifying the folder.
The API stays loopback-only. The service owns parsing decisions, duplicate checks, optional
source-specific merchant learning and atomic expense creation; reports only see real expenses.
Unknown merchants or ambiguous transaction fields require explicit review.

V5 adds capture and merchant-rule tables. PostgreSQL serializes capture/approval transactions
with a table lock for this single-user prototype. Stable event IDs and payload hashes prevent
replay; matching expense amount/currency/day requires explicit duplicate override. Manual expense
creation is not serialized with capture, so simultaneous manual/import duplicates are not fully
prevented. This is not bank reconciliation. Source writers are trusted, not verified bank issuers.

POST `/api/captures` accepts CaptureRequest; GET lists the first 100 unresolved records. POST
`/api/captures/{id}/approve` accepts `{expense: ExpenseRequest, merchant, rememberCategory,
allowPossibleDuplicate}`; POST `/{id}/dismiss` resolves without expenses. These APIs have the same
local-only, unauthenticated security boundary as existing CRUD. Never expose them publicly.

Capture date uses the supplied capture timestamp and IANA time zone, not Mac arrival time.
Raw notification bodies are cleared after resolution, but metadata and retry hashes remain.
iCloud files remain untouched; their retention is user-managed. Disconnect keeps existing data.
No refund automatically becomes a reimbursement. No source has been enabled on the user's phone.

## What is verified

Apple's iOS 27 Shortcuts guide describes notification-based automation triggers:
https://support.apple.com/en-om/guide/shortcuts/apdfbdbd7123/10.0/ios/27

Apple also documents Wallet transaction triggers for selected cards:
https://support.apple.com/en-mide/guide/shortcuts/apd65c67538a/ios

These are user-configured Shortcuts capabilities, not a promise that Atlas can independently read
every other app's notification history. Trigger availability and transaction fields need a real
iPhone test on the target iOS version, with representative bank notifications. Do not advertise
"all banks" until bank/locale templates and missing/hidden content have been tested.

FinanceKit is another route for supported financial data, with managed entitlement/eligibility
requirements; it is not universal notification access:
https://developer.apple.com/documentation/financekit

## Remaining production sequence (not required for the local prototype)

1. Finish accounts and a hosted authenticated HTTPS API (the current local loopback API cannot
   receive events from an iPhone). Define consent, ownership, retention and deletion.
2. Prototype an iOS App Intent accepting structured amount, currency, date, merchant and source.
   Configure a user-controlled Wallet/notification Shortcut on a physical phone.
3. First stage captured transactions in an import inbox. Require confirmation of uncertain parsing,
   category, transaction versus transfer, and refund versus income. Preserve notification text
   only if needed and explicitly opted into; redact sensitive account/card identifiers.
4. Give each source event a stable idempotency key. Retries must not create duplicate expenses.
   Also flag likely duplicates across Wallet and bank notifications for review.
5. Confirm authorization versus posted amount, declined transactions, reversals, foreign-currency
   charges, bank fees, timezone boundaries and delayed notifications. Reimbursements must be
   explicitly matched to an original expense; never treat every incoming payment as reimbursement.
6. Test one bank/locale end to end, then add templates and coverage evidence. Bank feeds and CSV
   statement imports remain alternatives for missing notifications and reconciliation.

The iCloud transport is an explicitly user-selected alternative to a public ingestion endpoint.
A real-phone test, verified Shortcut export, reconciliation and account/device ownership remain
future gates. Do not advertise all-bank coverage.
