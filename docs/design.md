# Snack Counter Automation — Design

**Draft:** This describes the planned solution. Domain data and in-memory offer/assignment handoff are implemented; budget accounting, reporting and integrations remain to be built and verified.

## Goal and workflow

The prototype coordinates one active refill at a time for each snack box. A volunteer or office coordinator reports a box as empty. The system offers it to an available volunteer in the office using round-robin order by employee ID. Each offer advances that office's rotation, including offers that are declined or expire; this spreads offers, not necessarily completed purchases. If an offered volunteer becomes unavailable or changes office, the offer moves on. Declines and expired offers or accepted assignments also cause a handoff. If no one is eligible, the refill stays open for an explicit retry.

Before purchase, the volunteer records an expected amount. The system reserves that amount against the office's monthly budget and rejects a reservation that would exceed the available budget. Once the volunteer records the actual purchase and receipt, the system records the spend and submits a reimbursement claim to the simulated HR service. HR owns approval and payroll-currency reimbursement; the prototype shows the claim as pending until HR reports an outcome.

## Main parts and data

- **Refill service:** currently tracks volunteers, availability, active refills, offers, deadlines and the last person offered in each office. State is held in memory.
- **Budget and report (planned):** track reservations and actual purchases in local currencies, then report office totals and a consolidated NOK total using a supplied monthly INR→NOK rate. Amounts use decimal values and an explicit currency.
- **Chat interface (planned simulation):** record offers, reminders, handoffs and status notifications; no real chat integration.
- **HR interface (planned simulation):** accept amount, purchase currency, description, receipt reference and employee ID; track claim outcomes. No real HR integration.

## How this addresses today's problems

- **Empty box with no owner:** each refill is assigned to one named volunteer and has a deadline.
- **Duplicate purchases:** only one active refill is allowed per office/snack box, and synchronized acceptance allows a single winner.
- **Work concentrated on a few people:** rotation distributes offers among available volunteers; declines and expiries cause a handoff. It does not guarantee an equal number of completed refills.
- **Slow/unclear reimbursement:** claim status is tracked from submission through HR outcome, along with receipt and employee reference.
- **Unknown spending:** office-local monthly totals and a consolidated report make spend visible and comparable.

## Trade-offs and exclusions

In-memory storage keeps the prototype small and easy to explain; all state is lost on restart, and synchronization only works within one application process. Production use would require durable storage and cross-instance coordination. The prototype does not implement authentication, real chat/HR connections, receipt OCR, live exchange rates, snack-consumption analytics, procurement cards, or deployment. HR's stated responsibility for payroll-currency reimbursement is treated as an external system boundary.

Expiry requires an explicit caller-driven check, not a background scheduler. If a volunteer has purchased but reports completion after reassignment, manual follow-up is required; the current prototype cannot record that late receipt or prevent a duplicate physical purchase.
