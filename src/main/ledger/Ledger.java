package ledger;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory ledger managing all participant accounts in O(1) lookup time.
 */
public class Ledger {
    private final Map<String, Account> accounts;

    public Ledger() {
        this.accounts = new HashMap<>();
    }

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
        if (account == null) {
            throw new IllegalArgumentException("Account not found for participant: " + participantId);
        }
        return account;
    }
}