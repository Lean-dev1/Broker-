# ADR-0003 — Route all balance mutations through one in-memory ledger journal

- **Status:** Accepted for the experimental in-memory simulator
- **Date:** 2026-11-07
- **Related issues:** #21, #20
- **Prerequisite:** ADR-0001 (balanced trade postings)

## Problem

Prior to this change, account holders could call `Account.deposit`,
`withdraw`, `hold`, `release`, and `settle` directly; those changes
bypassed the trade-only posting journal. A crash recovery design requires
an ordered representation of **every** balance mutation, not just executions.

## Decision

1. Expose balance mutations through `Ledger.deposit`, `withdraw`,
   `hold` and `release`. Remove direct public balance mutation methods
   from `Account`, leaving read-only public balance queries.
2. Record immutable `LedgerOperation` entries for each command, and
   immutable `LedgerTransaction` entries for trade settlement.
3. Store both behind a `LedgerRecord` interface in one ordered in-memory
   journal with monotonic local sequence numbers.
4. A `HOLD` and a `RELEASE` each contain two equal and opposite postings
   in the same participant/asset (AVAILABLE ↔ HELD).
5. A `DEPOSIT` or `WITHDRAWAL` is explicitly labeled as an **external**
   inflow/outflow; it has one participant posting and is **not** falsely
   claimed to be zero-sum inside the local participant accounts.
6. Validate the prospective resulting bucket balances and checked arithmetic
   before making any mutation.
7. Preserve the coordinator's rollback behavior by restoring balances,
   journal length and sequence count after a failed order.
8. Serialize coordinator commands against ledger operations by synchronizing
   on the same ledger monitor. Calls that bypass the coordinator and directly
   mutate the book or orders remain unsafe.

## Why

- One chronological history allows us to inspect exactly which operation
  changed which balance, and supports future deterministic replay designs.
- Mixed `LedgerRecord` entries avoid a trade-only log hiding externally
  injected funds or collateral holds.
- Explicit external inflow/outflow semantics are more honest than inventing
  a fictional counterparty with unbounded negative balances.
- Retaining in-memory snapshots avoids prematurely claiming durability.

## Consequences

**Breaking API change:** tests and callers must use
`ledger.deposit(participantId, asset, amount)` etc., not `Account.deposit`.
Public read-only access via `Account` remains.

Journal records, posted balances and sequence numbers are still volatile.
External operations do not yet carry idempotency keys or an immutable
evidence reference. They are not regulated accounting records.

## Open questions before durable storage

- Should a future dedicated external-clearing account be modeled to create
  complete double-entry accounting across cash movement boundaries?
- What are the asset scales, settlement timing, and clearing workflow?
- Which command/event IDs guarantee idempotence after a restart?
- Do we persist canonical commands, validated effects, or both?
- How will commit acknowledgements, fsync, schema versioning and replay
  guarantee old-state-or-new-state recovery?

This ADR **does not** close #20 or #21. It provides an explicit, in-memory
foundation for evaluating durable replay and full double-entry accounting.
