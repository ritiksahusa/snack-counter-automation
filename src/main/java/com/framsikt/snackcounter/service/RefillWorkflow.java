package com.framsikt.snackcounter.service;

import com.framsikt.snackcounter.model.Amount;
import com.framsikt.snackcounter.model.ClaimStatus;
import com.framsikt.snackcounter.model.Office;
import com.framsikt.snackcounter.model.Receipt;
import com.framsikt.snackcounter.model.RefillStatus;
import com.framsikt.snackcounter.model.ReimbursementClaim;
import com.framsikt.snackcounter.model.Volunteer;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Joins refill assignment, budget accounting, and simulated HR into one in-memory workflow. */
public final class RefillWorkflow {
    private final RefillCoordinator refills;
    private final BudgetService budgets;
    private final HrGateway hr;
    private final Map<String, ReimbursementClaim> claimsAwaitingSubmission = new HashMap<>();
    private final Set<String> activeReservations = new HashSet<>();

    public RefillWorkflow(RefillCoordinator refills, BudgetService budgets, HrGateway hr) {
        this.refills = Objects.requireNonNull(refills, "refills");
        this.budgets = Objects.requireNonNull(budgets, "budgets");
        this.hr = Objects.requireNonNull(hr, "hr");
    }

    public synchronized List<RefillCoordinator.Snapshot> upsertVolunteer(Volunteer volunteer) {
        List<RefillCoordinator.Snapshot> changed = refills.upsertVolunteer(volunteer);
        changed.forEach(snapshot -> releaseReservation(snapshot.id()));
        return changed;
    }

    public synchronized RefillCoordinator.Snapshot reportEmpty(Office office, String boxId) {
        return refills.reportEmpty(office, boxId);
    }

    /** Reserves expected local-currency spend before the volunteer accepts ownership. */
    public synchronized RefillCoordinator.Snapshot accept(String refillId, String employeeId, String offerId,
                                                            YearMonth period, Amount expectedAmount) {
        budgets.reserve(refillId, refills.get(refillId).office(), period, expectedAmount);
        activeReservations.add(refillId);
        try {
            return refills.accept(refillId, employeeId, offerId);
        } catch (RuntimeException failure) {
            releaseReservation(refillId);
            throw failure;
        }
    }

    public synchronized RefillCoordinator.Snapshot decline(String refillId, String employeeId, String offerId) {
        RefillCoordinator.Snapshot updated = refills.decline(refillId, employeeId, offerId);
        releaseReservation(refillId);
        return updated;
    }

    public synchronized List<RefillCoordinator.Snapshot> expireAssignments() {
        List<RefillCoordinator.Snapshot> changed = refills.expireAssignments();
        changed.forEach(snapshot -> releaseReservation(snapshot.id()));
        return changed;
    }

    public synchronized RefillCoordinator.Snapshot retryUnassigned(String refillId) {
        return refills.retryUnassigned(refillId);
    }

    /**
     * Records the actual purchase only after the coordinator verifies the current owner and deadline.
     * The claim is retained locally until submitClaim succeeds, so HR submission can be retried.
     */
    public synchronized RefillCoordinator.Snapshot completePurchase(String refillId, String employeeId,
                                                                      YearMonth purchasePeriod,
                                                                      Amount actualAmount,
                                                                      String description,
                                                                      Receipt receipt) {
        ReimbursementClaim claim = new ReimbursementClaim(employeeId, actualAmount, description,
                receipt, ClaimStatus.PENDING);
        RefillCoordinator.Snapshot completed = refills.markRefilled(refillId, employeeId,
                () -> budgets.recordPurchase(refillId, purchasePeriod, actualAmount));
        activeReservations.remove(refillId);
        claimsAwaitingSubmission.put(refillId, claim);
        return completed;
    }

    public synchronized HrGateway.Submission submitClaim(String refillId) {
        ReimbursementClaim claim = claimsAwaitingSubmission.get(refillId);
        if (claim == null) {
            throw new IllegalStateException("No completed purchase is awaiting claim submission");
        }
        HrGateway.Submission submitted = hr.submit(refillId, claim);
        refills.markClaimSubmitted(refillId);
        claimsAwaitingSubmission.remove(refillId);
        return submitted;
    }

    /** Approval is not payment; the refill is reimbursed only after HR reports REIMBURSED. */
    public synchronized HrGateway.Submission updateClaimStatus(String refillId, ClaimStatus status) {
        Objects.requireNonNull(status, "status");
        if (status == ClaimStatus.REIMBURSED
            && refills.get(refillId).status() != RefillStatus.CLAIM_SUBMITTED) {
            throw new IllegalStateException("A submitted refill claim is required before reimbursement");
        }
        HrGateway.Submission updated = hr.updateStatus(refillId, status);
        if (status == ClaimStatus.REIMBURSED) {
            refills.markReimbursed(refillId);
        } else {
            refills.recordClaimStatus(refillId, status);
        }
        return updated;
    }

    public synchronized HrGateway.Submission getClaim(String refillId) {
        return hr.getSubmission(refillId);
    }

    private void releaseReservation(String refillId) {
        if (activeReservations.remove(refillId)) {
            budgets.releaseReservation(refillId);
        }
    }
}
