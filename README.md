# Snack Counter Automation

A Java 17 prototype for coordinating snack refills in Oslo and Bengaluru. It turns an empty-box report into an assigned purchase, tracks office spending, and simulates reimbursement without contacting real chat or HR systems.

## The refill story

1. Someone reports an empty box. The coordinator offers the refill to an available volunteer in that office, rotating offers by employee ID.
2. The volunteer accepts the current offer. The workflow reserves the expected cost against the office's monthly local-currency budget: NOK 6,000 for Oslo or INR 15,000 for Bengaluru.
3. The volunteer records the actual purchase, description, and receipt reference. The budget service checks the actual purchase month and replaces the reservation with spend only if the cap allows it.
4. The workflow submits a simulated HR claim with employee ID, amount/currency, description, and receipt reference. HR can approve or reject; approval is not payment. The refill reaches reimbursed only after HR reports reimbursement.

The short design is in [docs/design.md](docs/design.md). Core logic is under `src/main/java`; tests are under `src/test/java`.

## Run the tests

Requires Java 17+ and Maven 3.8+.

```sh
mvn test
```

There is no standalone command-line application yet. The tests exercise the full simulated service workflow, including purchase, claim, reimbursement, budget rejection, and timeout handoff.

## Scope and trade-offs

The limits are deliberate for a small prototype:

- **Reliability:** State is in memory and synchronization covers only one process. Assignment expiry is caller-triggered, with no background scheduler. A purchase reported after reassignment needs manual follow-up.
- **Integrations:** Chat and HR are simulated. There is no company login, inbound chat command, calendar/holiday integration, or HR timing/escalation. HR handles payroll-currency conversion.
- **Evidence and insights:** A receipt is represented by a reference, not image data. The system records purchase descriptions but does not measure what snacks are actually consumed.
- **Fairness:** Rotation balances offers, not completed purchases.

Combined monthly reports keep office totals in their local currencies and require an explicit INR-to-NOK exchange rate. Simulated chat and HR are in-memory stand-ins; no employee or receipt data is transmitted externally.
