# Snack Counter Automation — Design

An empty snack box is easy to notice; the harder questions are who will refill it, whether the office can afford it, and whether the purchase was reimbursed. This prototype follows one refill from report to reimbursement, making ownership and spending visible without pretending to connect to company systems that are not part of the exercise.

## The refill, from start to finish

1. **Someone reports an empty box.** The system opens one active refill for that office and box, avoiding competing assignments for the same need.
2. **A volunteer gets a clear turn.** An available person in that office receives an offer and must accept it before the deadline. Offers rotate by employee ID; a decline or expiry hands the work on. If nobody is eligible, the refill stays open for a later retry.
3. **The budget is checked before commitment.** On acceptance, the workflow reserves the expected cost against the monthly office limit: NOK 6,000 in Oslo or INR 15,000 in Bengaluru. Failed acceptance releases the reservation. When the volunteer records the actual purchase, the system checks the purchase month and replaces the estimate with actual spend only if the cap still allows it.
4. **The claim has an explicit outcome.** The purchase amount, currency, description, receipt reference, and employee ID go to simulated HR. A claim can be pending, approved, rejected, or reimbursed. Approval is not payment; the refill is complete only when reimbursement is reported.

This sequence keeps the important promise simple: each refill has an owner, each commitment is budget-aware, and each claim can be followed to its actual outcome. The chat simulation records lifecycle events, while the HR simulation stores claim details; neither sends data externally. Reports show each office in its own currency. A combined NOK total is produced only when the caller supplies a positive NOK-per-INR rate, so the prototype does not hide an exchange-rate assumption.

## What belongs in this prototype—and what does not

**Keep:** the small domain model, one active refill per box, explicit offers and deadlines, budget reservations and actual-purchase checks, monthly reports, and clear claim states. These rules address the assignment's core risks and are exercised by automated tests. Rotation spreads offers, but it does not promise equal completed purchases.

**Leave out for now:** databases, multi-instance coordination, company login, live chat or HR connections, inbound chat commands, calendars and holiday handling, automatic expiry jobs, payroll conversion, receipt-image storage or OCR, consumption analytics, procurement-card processing, and deployment infrastructure. Those need real business policies, credentials, or operational requirements; adding them to a self-contained prototype would create complexity without validating the core workflow.

The boundary is deliberate: state is in memory and resets on restart, and synchronization covers only one process. Availability and expiry checks are supplied or triggered by the caller. A receipt is represented by a reference, not image data; HR timing and escalation are not automated. If a volunteer physically buys snacks after their assignment has been handed off, the case still needs manual follow-up. A production version should first add durable shared state and then connect approved identity, chat, and HR services behind the existing workflow boundaries.
