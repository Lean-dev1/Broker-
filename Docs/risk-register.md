# Risk register — experimental financial simulator

| ID | Risk | Impact | Probability | Mitigation | Status |
|---|---|---|---|---|---|
| R-001 | Partial settlement / book mutation on exceptions | Critical | Medium | In-memory snapshots + rollback regression tests | Mitigated for synchronous single-writer errors only |
| R-002 | State loss on process crash or machine failure | Critical | High | Durable journal + recovery/replay design | OPEN |
| R-003 | External or concurrent mutation of shared accounts/book | Critical | High | Encapsulation, single-writer command queue, concurrency tests | OPEN |
| R-004 | Accounting errors and ambiguous integer asset scales | High | Medium | Specify tick size, lot size, amount scale; double-entry ledger | OPEN |
| R-005 | Arithmetic overflow | High | Medium | Math.addExact / multiplyExact and exception rollback | Partially mitigated |
| R-006 | Order ID replay or non-idempotent external requests | High | Medium | Stable command IDs and persistent deduplication | OPEN |
| R-007 | O(N) snapshots on every operation harming latency | High | High | Replace snapshots with validated execution plans + atomic commit | OPEN |
| R-008 | Scope expansion before core correctness | Medium | High | Milestones, acceptance criteria and CI gates | Active monitoring |
| R-009 | Unverified benchmark claims | Medium | Medium | Record JVM, CPU, input distribution, warmup and test mode | OPEN |
| R-010 | No access controls or immutable audit log | Critical for real-world use | High | Threat model, secure API, audit and access controls | NOT IN SCOPE |

These are engineering risks, not evidence of regulatory compliance or financial
suitability. The code is a local simulator and must not handle real funds.
