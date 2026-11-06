# Requirements — In-Memory Trading Simulator

All requirements below are for **simulation** only.

## Functional Requirements and Acceptance Criteria

### FR-001 — Submit a valid limit order
- The user supplies a positive limit price and quantity, participant ID, side
  (through BuyOrder/SellOrder), and an order ID.
- The coordinator requires an existing participant account and sufficient collateral.
- **AC-001.1:** Invalid or insufficiently funded orders leave book and account
  balances unchanged and do not consume their ID.
- **AC-001.2:** A successfully processed order ID cannot be reused.
- **AC-001.3:** An incoming order that would cross a same-participant order is rejected.

### FR-002 — Match at price/time priority
- Match against the best eligible resting price first, then the oldest order
  at that price. Execution price is the resting order's price.
- **AC-002.1:** A buy limit below the best ask executes zero trades.
- **AC-002.2:** When multiple sell orders at the same price are available,
  they execute FIFO with correct partial quantities.
- **AC-002.3:** Every trade returns IDs, positive quantity, execution price,
  and the caller-supplied timestamp.

### FR-003 — Time in force
- **AC-003.1 GTC:** Any remaining quantity rests in the order book and keeps
  the necessary asset collateral held.
- **AC-003.2 IOC:** Execute available liquidity, cancel the remainder immediately,
  release the unused collateral, and leave no IOC order in the book.
- **AC-003.3 FOK:** Execute 100% immediately across eligible price levels or
  execute nothing, cancel everything, and preserve the existing book.

### FR-004 — Cancel a live order
- **AC-004.1:** Cancellation removes the order from the book, releases the
  remaining collateral and sets status to CANCELED.
- **AC-004.2:** Cancellation of unknown/already terminal IDs returns false.

### FR-005 — In-memory failure safety
- **AC-005.1:** If settlement throws after matching has changed state, the
  book, orders, accounts and order-ID registry are restored.
- **AC-005.2:** Rollback after the first of multiple executions restores
  original FIFO order and all affected account balances.
- **AC-005.3:** A failed/rolled-back submission may retry with the same ID.

## Nonfunctional Requirements

- **NFR-001 Determinism:** same initial state and input order yields the same
  execution list and final book state, independent of wall clock.
- **NFR-002 Technology isolation:** core matcher uses no database, web or
  networking framework.
- **NFR-003 Quantity and balance integrity:** no negative balances, negative
  fills or overfills; arithmetic overflow throws rather than wrapping.
- **NFR-004 Single-writer constraint:** this iteration is not safe for
  concurrent independent access to book, order or account instances.
- **NFR-005 No durable claims:** rollback only handles synchronous errors
  in the current JVM. No persistence, crash recovery or multi-node commit.

## Test strategy

JUnit regression tests cover acceptance criteria and known rollback failures.
Seeded fuzz tests exercise trade conservation. Benchmarks must disclose the
input distribution, system hardware, JVM options and snapshot overhead.
Capture any discovered invariant failure as a reproducible unit test.
