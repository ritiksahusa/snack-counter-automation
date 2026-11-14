# Snack Counter Automation — Design

**Draft:** This describes the planned solution. Currently only the domain data and money validation are implemented; the workflow and integrations remain to be built and verified.

## Goal and workflow

The prototype replaces informal refill messages with one visible, owned refill at a time for each snack box. A volunteer or office coordinator reports a box as empty. The system offers the refill to an available volunteer with the fewest recent assignments (stable tie-break). The volunteer accepts or declines. If they decline or the offer expires, the system offers it to the next eligible person; an accepted refill that is not completed by its deadline is also handed off.

Before purchase, the volunteer records an expected amount. The system reserves that amount against the office's monthly budget and rejects a reservation that would exceed the available budget. Once the volunteer records the actual purchase and receipt, the system records the spend and submits a reimbursement claim to the simulated HR service. HR owns approval and payroll-currency reimbursement; the prototype shows the claim as pending until HR reports an outcome.

## Main parts and data

- **Refill service:** owns lifecycle rules, assignment, deadlines, and coordination. It tracks the office/snack-box refill, current assignee, state, time limits, and assignment history.
- **In-memory repository:** stores offices, volunteers, availability, refills, reservations, purchases, claims, and monthly totals during one run. Money uses decimal amounts and an explicit currency.
- **Chat interface (simulated):** records offers, reminders, handoffs, and status notifications. A real chat integration is deliberately not included.
- **HR interface (simulated):** receives amount, purchase currency, description, receipt reference, and employee ID; tracks pending/approved/rejected claim status. A real HR integration is deliberately not included.
- **Report:** displays actual spend separately for Oslo (NOK) and Bengaluru (INR), remaining budget, and a consolidated NOK total using an explicitly supplied monthly INR→NOK rate. The report shows the rate used; it does not fetch or assume a live rate.

## How this addresses today's problems

- **Empty box with no owner:** each refill is assigned to one named volunteer and has a deadline.
- **Duplicate purchases:** only one active refill is allowed per office/snack box, and synchronized acceptance allows a single winner.
- **Work concentrated on a few people:** rotation favors volunteers with fewer recent assignments; unavailable/holiday volunteers are skipped; declines and expiries cause a handoff.
- **Slow/unclear reimbursement:** claim status is tracked from submission through HR outcome, along with receipt and employee reference.
- **Unknown spending:** office-local monthly totals and a consolidated report make spend visible and comparable.

## Trade-offs and exclusions

In-memory storage keeps the prototype small and easy to explain; all state is lost on restart, and synchronization only works within one application process. Production use would require durable storage and cross-instance coordination. The prototype does not implement authentication, real chat/HR connections, receipt OCR, live exchange rates, snack-consumption analytics, procurement cards, or deployment. HR's stated responsibility for payroll-currency reimbursement is treated as an external system boundary.
