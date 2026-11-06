# Quality attributes and verification plan

| Attribute | Current property | Verification |
|---|---|---|
| Determinism | Caller-injected timestamp, single-writer matching | Reproducible seeded tests |
| Price/time priority | Best eligible level, FIFO within a level | Unit tests |
| Order lifecycle | CREATED/OPEN/PARTIALLY_FILLED/FILLED/CANCELED | Transition tests |
| Time-in-force | GTC, IOC, FOK through coordinator | Regression tests |
| Quantity integrity | Positive fills, no overfills, quantity conservation | Invariant tests |
| Financial conservation | Base/quote totals preserved by successful internal trades | Fuzz tests |
| Synchronous failure atomicity | Snapshot/restore book, balances, IDs, orders | Mid-settlement overflow regression |
| Crash durability/recovery | **NOT IMPLEMENTED** | Future write-ahead log/replay |
| Concurrent access safety | **NOT PROVIDED** outside coordinator | Future single-writer architecture |
| Performance | O(book + accounts) checkpoint per command | Benchmark and disclose overhead |

Passing CI is a minimum verification gate, not evidence of production readiness.
