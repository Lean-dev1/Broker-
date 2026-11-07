# ADR-0001 — Immutable balanced postings for simulated trade settlement

- **Status:** Accepted for the current in-memory simulator
- **Date:** 2026-11-06
- **Related:** issue #21

## Context

Initially `BuyOrder` and `SellOrder` directly changed account balances through
`settleExecution`, and the coordinator could not inspect a structured,
immutable record of what happened during an execution.

## Decision

Each simulated `Trade` is represented by a `LedgerTransaction` carrying
immutable `LedgerPosting` entries. A posting contains a participant ID, an
asset identifier, a balance bucket (`AVAILABLE` or `HELD`) and a signed
integer delta. Before applying the entries, the ledger:

1. Verifies trade/order identities and compatible execution prices.
2. Uses checked integer arithmetic for all multiplied prices and quantities.
3. Rejects transactions whose postings do not sum to zero **per asset**.
4. Preflights all resulting account/bucket balances for negatives and overflow.
5. Applies the postings and stores the immutable transaction in memory.

For a buy of 10 shares at limit 110 with execution at 100:

- Buyer ARS held: -1100
- Seller ARS available: +1000
- Buyer ARS available: +100 (price improvement refund)
- Seller shares held: -10
- Buyer shares available: +10

Both ARS and shares have a net change of zero. No arithmetic mixes assets.

The coordinator checkpoint also records the journal size and next sequence,
so failed submissions roll back both balances **and** new journal entries.

## Rationale

- Makes simulated trade settlement inspectable and testable.
- Detects unbalanced transfers before mutating accounts.
- Separates financial postings from order-side matching logic.
- Provides an in-memory data model for a future durable event journal.

## Non-goals / limitations

**This is NOT a complete production double-entry ledger.** Deposits,
withdrawals, reservation and release are not yet journaled, and the account
API still permits direct mutations. There is no durable storage, replay,
cross-process isolation or external transaction ID. Sequences are local to
one Ledger instance and reset on restart.

`long` represents integer minor units but instrument-specific scales, tick
sizes, lot sizes and rounding rules remain unspecified and must be designed
before integrating real assets or persistence.

The in-memory rollback snapshot is O(book + accounts), and adds significant
per-command allocation. No claim of real-money suitability is made.

## Consequences and follow-up

Before closing issue #21, record ALL balance movements through the same
controlled accounting boundary, constrain external mutation, and establish
explicit per-instrument integer scales and accounting invariants.
