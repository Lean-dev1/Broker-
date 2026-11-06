# Broker- — Financial Matching Engine

Experimental **in-memory financial market simulation** written in Java 17.
This repository is not a production broker, exchange, or financial custody system.

## Current modules
- Limit-order price/time matching with deterministic injected timestamps
- In-memory bid/ask order book and partial fills
- Account balance holds and simulated trade settlement
- Unit/fuzz tests and JMH benchmarks
- GTC-style resting behavior in the primary engine path

**Not production ready:** accounting and matching are not yet a single atomic transaction;
there is no recovery log, persistent journal, authentication, or audited risk engine.
IOC/FOK strategy classes are experimental and **not connected** to the main execution path.

## Build and test
Requires JDK 17+ and Maven 3.9+.

```bash
mvn clean test
mvn clean package
```

The project temporarily retains the original `src/main` and `tests` paths,
explicitly configured in `pom.xml`. A migration to the Maven standard
`src/main/java` and `src/test/java` is planned.

## Documentation
- [Vision](Docs/Vision.md)
- [Scope](Docs/Scope.md)
- [Requirements](Docs/Requirements.md)
- [Domain invariants](Docs/domain-model.md)
- [Risks](Docs/risk-register.md)

## Next engineering gates
1. Run and pass all tests in CI.
2. Specify order lifecycle and duplicate ID semantics.
3. Design a transactional, auditable order + ledger commit boundary.
4. Establish reproducible performance measurements before making speed claims.
