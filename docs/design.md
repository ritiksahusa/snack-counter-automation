# Snack Counter Automation — Design

An empty snack box is easy to notice; the harder questions are who will refill it, whether the office can afford it, and whether the purchase was reimbursed. This Java 17 prototype makes one refill traceable from report to outcome while keeping its boundaries clear: chat and HR are simulated, and state is held in memory.

## 1. From empty to reimbursed

1. **Report:** A volunteer or office coordinator reports an empty box. The system opens one active refill for that office and box.
2. **Assign:** The coordinator offers it to an available volunteer in the same office, rotating by employee ID. The volunteer must accept the current offer before its deadline. A decline, expiry, or changed availability hands the work on; when nobody is eligible, the refill remains open for retry.
3. **Reserve and buy:** During acceptance, the workflow reserves the expected cost against that office’s monthly local-currency limit—NOK 6,000 in Oslo or INR 15,000 in Bengaluru. If acceptance fails, the reservation is released. Once the current owner records a purchase, the budget service checks its actual month and amount before replacing the estimate with spend.
4. **Submit and close:** The workflow submits a claim to simulated HR. HR records pending, approved, rejected, or reimbursed status. Approval is not payment: the refill reaches reimbursed only after HR reports reimbursement.

## 2. Main parts and the data they keep

- **Domain records** describe offices, volunteers, refills, purchases, receipts, and claims. A volunteer has an employee ID, home/current office, payroll currency, and availability. A refill has an ID, box and office, status, owner/offer details, and deadline. Purchase and claim data include the employee ID, amount and currency, purchase month, description, receipt reference, and claim status.
- **Refill coordinator** owns assignment rules and lifecycle transitions. It tracks volunteer availability, active refills, offer tokens/deadlines, and per-office rotation. A `ChatGateway` receives lifecycle events; the in-memory implementation stores events, not purchase or receipt contents.
- **Workflow and budget service** connect acceptance, purchase, and claim submission. The budget service keeps reservations and actual purchases by refill, office, month, and local-currency amount; reports show local totals and optionally a combined NOK total using an explicitly supplied positive INR-to-NOK rate.
- **HR gateway** receives a claim containing employee ID, purchase amount/currency, description, and receipt reference, then tracks its status. The in-memory implementation is a testable stand-in, not an external integration.

## 3. How the design prevents the stated problems

- **Unowned or duplicated work:** only one active refill is allowed per office/box. A named, time-limited offer and guarded acceptance identify the owner; declines and expiries trigger handoff. Synchronized lifecycle operations prevent two volunteers from accepting the same offer.
- **Work falling repeatedly to the same people:** per-office round-robin rotates offers and skips unavailable volunteers. This balances opportunities, not completed purchases.
- **Overspending or unclear totals:** expected costs are reserved before acceptance, and the actual purchase must pass the same office/month cap check. Oslo and Bengaluru totals remain in NOK and INR; combined reporting makes its exchange-rate input explicit.
- **Unclear reimbursement:** claim status is tracked separately from refill status, so approval cannot be mistaken for payment. Receipt and employee references travel with the simulated claim.

## 4. Trade-offs and deliberate exclusions

**Keep the prototype focused:** the domain rules, assignment and handoff, budget reservations and reports, claim lifecycle, simulated gateway boundaries, and automated tests. These directly demonstrate the requested workflow without adding infrastructure that does not validate it.

**Do not build yet:** a database or multi-instance coordination, company login, live chat/HR connections or inbound chat commands, calendar/holiday automation, a background expiry scheduler, payroll-currency conversion, receipt-image storage/OCR, snack-consumption analytics, procurement-card processing, or deployment infrastructure. These require real policies, credentials, or operational decisions.

The trade-off is that state resets on restart and synchronization covers one process. Availability and expiry checks are supplied or triggered by the caller. A receipt is a reference, not image data; HR timing and escalation are not automated. A purchase made after reassignment needs manual follow-up, and rotation cannot guarantee equal completed purchases. A production version should begin with durable shared state, then connect approved identity, chat, and HR services behind the existing boundaries.
