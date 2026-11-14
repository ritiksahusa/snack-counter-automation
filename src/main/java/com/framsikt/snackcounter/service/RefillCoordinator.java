package com.framsikt.snackcounter.service;

import com.framsikt.snackcounter.model.Office;
import com.framsikt.snackcounter.model.RefillStatus;
import com.framsikt.snackcounter.model.Volunteer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Coordinates offers in one process. Call expireAssignments periodically to perform handoffs. */
public final class RefillCoordinator {
    private final Clock clock;
    private final Duration offerTimeout;
    private final Duration completionTimeout;
    private final Map<String, Volunteer> volunteers = new HashMap<>();
    private final Map<String, Ticket> tickets = new HashMap<>();
    private final Map<Office, String> lastOffered = new EnumMap<>(Office.class);

    public record Snapshot(String id, Office office, String boxId, RefillStatus status,
                           String employeeId, String offerId, Instant deadline) { }

    private static final class Ticket {
        final String id = UUID.randomUUID().toString();
        final Office office;
        final String boxId;
        final Set<String> attempted = new HashSet<>();
        RefillStatus status = RefillStatus.OPEN;
        String employeeId;
        String offerId;
        Instant deadline;

        Ticket(Office office, String boxId) {
            this.office = office;
            this.boxId = boxId;
        }

        Snapshot snapshot() {
            return new Snapshot(id, office, boxId, status, employeeId, offerId, deadline);
        }
    }

    public RefillCoordinator(Clock clock, Duration offerTimeout, Duration completionTimeout) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.offerTimeout = positive(offerTimeout);
        this.completionTimeout = positive(completionTimeout);
    }

    private static Duration positive(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        return duration;
    }

    /** Updates availability/location and returns offers that had to be reassigned. */
    public synchronized List<Snapshot> upsertVolunteer(Volunteer volunteer) {
        Objects.requireNonNull(volunteer, "volunteer");
        volunteers.put(volunteer.employeeId(), volunteer);
        List<Snapshot> changed = new ArrayList<>();
        Instant now = clock.instant();
        for (Ticket ticket : tickets.values()) {
            if (ticket.status == RefillStatus.OFFERED && volunteer.employeeId().equals(ticket.employeeId)
                    && (!volunteer.available() || volunteer.currentOffice() != ticket.office)) {
                offerNext(ticket, now);
                changed.add(ticket.snapshot());
            }
        }
        return List.copyOf(changed);
    }

    public synchronized Snapshot reportEmpty(Office office, String boxId) {
        Objects.requireNonNull(office, "office");
        if (boxId == null || boxId.isBlank()) {
            throw new IllegalArgumentException("boxId must not be blank");
        }
        boolean alreadyActive = tickets.values().stream().anyMatch(t -> t.office == office
                && t.boxId.equals(boxId) && t.status != RefillStatus.PURCHASED);
        if (alreadyActive) {
            throw new IllegalStateException("A refill is already active for this box");
        }
        Ticket ticket = new Ticket(office, boxId);
        tickets.put(ticket.id, ticket);
        offerNext(ticket, clock.instant());
        return ticket.snapshot();
    }

    public synchronized Snapshot get(String refillId) {
        return find(refillId).snapshot();
    }

    /** An offer token prevents an old response accepting a later offer to the same person. */
    public synchronized Snapshot accept(String refillId, String employeeId, String offerId) {
        Ticket ticket = find(refillId);
        Instant now = clock.instant();
        expire(ticket, now);
        requireOffer(ticket, employeeId, offerId);
        Volunteer volunteer = volunteers.get(employeeId);
        if (volunteer == null || !volunteer.available() || volunteer.currentOffice() != ticket.office) {
            throw new IllegalStateException("Volunteer is not available in this office");
        }
        ticket.status = RefillStatus.ACCEPTED;
        ticket.offerId = null;
        ticket.deadline = now.plus(completionTimeout);
        return ticket.snapshot();
    }

    public synchronized Snapshot decline(String refillId, String employeeId, String offerId) {
        Ticket ticket = find(refillId);
        Instant now = clock.instant();
        expire(ticket, now);
        requireOffer(ticket, employeeId, offerId);
        offerNext(ticket, now);
        return ticket.snapshot();
    }

    /** Marks the box refilled only; purchase details and budget accounting are a later step. */
    public synchronized Snapshot markRefilled(String refillId, String employeeId) {
        Ticket ticket = find(refillId);
        Instant now = clock.instant();
        expire(ticket, now);
        if (ticket.status != RefillStatus.ACCEPTED || !Objects.equals(ticket.employeeId, employeeId)) {
            throw new IllegalStateException("Only the current assignee may complete this refill; handle late purchases manually");
        }
        ticket.status = RefillStatus.PURCHASED;
        ticket.deadline = null;
        return ticket.snapshot();
    }

    /** Reopens an unassigned ticket for a new round after availability changes. */
    public synchronized Snapshot retryUnassigned(String refillId) {
        Ticket ticket = find(refillId);
        if (ticket.status != RefillStatus.OPEN) {
            throw new IllegalStateException("Only an unassigned refill can be retried");
        }
        ticket.attempted.clear();
        offerNext(ticket, clock.instant());
        return ticket.snapshot();
    }

    /** Explicit tick: there is no background scheduler in this prototype. */
    public synchronized List<Snapshot> expireAssignments() {
        Instant now = clock.instant();
        List<Snapshot> changed = new ArrayList<>();
        for (Ticket ticket : tickets.values()) {
            if (expire(ticket, now)) {
                changed.add(ticket.snapshot());
            }
        }
        return List.copyOf(changed);
    }

    private boolean expire(Ticket ticket, Instant now) {
        if ((ticket.status == RefillStatus.OFFERED || ticket.status == RefillStatus.ACCEPTED)
                && !now.isBefore(ticket.deadline)) {
            offerNext(ticket, now);
            return true;
        }
        return false;
    }

    private void offerNext(Ticket ticket, Instant now) {
        ticket.status = RefillStatus.OPEN;
        ticket.employeeId = null;
        ticket.offerId = null;
        ticket.deadline = null;
        List<String> eligible = volunteers.values().stream()
                .filter(v -> v.available() && v.currentOffice() == ticket.office
                        && !ticket.attempted.contains(v.employeeId()))
            .map(Volunteer::employeeId)
            .sorted()
            .toList();
        if (eligible.isEmpty()) {
            return;
        }
        String previous = lastOffered.get(ticket.office);
        String next = eligible.stream()
            .filter(id -> previous == null || id.compareTo(previous) > 0)
            .findFirst().orElse(eligible.get(0));
        lastOffered.put(ticket.office, next);
        ticket.attempted.add(next);
        ticket.status = RefillStatus.OFFERED;
        ticket.employeeId = next;
        ticket.offerId = UUID.randomUUID().toString();
        ticket.deadline = now.plus(offerTimeout);
    }

    private static void requireOffer(Ticket ticket, String employeeId, String offerId) {
        if (ticket.status != RefillStatus.OFFERED || offerId == null
                || !Objects.equals(ticket.offerId, offerId) || !Objects.equals(ticket.employeeId, employeeId)) {
            throw new IllegalStateException("Offer is no longer valid for this employee");
        }
    }

    private Ticket find(String refillId) {
        Ticket ticket = tickets.get(refillId);
        if (ticket == null) {
            throw new IllegalArgumentException("Unknown refill");
        }
        return ticket;
    }
}