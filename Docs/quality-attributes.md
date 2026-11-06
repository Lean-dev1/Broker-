# Quality attributes and verification plan

| Attribute | Desired property | Verification |
|---|---|---|
| Determinism | Same initial state + input sequence => same executions and final book | Repeatable seeded tests |
| Price/time priority | Best eligible price then FIFO at equal price | Unit tests |
| Quantity integrity | Only strictly positive fill quantities; never overfill | Invariant tests |
| Financial conservation | Total funds per asset conserved after internal trades | Fuzz tests |
| Atomicity | No partially committed matching + settlement | **NOT MET**; design required |
| Recovery | Restart without loss/duplication | **NOT IMPLEMENTED** |
| Performance | Benchmarked under stated hardware and workload | JMH + documented environment |

Do not interpret the presence of benchmarks as evidence of production-level latency.
