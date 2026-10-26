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