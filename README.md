# Atlas

**A personal expense tracker built with Java, Spring Boot and PostgreSQL, with a downloadable desktop interface.**

Atlas helps users record expenses, understand spending across currencies, and track reimbursements
without losing the original purchase amounts. The project focuses on readable application design,
transactional correctness and testable financial calculations.

> **Work in progress:** Atlas is under active development and is not a production-ready release.
> This is a single-user desktop portfolio project; features, interface and architecture may change.
> Core expense tracking is implemented. iPhone capture remains an experimental testing prototype,
> not a released bank or Wallet integration.

## Features

- **Expense management:** create, edit and delete expenses with standardised categories, currency
  selection, calendar pickers, date presets and paginated history.
- **Multi-currency spending:** separate currency totals by default, with optional historical
  conversion using purchase-date exchange rates.
- **Reimbursements:** record partial or full repayments while preserving the original expense and
  preventing repayment totals from exceeding it.
- **Spending insights:** category breakdowns, daily/monthly trends and comparisons with the previous
  equal-length period, calculated separately for each currency.
- **Desktop experience:** a light, Apple-inspired Swing interface that starts and stops its own
  local Spring Boot backend. PostgreSQL remains a separate prerequisite.

## Engineering highlights

- **Money handling:** `BigDecimal` and PostgreSQL `NUMERIC(19,2)` avoid floating-point amounts.
- **Concurrent-write protection:** database row locks serialize reimbursement and related expense
  changes, including checks against over-reimbursement.
- **Repeatable reporting:** historical exchange-rate observations are cached with their dates and
  source. Incomplete conversion falls back to separate totals instead of presenting a partial sum.
- **Clear boundaries:** controllers handle HTTP, services own business rules and transactions,
  repositories handle persistence, and focused desktop components own presentation.
- **Schema and validation:** versioned Flyway migrations, Hibernate schema validation, shared
  category/currency catalogues and structured API errors.
- **Verification:** 40 automated tests currently pass locally, including PostgreSQL integration,
  concurrency, API-client, calculation and desktop-component checks.

## Technology

| Area | Stack |
| --- | --- |
| Backend | Java 17 target, Spring Boot 4.1.1, Spring Web MVC, Jakarta Validation |
| Persistence | PostgreSQL, Spring Data JPA / Hibernate, Flyway |
| Desktop | Java Swing, background API requests, `jpackage` macOS bundle |
| Testing | JUnit, Spring Boot Test, MockMvc, isolated PostgreSQL test database |
| Build and CI | Maven Wrapper; GitHub Actions workflow configured for Java 17 and PostgreSQL 17 |

## Architecture

The desktop client communicates with the API over HTTP; it never accesses the database directly.
Opening the app starts an owned backend on an available loopback port. Closing the app stops that
backend without interfering with separately running servers.

Source packages are organised by feature:

- `expense`: expense CRUD, validation, filtering and totals.
- `reimbursement`: received repayments and consistency checks.
- `insights`: category and time-series reporting.
- `fx`: historical rate lookup, caching and conversion.
- `desktop`: application lifecycle, API adapter, panels and dialogs.
- `capture`: experimental import parsing, review and duplicate protection.

Start with [Reading Atlas](docs/reading-the-code.md) for a walkthrough of the code,
or [Design decisions](docs/design.md) for the reasoning behind the architecture.

## Run locally

Requires **JDK 17+** and a running PostgreSQL instance. Create an `atlas` database and set the
connection details for your own local PostgreSQL user:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/atlas
export DB_USER=your_postgres_user
export DB_PASSWORD='your_postgres_password'

./mvnw -Pdesktop -DskipTests package
java -Djava.awt.headless=false -jar target/atlas-desktop.jar
```

Flyway applies the schema migrations on startup. No separate backend command is needed for the
desktop app. For API-only development, use `./mvnw spring-boot:run`; the default address is
`http://127.0.0.1:8080`. `ATLAS_API_URL` optionally connects the desktop to an existing backend.

### Build the macOS app

```bash
bash scripts/package-macos.sh
```

This produces `dist/Atlas.app` and `dist/Atlas-macos.zip`, including a Java runtime and backend.
PostgreSQL is not bundled. The build targets the machine's OS/architecture and is locally ad-hoc
signed, not Developer ID signed or notarised. Generated bundles are excluded from Git.

## Tests

Create a **separate** `atlas_test` database, then configure its connection:

```bash
export TEST_DB_URL=jdbc:postgresql://localhost:5432/atlas_test
export TEST_DB_USER=your_postgres_user
export TEST_DB_PASSWORD='your_postgres_password'
./mvnw test
```

The suite covers expense CRUD and validation, filtering and pagination, reimbursement limits
under concurrent writes, insights, historical conversion and missing-rate behaviour, backend
lifecycle, desktop panels and experimental import safeguards. Exchange-rate tests use controlled
fixtures rather than the live provider. Database test records are rolled back or explicitly
cleaned up; test schema migrations remain.

A GitHub Actions workflow is included to run `verify` against PostgreSQL. A passing hosted CI run
is not claimed; the workflow has not yet been verified on GitHub.

## Experimental: iPhone capture — testing only

**This is a development experiment, not a working end-to-end iPhone product.**

The Mac-side test implementation reads JSON capture files from a user-selected private iCloud
folder. It stages uncertain payments for review, detects repeated deliveries, flags possible
duplicates and can remember merchant categories with explicit user approval. These safeguards
have automated tests, but that does **not** establish that phone capture works.

**Not yet configured or verified:**

- A physical iPhone / Wallet automation and its available transaction fields.
- End-to-end iCloud delivery, locked-phone behaviour or bank-specific coverage.
- A distributed iPhone app, signed Shortcut, FinanceKit access or live bank connection.

The proposed first test source is a user-configured Wallet transaction Shortcut on iOS 26.
It is not phone-wide notification access or a complete bank-history feed. Atlas only scans while
the desktop is open; iCloud may delay delivery. The source files can contain financial information
and are not automatically deleted. No phone permissions or bank connections have been activated.

See the [experiment notes](docs/iphone-import-plan.md) and
[testing setup guide](src/main/resources/iphone-setup.txt) for limitations and the file contract.

## Scope and limitations

- **Local, single-user application:** no accounts, authentication or cross-device account sync.
  Keep the API on loopback; do not expose its unauthenticated endpoints publicly.
- **Purchase-based net spending:** reimbursements reduce the original purchase period. This is
  not an account-balance, settlement or cash-flow report.
- **Reference conversion:** exchange-rate estimates exclude card-provider spreads and fees.
  The current money model supports at most two decimal places.
- **No live bank syncing:** statement importing and supported bank connections remain possible
  future work, not implemented features.

Detailed endpoints, validation rules and calculation policies are documented in the
[API and behaviour reference](docs/api-and-behaviour.md).
