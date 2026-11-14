# Snack Counter Automation

A small Java prototype being developed to coordinate snack refills across the Oslo and Bengaluru offices. The current step defines the office, volunteer, money, refill, receipt, and claim data; assignment, budget checks, reporting, and simulated integrations are not implemented yet.

## Assignment deliverables

- **Design:** [docs/design.md](docs/design.md) — concise submission-facing design document.
- **Working core and tests:** `src/main/java` and `src/test/java`.

## Run the tests

Requires Java 17+ and Maven 3.8+.

```sh
mvn test
```

Run the demonstration once an application entry point is implemented:

```sh
mvn package
java -jar target/snack-counter-automation-1.0-SNAPSHOT.jar
```

## Scope and limitations

This is an in-memory, single-process prototype. Data resets when it exits, and its synchronization does not coordinate multiple application instances. Chat and HR are simulated; no real employee data, receipts, or claims are sent to external systems.

## Working agreement

The project is built in small, reviewable steps. Each step should be tested before it is approved and committed. Commit messages use two labeled sections:

- **Business Summary:** behavior or problem addressed, in business terms.
- **Technical Summary:** implementation and verification, in accessible technical terms.

See [docs/workflow.md](docs/workflow.md) for the step/approval checklist. Personal interview notes belong in `.private/WORKLOG.md`, which is intentionally Git-ignored and must not be submitted. The assignment PDF is source material; do not include it in a submission unless explicitly requested.
