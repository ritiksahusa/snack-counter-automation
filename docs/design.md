# Snack Counter Automation — Design

**Draft:** Domain data, in-memory offer/assignment handoff, budget accounting, monthly reporting, and simulated chat/HR workflows are implemented as a single-process prototype.

## Goal and workflow

The prototype coordinates one active refill at a time for each snack box. A volunteer or office coordinator reports a box as empty. The system offers it to an available volunteer in the office using round-robin order by employee ID. Each offer advances that office's rotation, including offers that are declined or expire; this spreads offers, not necessarily completed purchases. If an offered volunteer becomes unavailable or changes office, the offer moves on. Declines and expired offers or accepted assignments also cause a handoff. If no one is eligible, the refill stays open for an explicit retry.

When a volunteer accepts, the workflow reserves the expected amount against the office's local-currency monthly budget. Reservations and recorded purchases together cannot exceed the monthly cap. Decline/timeout handoffs release the reservation. A purchase is recorded once against its actual purchase month; if that differs from its reservation month, the old reservation is released only after the actual month passes its cap check. An actual amount may exceed its estimate only when the remaining budget permits it. Completion records the actual amount and a receipt reference, then the claim is submitted to simulated HR with the employee ID, description and purchase-currency amount. HR approval and rejection are distinct from reimbursement; the refill is marked reimbursed only after HR reports payment. Simulated chat records refill, offer, acceptance, decline, handoff, and completion events.

## Main parts and data

- **Refill service:** tracks volunteers, availability, active refills, offers, deadlines and the last person offered in each office. State is held in memory and significant lifecycle events are sent through a `ChatGateway`.
- **Workflow and budget:** `RefillWorkflow` joins the coordinator and budget service. Expected reservations and actual purchases are checked against Oslo's 6,000 NOK and Bengaluru's 15,000 INR monthly limits. Reports retain local currencies and provide combined NOK totals only with an explicit positive NOK-per-INR rate; conversion rounds to two decimals using half-up rounding.
- **Chat simulation:** `InMemoryChatGateway` stores structured refill and claim-status events in memory. It does not send messages or include purchase amounts or receipt contents.
- **HR simulation:** `InMemoryHrGateway` stores the claim payload (employee ID, amount in purchase currency, description, receipt reference and status). It models pending, approved, rejected and reimbursed outcomes with guarded transitions; approval is not payment.

## How this addresses today's problems

- **Empty box with no owner:** each refill is assigned to one named volunteer and has a deadline.
- **Duplicate purchases:** only one active refill is allowed per office/snack box, and synchronized acceptance allows a single winner.
- **Work concentrated on a few people:** rotation distributes offers among available volunteers; declines and expiries cause a handoff. It does not guarantee an equal number of completed refills.
- **Slow/unclear reimbursement:** claim status is tracked from submission through HR outcome, along with receipt and employee reference.
- **Unknown spending:** office-local monthly totals and a consolidated report make spend visible and comparable.

## Trade-offs and exclusions

In-memory storage keeps the prototype small and easy to explain; all state is lost on restart, and synchronization only works within one application process. Production use would require durable storage, external-system idempotency, and cross-instance coordination. The prototype does not implement authentication, real chat/HR connections, receipt image storage or OCR, live exchange rates, snack-consumption analytics, procurement cards, or deployment. Claim amounts remain in the currency in which the purchase was made; payroll-currency conversion is left to HR and is not inferred from the employee's home office or payroll currency.

Expiry requires an explicit caller-driven check, not a background scheduler. If a volunteer has purchased but reports completion after reassignment, manual follow-up is required; the current prototype cannot record that late receipt or prevent a duplicate physical purchase. The chat and HR gateways are local simulations, not production integrations.
