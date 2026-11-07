# ADR-0004 — Independent ledger reconciliation through deterministic replay

- **Status:** Accepted for the in-memory simulator
- **Date:** 2026-11-07
- **Related issues:** #20, #21
- **Follows:** ADR-0003

## Problem

The ledger now stores an ordered sequence of deposits, withdrawals, holds,
releases and trade postings. Merely recording entries does not prove that
the live account balances agree with what the journal implies. We need an
**independent and testable reconciliation mechanism** before deciding how
to persist events across process restarts.

## Decision

Implement `LedgerAuditor.replay(List<LedgerRecord>)`, a **pure, read-only**
function which reconstructs balance buckets from zero by applying each
record's immutable postings in strict sequence order starting at 1.

- Reject sequence gaps and duplicates.
- Validate each operation's canonical postings and check that each trade is
  balanced per asset.
- Apply postings with checked arithmetic, rejecting negative and overflowing
  available, held, or total balances.
- Return a detached, immutable `LedgerAuditReport`.
- Expose `Ledger.reconcile()` to compare replay-derived positions with the
  current account balances. This comparison holds the ledger monitor to
  prevent interleaving with normal ledger mutations.
- Include recovery-related regression and seeded randomized test coverage.

## Rationale

- The journal is meaningful only if it independently explains current balances.
- A deterministic, side-effect-free replay function is separately testable and
  could eventually serve as a foundation for rebuilding ledger balances from
  a **durably committed** event stream.
- Reconciliation can detect a missing event, arithmetic corruption or an
  internal mutation that bypasses the ledger recording path.
- We deliberately do **not** run an O(journal-length) audit on every order;
  that would impose growing latency on the matching hot path.

## Limits and non-goals

This change **does not create a durable journal**, replay order-book changes,
recover canceled/filled orders or consumed IDs, validate external deposits
against real custodial evidence, or make the system suitable for real funds.

The journal still begins in RAM, and its account-creation events are not
explicitly captured. Accounts with no movements are not reconstructed. Replay
of balances alone cannot prove the order book has consistent collateral or
that the economic meaning of every trade is correct.

Reconciliation is designed for the single-writer, in-memory development
iteration. Code with internal account mutation privileges can still change
balances behind the coordinator; this audit detects that after the fact,
but does not automatically prevent it.

## Next steps before issue #20 can close

- Define a canonical, versioned command/event schema for book, orders,
  ledger movements, accounts, and consumed command IDs.
- Record every committed state transition durably with checksums and ordering.
- Define acknowledgment, crash-recovery and idempotent retry semantics.
- Verify process-kill recovery at commit boundaries.
- Choose storage only after the above contracts are experimentally checked.
