package com.framsikt.snackcounter.service;

import com.framsikt.snackcounter.model.Amount;
import com.framsikt.snackcounter.model.Office;
import com.framsikt.snackcounter.model.SnackCurrency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Tracks in-memory budget reservations, purchases, and monthly reports. */
public final class BudgetService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final Map<String, Reservation> reservations = new HashMap<>();
    private final Map<String, Purchase> purchases = new HashMap<>();

    public record Reservation(String refillId, Office office, YearMonth period, Amount expectedAmount) { }

    public record Purchase(String refillId, Office office, YearMonth period, Amount actualAmount) { }

    public record OfficeSummary(Office office, YearMonth period, Amount monthlyBudget,
                                Amount spent, Amount reserved, Amount remaining) { }

    public record MonthlyReport(YearMonth period, BigDecimal inrToNokRate,
                                List<OfficeSummary> offices, Amount totalBudgetNok,
                                Amount totalSpentNok, Amount totalReservedNok,
                                Amount totalRemainingNok) {
        public MonthlyReport {
            offices = List.copyOf(offices);
        }
    }

    /** Holds expected spend against the office's local budget for the specified month. */
    public synchronized Reservation reserve(String refillId, Office office, YearMonth period,
                                            Amount expectedAmount) {
        requireRefillId(refillId);
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(period, "period");
        requirePositive(expectedAmount, "expectedAmount");
        requireOfficeCurrency(office, expectedAmount);
        if (reservations.containsKey(refillId) || purchases.containsKey(refillId)) {
            throw new IllegalStateException("This refill already has a reservation or purchase");
        }
        ensureWithinBudget(office, period, expectedAmount, null);
        Reservation reservation = new Reservation(refillId, office, period, expectedAmount);
        reservations.put(refillId, reservation);
        return reservation;
    }

    /** Releases a reservation when its refill will not proceed; returns false if none exists. */
    public synchronized boolean releaseReservation(String refillId) {
        requireRefillId(refillId);
        return reservations.remove(refillId) != null;
    }

    /**
     * Records one actual purchase. It is charged to the supplied purchase month, which may
     * differ from the reservation month; the reservation is released only after the cap check.
     */
    public synchronized Purchase recordPurchase(String refillId, YearMonth purchasePeriod,
                                                Amount actualAmount) {
        requireRefillId(refillId);
        Objects.requireNonNull(purchasePeriod, "purchasePeriod");
        requirePositive(actualAmount, "actualAmount");
        Reservation reservation = reservations.get(refillId);
        if (reservation == null) {
            throw new IllegalStateException("A reservation is required before recording a purchase");
        }
        requireOfficeCurrency(reservation.office(), actualAmount);
        if (purchases.containsKey(refillId)) {
            throw new IllegalStateException("A purchase is already recorded for this refill");
        }
        ensureWithinBudget(reservation.office(), purchasePeriod, actualAmount,
                reservation.period().equals(purchasePeriod) ? refillId : null);
        Purchase purchase = new Purchase(refillId, reservation.office(), purchasePeriod, actualAmount);
        purchases.put(refillId, purchase);
        reservations.remove(refillId);
        return purchase;
    }

    public synchronized MonthlyReport report(YearMonth period, BigDecimal inrToNokRate) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(inrToNokRate, "inrToNokRate");
        if (inrToNokRate.signum() <= 0) {
            throw new IllegalArgumentException("INR-to-NOK rate must be positive");
        }
        BigDecimal normalizedRate = inrToNokRate;
        List<OfficeSummary> offices = new ArrayList<>();
        for (Office office : Office.values()) {
            Amount spent = spent(office, period);
            Amount reserved = reserved(office, period);
            Amount budget = office.monthlyBudget();
            Amount remaining = amount(budget.value().subtract(spent.value()).subtract(reserved.value()),
                    budget.currency());
            offices.add(new OfficeSummary(office, period, budget, spent, reserved, remaining));
        }
        offices.sort(Comparator.comparing(OfficeSummary::office));

        Amount totalBudget = consolidate(offices, OfficeSummary::monthlyBudget, normalizedRate);
        Amount totalSpent = consolidate(offices, OfficeSummary::spent, normalizedRate);
        Amount totalReserved = consolidate(offices, OfficeSummary::reserved, normalizedRate);
        Amount totalRemaining = amount(totalBudget.value().subtract(totalSpent.value()).subtract(totalReserved.value()),
                SnackCurrency.NOK);
        return new MonthlyReport(period, normalizedRate, offices, totalBudget, totalSpent,
                totalReserved, totalRemaining);
    }

    private void ensureWithinBudget(Office office, YearMonth period, Amount newAmount,
                                    String reservationBeingReplaced) {
        BigDecimal committed = spent(office, period).value().add(reserved(office, period,
                reservationBeingReplaced).value()).add(newAmount.value());
        if (committed.compareTo(office.monthlyBudget().value()) > 0) {
            throw new IllegalStateException("Monthly budget exceeded for " + office);
        }
    }

    private Amount spent(Office office, YearMonth period) {
        return purchases.values().stream()
                .filter(purchase -> purchase.office() == office && purchase.period().equals(period))
                .map(Purchase::actualAmount)
                .reduce(zero(office.monthlyBudget().currency()), Amount::plus);
    }

    private Amount reserved(Office office, YearMonth period) {
        return reserved(office, period, null);
    }

    private Amount reserved(Office office, YearMonth period, String excludingRefillId) {
        return reservations.values().stream()
                .filter(reservation -> reservation.office() == office && reservation.period().equals(period)
                        && !Objects.equals(reservation.refillId(), excludingRefillId))
                .map(Reservation::expectedAmount)
                .reduce(zero(office.monthlyBudget().currency()), Amount::plus);
    }

    private static Amount consolidate(List<OfficeSummary> offices,
                                      Function<OfficeSummary, Amount> selectAmount,
                                      BigDecimal inrToNokRate) {
        BigDecimal nok = ZERO;
        BigDecimal inr = ZERO;
        for (OfficeSummary office : offices) {
            Amount amount = selectAmount.apply(office);
            if (amount.currency() == SnackCurrency.NOK) {
                nok = nok.add(amount.value());
            } else {
                inr = inr.add(amount.value());
            }
        }
        BigDecimal total = nok.add(inr.multiply(inrToNokRate)).setScale(2, RoundingMode.HALF_UP);
        return new Amount(total, SnackCurrency.NOK);
    }

    private static Amount zero(SnackCurrency currency) {
        return new Amount(ZERO, currency);
    }

    private static Amount amount(BigDecimal value, SnackCurrency currency) {
        return new Amount(value, currency);
    }

    private static void requireOfficeCurrency(Office office, Amount amount) {
        if (amount.currency() != office.monthlyBudget().currency()) {
            throw new IllegalArgumentException("Amount currency must match the office budget currency");
        }
    }

    private static void requirePositive(Amount amount, String name) {
        Objects.requireNonNull(amount, name);
        if (amount.value().signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireRefillId(String refillId) {
        if (refillId == null || refillId.isBlank()) {
            throw new IllegalArgumentException("refillId must not be blank");
        }
    }
}
