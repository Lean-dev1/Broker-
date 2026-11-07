# Quality attributes and verification plan

| Attribute | Current property | Verification |
|---|---|---|
| Determinism | Caller-injected timestamp, single-writer matching | Reproducible seeded tests |
| Price/time priority | Best eligible level, FIFO within a level | Unit tests |
| Order lifecycle | CREATED/OPEN/PARTIALLY_FILLED/FILLED/CANCELED | Transition tests |
| Time-in-force | GTC, IOC, FOK through coordinator | Regression tests |
| Quantity integrity | Positive fills, no overfills, quantity conservation | Invariant tests |
| Financial conservation | Base/quote totals preserved by successful internal trades | Fuzz tests |
| In-memory audit completeness | Each ledger deposit/withdraw/hold/release/trade produces ordered immutable entries | LedgerJournalTest |
| Ledger reconciliation | Independent replay of postings agrees with live balances | LedgerReconciliationTest and fuzz test |
| In-process command idempotency | Same command ID + immutable request returns the same receipt and financial effects exactly once in one coordinator | IdempotentSubmissionTest |
| Numeric safety | Available, held AND their combined total must fit in long | Overflow regression tests |
| Synchronous failure atomicity | Snapshot/restore book, balances, IDs, orders | Mid-settlement overflow regression |
| Crash durability/recovery | **NOT IMPLEMENTED** | Future write-ahead log/replay |
| Concurrent access safety | **NOT PROVIDED** outside coordinator | Future single-writer architecture |
| Performance | O(book + accounts) checkpoint per command | Benchmark and disclose overhead |

Passing CI is a minimum verification gate, not evidence of production readiness.
