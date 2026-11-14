# Snack Counter Automation

A small Java prototype being developed to coordinate snack refills across the Oslo and Bengaluru offices. The current code models business data and coordinates refill offers, acceptance, and handoffs in memory. Budget checks, purchase accounting, reporting, and simulated integrations are not implemented yet.

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

This is an in-memory, single-process prototype. Data resets when it exits, and its synchronization does not coordinate multiple application instances. Chat and HR stand-ins are planned but not yet implemented; no real employee data, receipts, or claims are sent to external systems.

Assignment expiry requires the caller to invoke `expireAssignments()`; there is no background scheduler. Completion only marks the box as refilled; it does not yet record an amount or receipt. An expired assignee who already purchased snacks needs manual follow-up: the prototype cannot record that late receipt yet and does not guarantee that a physical duplicate purchase is impossible.

Offers rotate by employee ID separately for each office and skip unavailable volunteers. Each offer advances the rotation, even if declined or expired. Updating an offered volunteer's availability or office immediately returns any reassigned refill snapshots; accepted assignments remain with their current owner until completion or expiry.

## Working agreement

The project is built in small, reviewable steps. Each step should be tested before it is approved and committed. Commit messages use two labeled sections:

- **Business Summary:** behavior or problem addressed, in business terms.
- **Technical Summary:** implementation and verification, in accessible technical terms.

See [docs/workflow.md](docs/workflow.md) for the step/approval checklist. Personal interview notes belong in `.private/WORKLOG.md`, which is intentionally Git-ignored and must not be submitted. The assignment PDF is source material; do not include it in a submission unless explicitly requested.
