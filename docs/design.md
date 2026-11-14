# Snack Counter Automation — Design

**Draft:** Domain data, in-memory offer/assignment handoff, budget accounting, and monthly reporting are implemented as prototype services. The budget service is not yet wired into refill completion; chat and HR simulations remain planned.

## Goal and workflow

The prototype coordinates one active refill at a time for each snack box. A volunteer or office coordinator reports a box as empty. The system offers it to an available volunteer in the office using round-robin order by employee ID. Each offer advances that office's rotation, including offers that are declined or expire; this spreads offers, not necessarily completed purchases. If an offered volunteer becomes unavailable or changes office, the offer moves on. Declines and expired offers or accepted assignments also cause a handoff. If no one is eligible, the refill stays open for an explicit retry.

Before purchase, the caller records an expected amount as a reservation against the office's local-currency monthly budget. Reservations and recorded purchases together cannot exceed the monthly cap. A purchase is recorded once against its actual purchase month; if that differs from its reservation month, the old reservation is released only after the actual month passes its cap check. An actual amount may exceed its estimate only when the remaining budget permits it. Unneeded reservations must be explicitly released; there is no reservation-expiry scheduler. The budget service is currently separate from the refill coordinator, so callers must connect purchase and completion operations. Receipt capture and HR submission remain planned.

## Main parts and data

- **Refill service:** currently tracks volunteers, availability, active refills, offers, deadlines and the last person offered in each office. State is held in memory.
- **Budget and report:** the in-memory service tracks expected reservations and actual purchases against Oslo's 6,000 NOK and Bengaluru's 15,000 INR monthly limits. It reports each office in its local currency and provides combined NOK budget, spend, reserved, and remaining totals only when given an explicit positive NOK-per-INR rate. It rounds the converted INR total to two decimal places using half-up rounding; no live rate is fetched. The service is not yet connected to refill lifecycle operations.
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
