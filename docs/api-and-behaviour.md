# API and behaviour reference

Technical details for the current local, single-user application. For project overview and setup,
see the [README](../README.md).

## Reimbursements and insights

Select an expense and choose Reimbursements to record money actually received back. Partial
repayments and several reimbursements are supported. Amounts must use the purchase currency;
received date cannot be before the purchase or in the future. The total cannot exceed the original
expense. The ledger shows Original, Reimbursed and Net cost; the headline now shows net spending.
Remove a reimbursement to correct a mistake. An expense with reimbursements cannot be deleted,
changed to another currency, reduced below its reimbursed amount, or moved past a received date.

Reports use purchase-based accounting: an October reimbursement of a September purchase reduces
September's net spending. This is not an account balance or a cash-flow report. Converted totals
convert the remaining net cost at the purchase-date rate, preserving original records.

Spending insights opens a per-currency view with category shares, daily/monthly trends, daily
average, and change versus the immediately preceding equal-length interval. The default is this
month through today; custom intervals are limited to 366 days. Categories and trends use net
spending and calendar days include days with no purchases. Zero prior spending has no percentage
comparison. Insights use original currencies even when the headline is set to converted mode.
There are no budget judgments or AI-generated claims.

## API

| Method | Path | Result |
|---|---|---|
| POST | `/api/expenses` | Create, 201 + Location |
| GET | `/api/expenses/{id}` | Read, 200 or 404 |
| GET | `/api/expenses?page=0&size=25` | Paginated list |
| PUT | `/api/expenses/{id}` | Replace editable fields |
| DELETE | `/api/expenses/{id}` | Delete, 204 or 404 |
| GET | `/api/expenses/totals` | Totals grouped by currency |
| GET | `/api/expenses/converted-total?currency=GBP` | Purchase-date conversion with rate provenance and completeness |
| POST / GET | `/api/expenses/{id}/reimbursements` | Record / list received reimbursements |
| GET / DELETE | `/api/expenses/{id}/reimbursements/{reimbursementId}` | Read / remove a reimbursement |
| GET | `/api/insights/spending?from=2026-09-01&to=2026-09-30` | Per-currency purchase-based net spending insights |

List and totals accept `category`, `from`, `to` filters. Dates are inclusive; categories match
exactly ignoring case. Pages are zero-based; size is 1–100. Sort: spentOn descending, id ascending.
Expense responses add `reimbursedAmount` and `netAmount`. Currency totals' `amount` now means net
spending, alongside `grossAmount` and `reimbursedAmount`. Reimbursement create example:

```json
{"amount":40.00,"currency":"GBP","receivedOn":"2026-10-01","note":"Dinner repayment"}
```

Over-reimbursement and incompatible expense edits return 409 Conflict. Parent-row locks serialize
concurrent reimbursement and expense writes. The database enforces expense/currency linkage.

```json
{"amount":2500.00,"currency":"KZT","category":"Food","description":"Lunch","spentOn":"2026-09-25"}
```

Create/replace accepts positive money with <=2 decimals, a supported currency and category,
optional description <=500, and a purchase date no later than the server's today.
Category/currency values come from `ExpenseOptions`; API validation enforces the same catalogue
as the dropdowns. Existing legacy categories are preserved; reclassify them when editing.
The supported currencies are KZT, GBP, USD, EUR, CHF, CAD, AUD, NZD, CNY, INR, AED, TRY, SGD, HKD,
SEK, NOK, DKK, PLN, CZK and ZAR. They all use the existing two-decimal amount model.
Errors use Problem Details JSON, with an `errors` array for field validation.

## Currency totals

Separate currencies is the default. The Show totals control switches to Purchase-date conversion;
choose a display currency beside it. The choice is saved locally for future launches. Changing
the display never changes stored expense amounts or currencies.

Historical conversion uses [Frankfurter v2](https://frankfurter.dev/). Only currency pairs and dates
are sent to the provider; amounts, categories, descriptions and user identifiers are not sent.
Successful observations are stored in `exchange_rates`, including the purchase date, actual rate
date, rate and source. Later calculations reuse that snapshot. There is no latest-rate fallback.
If a daily rate is not published, a previous observation up to seven days earlier is accepted and
its actual date is exposed in `ratesUsed`. Each expense is converted and rounded to two decimals
with HALF_EVEN, then summed. Reference estimates exclude bank fees and card-provider spreads.

On missing/offline rates, the response has `complete=false`, `amount=null`, and `missingRates`.
The UI shows original separate totals with a notice, never a partial combined amount.
Rate lookup has a bounded network budget; for large uncached histories, Refresh continues filling
the cache. Already cached rates and same-currency totals work without the provider. Override
`atlas.fx.base-url` only for a trusted compatible provider or local test fixture.
