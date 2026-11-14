package com.framsikt.snackcounter.service;

import static org.junit.jupiter.api.Assertions.*;

import com.framsikt.snackcounter.model.Office;
import com.framsikt.snackcounter.model.RefillStatus;
import com.framsikt.snackcounter.model.SnackCurrency;
import com.framsikt.snackcounter.model.Volunteer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RefillCoordinatorTest {
    private static final Duration OFFER = Duration.ofHours(2);
    private static final Duration COMPLETION = Duration.ofHours(24);
    private final MutableClock clock = new MutableClock();
    private final RefillCoordinator service = new RefillCoordinator(clock, OFFER, COMPLETION);

    private static Volunteer person(String id, Office office, boolean available) {
        return new Volunteer(id, office, office, SnackCurrency.NOK, available);
    }

    @Test
    void emptyReportRequiresAnOfficeAndNonBlankBoxId() {
        assertThrows(NullPointerException.class, () -> service.reportEmpty(null, "box"));
        assertThrows(IllegalArgumentException.class, () -> service.reportEmpty(Office.OSLO, " "));
    }

    @Test
    void emptyToRefilledViaActualServiceAndDuplicateBoxProtection() {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        var offer = service.reportEmpty(Office.OSLO, "box");
        assertEquals(RefillStatus.OFFERED, offer.status());
        assertThrows(IllegalStateException.class, () -> service.reportEmpty(Office.OSLO, "box"));
        var accepted = service.accept(offer.id(), "alice", offer.offerId());
        assertEquals(RefillStatus.ACCEPTED, accepted.status());
        assertThrows(IllegalStateException.class, () -> service.markRefilled(offer.id(), "bob"));
        assertEquals(RefillStatus.PURCHASED, service.markRefilled(offer.id(), "alice").status());
        assertEquals(RefillStatus.OFFERED, service.reportEmpty(Office.OSLO, "box").status());
    }

    @Test
    void simultaneousAcceptancesHaveExactlyOneWinner() throws Exception {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        var offer = service.reportEmpty(Office.OSLO, "box");
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> tryAccept(offer, start));
            var second = executor.submit(() -> tryAccept(offer, start));
            start.countDown();
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(RefillStatus.ACCEPTED, service.get(offer.id()).status());
            assertEquals("alice", service.get(offer.id()).employeeId());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryAccept(RefillCoordinator.Snapshot offer, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.accept(offer.id(), "alice", offer.offerId());
            return true;
        } catch (IllegalStateException expected) {
            return false;
        }
    }

    @Test
    void twoDifferentVolunteersCannotBothAcceptOneRefill() throws Exception {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        service.upsertVolunteer(person("bob", Office.OSLO, true));
        var offer = service.reportEmpty(Office.OSLO, "box");
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var alice = executor.submit(() -> tryAcceptAs(offer, "alice", start));
            var bob = executor.submit(() -> tryAcceptAs(offer, "bob", start));
            start.countDown();
            assertTrue(alice.get(5, TimeUnit.SECONDS));
            assertFalse(bob.get(5, TimeUnit.SECONDS));
            assertEquals("alice", service.get(offer.id()).employeeId());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryAcceptAs(RefillCoordinator.Snapshot offer, String employeeId, CountDownLatch start)
            throws InterruptedException {
        start.await();
        try {
            service.accept(offer.id(), employeeId, offer.offerId());
            return true;
        } catch (IllegalStateException expected) {
            return false;
        }
    }

    @Test
    void acceptanceAtDeadlineExpiresOfferEvenWithoutSchedulerTick() {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        service.upsertVolunteer(person("bob", Office.OSLO, true));
        var offer = service.reportEmpty(Office.OSLO, "box");
        clock.advance(OFFER);
        assertThrows(IllegalStateException.class, () -> service.accept(offer.id(), "alice", offer.offerId()));
        assertEquals("bob", service.get(offer.id()).employeeId());
    }

    @Test
    void declineAndTimeoutHandOffAndRejectStaleOffers() {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        service.upsertVolunteer(person("bob", Office.OSLO, true));
        var alice = service.reportEmpty(Office.OSLO, "box");
        var bob = service.decline(alice.id(), "alice", alice.offerId());
        assertEquals("bob", bob.employeeId());
        assertThrows(IllegalStateException.class, () -> service.accept(alice.id(), "alice", alice.offerId()));
        clock.advance(OFFER);
        assertEquals(RefillStatus.OPEN, service.expireAssignments().get(0).status());
        assertThrows(IllegalStateException.class, () -> service.accept(bob.id(), "bob", bob.offerId()));
        var retried = service.retryUnassigned(bob.id());
        assertEquals("alice", retried.employeeId());
        assertNotEquals(alice.offerId(), retried.offerId());
        assertThrows(IllegalStateException.class, () -> service.accept(alice.id(), "alice", alice.offerId()));
    }

    @Test
    void unfinishedAcceptedRefillIsOfferedToNextVolunteer() {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        service.upsertVolunteer(person("bob", Office.OSLO, true));
        var offer = service.reportEmpty(Office.OSLO, "box");
        service.accept(offer.id(), "alice", offer.offerId());
        clock.advance(COMPLETION);
        var next = service.expireAssignments().get(0);
        assertEquals("bob", next.employeeId());
        assertEquals(RefillStatus.OFFERED, next.status());
        assertThrows(IllegalStateException.class, () -> service.markRefilled(offer.id(), "alice"));
    }

    @Test
    void selectionSkipsUnavailableAndOtherOfficesAndCanRetry() {
        service.upsertVolunteer(person("alice", Office.OSLO, false));
        service.upsertVolunteer(person("bob", Office.BENGALURU, true));
        var open = service.reportEmpty(Office.OSLO, "box");
        assertEquals(RefillStatus.OPEN, open.status());
        assertNull(open.employeeId());
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        assertEquals("alice", service.retryUnassigned(open.id()).employeeId());
    }

    @Test
    void rotationAdvancesOnEachOfferAndWrapsAround() {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        service.upsertVolunteer(person("bob", Office.OSLO, true));
        var first = service.reportEmpty(Office.OSLO, "box-1");
        assertEquals("alice", first.employeeId());
        assertEquals("bob", service.reportEmpty(Office.OSLO, "box-2").employeeId());
        assertEquals("alice", service.reportEmpty(Office.OSLO, "box-3").employeeId());
    }

    @Test
    void rotationIsIndependentPerOfficeAndSkipsUnavailableVolunteers() {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        service.upsertVolunteer(person("bob", Office.OSLO, false));
        service.upsertVolunteer(person("carol", Office.OSLO, true));
        service.upsertVolunteer(person("david", Office.BENGALURU, true));
        service.upsertVolunteer(person("eva", Office.BENGALURU, true));
        assertEquals("alice", service.reportEmpty(Office.OSLO, "oslo-1").employeeId());
        assertEquals("david", service.reportEmpty(Office.BENGALURU, "blr-1").employeeId());
        assertEquals("carol", service.reportEmpty(Office.OSLO, "oslo-2").employeeId());
        assertEquals("eva", service.reportEmpty(Office.BENGALURU, "blr-2").employeeId());
    }

    @Test
    void unavailableOfferedVolunteerIsImmediatelyReplacedAndOldOfferIsInvalid() {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        service.upsertVolunteer(person("bob", Office.OSLO, true));
        var alice = service.reportEmpty(Office.OSLO, "box");
        var updates = service.upsertVolunteer(person("alice", Office.OSLO, false));
        assertEquals(1, updates.size());
        assertEquals(alice.id(), updates.get(0).id());
        assertEquals("bob", updates.get(0).employeeId());
        assertNotEquals(alice.offerId(), updates.get(0).offerId());
        assertThrows(IllegalStateException.class, () -> service.accept(alice.id(), "alice", alice.offerId()));
        assertEquals(RefillStatus.ACCEPTED,
                service.accept(alice.id(), "bob", updates.get(0).offerId()).status());
    }

    @Test
    void officeChangeLeavesRefillOpenIfNobodyElseIsEligible() {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        var offer = service.reportEmpty(Office.OSLO, "box");
        var updates = service.upsertVolunteer(person("alice", Office.BENGALURU, true));
        assertEquals(1, updates.size());
        assertEquals(RefillStatus.OPEN, updates.get(0).status());
        assertNull(updates.get(0).employeeId());
        assertThrows(IllegalStateException.class, () -> service.accept(offer.id(), "alice", offer.offerId()));
        service.upsertVolunteer(person("bob", Office.OSLO, true));
        assertEquals("bob", service.retryUnassigned(offer.id()).employeeId());
    }

    @Test
    void availabilityChangeDoesNotAutomaticallyReassignAcceptedWork() {
        service.upsertVolunteer(person("alice", Office.OSLO, true));
        service.upsertVolunteer(person("bob", Office.OSLO, true));
        var offer = service.reportEmpty(Office.OSLO, "box");
        service.accept(offer.id(), "alice", offer.offerId());
        assertTrue(service.upsertVolunteer(person("alice", Office.OSLO, false)).isEmpty());
        assertEquals(RefillStatus.ACCEPTED, service.get(offer.id()).status());
        assertEquals("alice", service.get(offer.id()).employeeId());
        assertEquals(RefillStatus.PURCHASED, service.markRefilled(offer.id(), "alice").status());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T10:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}