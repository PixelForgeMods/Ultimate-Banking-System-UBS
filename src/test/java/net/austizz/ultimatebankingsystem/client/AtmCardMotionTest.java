package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;
import net.austizz.ultimatebankingsystem.atm.AtmSoundTiming;
import static org.junit.jupiter.api.Assertions.*;

class AtmCardMotionTest {
    @Test void escapeWaitsThroughTheMotorThenFocusesOnlyForTheHandPickup() {
        var motion = new AtmCardMotion();
        motion.request(0); motion.accept(0);
        assertTrue(motion.focusesCamera(), "Keep the existing insertion focus");
        motion.advance(AtmCardMotion.INSERT_NANOS);
        long escape = AtmCardMotion.INSERT_NANOS + 1;
        motion.remove(escape);
        assertEquals(AtmCardMotion.Phase.EJECTING, motion.phase);
        assertFalse(motion.focusesCamera(), "The return motor must not play an extra camera turn before pickup");
        motion.advance(escape + AtmCardMotion.EJECT_NANOS);
        assertEquals(AtmCardMotion.Phase.REMOVING, motion.phase);
        assertTrue(motion.focusesCamera(), "Focus the reader once while the hand collects the card");
        motion.advance(escape + AtmCardMotion.EJECT_NANOS + AtmCardMotion.REMOVE_NANOS);
        assertFalse(motion.focusesCamera(), "Return to the player's camera after pickup");
    }
    @Test void cardGuideIsLitOnlyWhenCardPhysicallyOccupiesReader() {
        var motion = new AtmCardMotion();
        assertFalse(motion.inReader(0));
        motion.request(0); motion.accept(0);
        assertFalse(motion.inReader(0));
        assertTrue(motion.inReader(AtmCardMotion.INSERT_NANOS));
        motion.advance(AtmCardMotion.INSERT_NANOS);
        assertTrue(motion.inReader(AtmCardMotion.INSERT_NANOS));
        motion.presentCard(1);
        assertTrue(motion.inReader(AtmCardMotion.INSERT_NANOS));
        motion.remove(AtmCardMotion.INSERT_NANOS);
        assertFalse(motion.inReader(AtmCardMotion.INSERT_NANOS + AtmCardMotion.REMOVE_NANOS));
    }
    @Test void returnedCardProtrudesWithoutHandAndPickupStartsAtTheSamePosition() {
        var motion = new AtmCardMotion();
        motion.request(0); motion.accept(0); motion.advance(AtmCardMotion.INSERT_NANOS);
        assertFalse(motion.sample(AtmCardMotion.INSERT_NANOS).visible());
        motion.presentCard(.5);
        var halfway = motion.sample(AtmCardMotion.INSERT_NANOS);
        motion.presentCard(1);
        var offered = motion.sample(AtmCardMotion.INSERT_NANOS);
        assertTrue(offered.visible());
        assertFalse(offered.handVisible());
        assertTrue(offered.cardZ() < halfway.cardZ());
        assertEquals(.015, offered.cardZ(), 1e-9);
        motion.remove(AtmCardMotion.INSERT_NANOS);
        var pickup = motion.sample(AtmCardMotion.INSERT_NANOS);
        assertEquals(offered.cardZ(), pickup.cardZ(), 1e-9, "Card must not jump back inside when clicked");
        assertTrue(pickup.handVisible());
        motion.reset();
        assertFalse(motion.sample(0).visible());
    }
    @Test
    void requiresValidationAndCompletesInsertionThenRemovalExactlyOnce() {
        var motion = new AtmCardMotion();
        assertFalse(motion.accept(0));
        assertFalse(motion.remove(0));
        assertTrue(motion.request(0));
        assertFalse(motion.request(1));
        assertTrue(motion.busy());
        assertFalse(motion.sample(100).visible());
        assertTrue(motion.accept(100));
        assertFalse(motion.advance(AtmCardMotion.INSERT_NANOS));
        assertTrue(motion.advance(AtmCardMotion.INSERT_NANOS + 100));
        assertEquals(AtmCardMotion.Phase.INSERTED, motion.phase);
        assertFalse(motion.sample(AtmCardMotion.INSERT_NANOS + 100).visible());
        assertFalse(motion.advance(AtmCardMotion.INSERT_NANOS + 101));
        long removal = AtmCardMotion.INSERT_NANOS + 1_000_000_000L;
        assertTrue(motion.remove(removal));
        assertEquals(AtmCardMotion.Phase.EJECTING, motion.phase);
        assertFalse(motion.advance(removal + AtmCardMotion.EJECT_NANOS - 1));
        assertTrue(motion.advance(removal + AtmCardMotion.EJECT_NANOS));
        assertEquals(AtmCardMotion.Phase.REMOVING, motion.phase);
        assertTrue(motion.advance(removal + AtmCardMotion.EJECT_NANOS + AtmCardMotion.REMOVE_NANOS));
        assertEquals(AtmCardMotion.Phase.EMPTY, motion.phase);
        assertFalse(motion.busy());
    }

