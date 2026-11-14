# Build, Review, and Submission Workflow

## Guardrails

- Keep the implementation focused on the assignment: design document, working core lifecycle/assignment/budget logic, simulated chat and HR, and automated tests.
- Optimize for a clear 4–5 minute walkthrough; prefer readable business rules over extra frameworks or infrastructure.
- Do not commit credentials, real employee details, real receipts, or personal interview preparation.
- Personal notes live under `.private/` and are excluded by `.gitignore`.
- The assignment PDF is source material and is not part of the deliverable set unless explicitly requested.

## Steps and approval gates

| Step | Work | Evidence / approval gate | Status |
| --- | --- | --- | --- |
| 0. Repository setup | Maven Java project, source/test layout, README, design/workflow docs, private-notes exclusion | Java 17 compilation passes; Maven test attempt is blocked by the configured artifact mirror timing out; private notes and assignment PDF are ignored | Complete |
| 1. Domain model | Office, volunteers, refill state, amount/currency, receipt and claim data | Nine model tests pass using Maven installation-default settings; workflow not yet implemented | Complete |
| 2. Lifecycle and assignment | Open empty-box refill; round-robin volunteer offer; accept/decline; expiry/handoff; completion | Thirteen service tests and nine model tests pass; deadline tick and late-purchase limitations documented | Complete |
| 3. Budget and report | Office-local monthly caps, reservation/spend logic, explicit FX input and combined report | Budget tests cover cap boundaries, concurrent reservations, releases, month changes, local currencies, explicit FX and rounding; budget service remains separate from lifecycle coordinator | Complete |
| 4. Simulated integrations | Chat notifications and HR claim submit/status stand-ins | Test payload includes amount, currency, description, receipt, employee ID | Not started |
| 5. Design and walkthrough | Keep design doc to 1–2 pages; verify all assignment deliverables and rehearse a 4–5 minute explanation | Full tests pass; requirement checklist complete | Not started |

## Step protocol

1. Make one small, coherent change addressing a listed step.
2. Review it in business terms: which operational problem does it solve? Review it in accessible technical terms: where is the rule enforced and how is it tested?
3. Run focused and full tests when practical; add end-to-end scenarios as real workflow operations become available. Record exactly what was tested and fix failures before proceeding.
4. Update the status/evidence table and the README/design document if the user-facing behavior or assumptions changed.
5. Request/review approval of the step before committing. Do not assume approval merely because the implementation exists.
6. After approval, create one focused commit. Use a message body with `Business Summary:` and `Technical Summary:` sections, including key decisions and test evidence.
7. Keep private interview notes in `.private/WORKLOG.md` only. Never stage or submit them.

## Completion checklist

- [ ] Design document is at most 1–2 pages.
- [ ] A refill can move from empty report through purchase, HR claim, and reimbursement status.
- [ ] Volunteer choice is fair and unavailable volunteers are skipped.
- [ ] A refill cannot be committed by two volunteers; unfinished refills are handed off.
- [x] Office budgets and monthly totals are correct in NOK and INR; combined reporting states the FX assumption.
- [ ] Simulated chat and HR systems have clear, testable interfaces.
- [ ] Automated tests pass.
- [ ] README tells a reviewer how to run tests and states limitations.
- [ ] No personal notes, real data, build output, or unintended source material are staged for submission.
- [ ] Walkthrough can be delivered in 4–5 minutes.
