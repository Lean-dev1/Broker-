1. Initial Scope and Explicit Exclusions


To protect the initial phase from infrastructure complexity, we set strict limits.

Included in Iteration 1:

   - In-memory engine written in pure Java (POJOs), with no external dependencies.

   - Limited order types (Limit Orders) with standard validity (Good 'Til Canceled).
    
   - Order book with market depth (L2).
    
   - FIFO matching algorithm (Price-Time Priority).
    
   - Cancellation of queued orders.
    
Explicit Exclusions (Will not be implemented in Iteration 1):
   
   - Database (PostgreSQL is outside the core logic).
   
   - Web frameworks (Spring Boot will not handle matching; this will be a subsequent upper layer).
   
   - Complex orders (Market Orders, Stop Loss, Iceberg, FOK).
   
   - Balance and account validation (we assume the order placer has sufficient funds; the ledger will be built in Iteration 2).
   
   - Multithreaded concurrency in the matching core (the initial engine must be single-threaded to ensure determinism; concurrent routing will be handled in higher layers).

## Implementation status note (2026-11-05)

The repository includes experimental ledger/coordinator components in addition
to the original Iteration 1 scope. `TradingCoordinator` currently supports
GTC, IOC and FOK; standalone `MatchingEngine` retains GTC behavior.
Executed trades and all balance movements (deposit, withdrawal, hold, release) now
produce immutable, chronologically sequenced in-memory journal records.
External deposits/withdrawals are explicit external flows, not balanced internal trades.
This is **not** a full general ledger, persistent journal or production-ready
implementation. Data durability, crash recovery and multi-process isolation
remain out of scope for this iteration.
