package main.ledger;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Package-scoped fault injection: simulates an internal bypass of Ledger.
 * External client code cannot access Account.applyPosting.
 */
class LedgerTamperDetectionTest {
    @Test
    void reconciliationDetectsUnauditedInternalBalanceMutation() {
        Ledger ledger = new Ledger();
        Account account = ledger.createAccount("p");
        ledger.deposit("p", "ARS", 100L);
        assertEquals(100L,
                ledger.reconcile().balances().get(new AccountAssetKey("p", "ARS")).available());

        account.applyPosting(new LedgerPosting("p", "ARS", BalanceBucket.AVAILABLE, 1L));

        IllegalStateException failure = assertThrows(IllegalStateException.class, ledger::reconcile);
        assertTrue(failure.getMessage().contains("reconciliation mismatch"));
        assertEquals(1, ledger.getJournal().size());
    }
}