    @Test
    void cardAlignsWithMouthAndHandStopsBeforeItEntersTheMachine() {
        var motion = new AtmCardMotion();
        motion.request(0); motion.accept(0);
        var aligned = motion.sample(AtmSoundTiming.nanos(AtmSoundTiming.CARD_APPROACH));
        assertEquals(0, aligned.cardX(), 1e-9);
        assertEquals(0, aligned.cardY(), 1e-9);
        assertEquals(-.0628, aligned.cardZ(), 1e-9);
        for (int frame = 0; frame <= 180; frame++) {
            var sample = motion.sample(AtmCardMotion.INSERT_NANOS * frame / 180);
            assertTrue(sample.visible());
            assertTrue(sample.handZ() <= -.025 + 1e-9, "Hand must not follow the card inside the slot");
            assertTrue(Double.isFinite(sample.cardX() + sample.cardY() + sample.cardZ()));
        }
        var inserted = motion.sample(AtmCardMotion.INSERT_NANOS);
        assertTrue(inserted.cardZ() - .0428 > 0, "Even the card's trailing edge must enter the reader");
        motion.advance(AtmCardMotion.INSERT_NANOS);
        motion.remove(AtmCardMotion.INSERT_NANOS);
        var start = motion.sample(AtmCardMotion.INSERT_NANOS);
        assertEquals(inserted.cardZ(), start.cardZ(), 1e-9);
        assertFalse(start.handVisible(), "Wait for the return motor before reaching for the card");
        var offered = motion.sample(AtmCardMotion.INSERT_NANOS + AtmCardMotion.EJECT_NANOS);
        motion.advance(AtmCardMotion.INSERT_NANOS + AtmCardMotion.EJECT_NANOS);
        assertEquals(offered.cardZ(), motion.sample(AtmCardMotion.INSERT_NANOS + AtmCardMotion.EJECT_NANOS).cardZ(), 1e-9);
        var out = motion.sample(AtmCardMotion.INSERT_NANOS + AtmCardMotion.EJECT_NANOS + (long) (AtmCardMotion.REMOVE_NANOS * .68));
        assertEquals(0, out.cardX(), 1e-9);
        assertEquals(0, out.cardY(), 1e-9);
        assertTrue(out.cardZ() + .0428 < 0, "The entire card clears the mouth before lowering");
    }

    @Test
    void interruptedRequestsAndAnimationsResetWithoutLeavingAStoredCard() {
        var motion = new AtmCardMotion();
        motion.request(0); motion.reset();
        assertFalse(motion.accept(10));
        motion.request(20); motion.accept(30); motion.reset();
        assertEquals(AtmCardMotion.Phase.EMPTY, motion.phase);
        assertFalse(motion.sample(100).visible());
        assertFalse(motion.advance(Long.MAX_VALUE));
    }
}
