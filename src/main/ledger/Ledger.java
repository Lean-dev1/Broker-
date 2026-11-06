package main.ledger;

import java.util.HashMap;
import java.util.Map;

/** In-memory ledger of participant accounts. Single-writer; no persistent journal. */
public class Ledger {
    private final Map<String, Account> accounts = new HashMap<>();

    public Account createAccount(String participantId) {
        if (accounts.containsKey(participantId)) {
            throw new IllegalArgumentException("Account already exists for participant: " + participantId);
        }
        Account account = new Account(participantId);
        accounts.put(participantId, account);
        return account;
    }

    public Account getAccount(String participantId) {
        Account account = accounts.get(participantId);
        if (account == null) throw new IllegalArgumentException("Account not found: " + participantId);
        return account;
    }

    public static final class Snapshot {
        private final Map<Account, Account.Snapshot> accounts;
        private Snapshot(Map<Account, Account.Snapshot> accounts) { this.accounts = accounts; }
    }

    /** Copies every account for rollback. O(accounts × assets); not a production hot path. */
    public Snapshot snapshot() {
        Map<Account, Account.Snapshot> states = new HashMap<>();
        for (Account account : accounts.values()) states.put(account, account.snapshotState());
        return new Snapshot(states);
    }

    public void restore(Snapshot snapshot) {
        snapshot.accounts.forEach(Account::restoreState);
    }
}
