# Domain Model — Order Lifecycle and Invariants

The implementation currently supports an **in-memory simulated market**, not live assets.

## Entities and types

- `Order`: unique ID, participant, limit price, original quantity, executed quantity,
  remaining quantity, canceled quantity, and lifecycle status.
- `BuyOrder` and `SellOrder`: reserve quote and base assets, respectively.
- `OrderBook`: price-sorted levels (best bid descending, best ask ascending).
- `PriceLevel`: FIFO queue of resting orders at one limit price.
- `Trade`: immutable execution with order IDs, price, quantity, injected timestamp.
- `Account`, `AssetBalance`: available and held balances in integer minor units.

## Order states

`CREATED` -> `OPEN` when resting without executions.

`CREATED` or `OPEN` -> `PARTIALLY_FILLED` on a partial execution.

`CREATED`, `OPEN`, or `PARTIALLY_FILLED` -> `FILLED` on complete execution.

`CREATED`, `OPEN`, or `PARTIALLY_FILLED` -> `CANCELED` when
the remainder is canceled (by explicit cancellation, IOC, or unfilled FOK).
A partially executed IOC ends as `CANCELED` with a positive executed quantity.

`FILLED` and `CANCELED` are terminal states. Identifiers cannot be reused
after a successfully processed order, even if later canceled.

## Invariants

1. **Quantity conservation:** `original = executed + remaining + canceled`;
   every term is nonnegative. After cancellation the remaining quantity is zero.
2. **No crossed resting book:** best bid must be strictly below best ask when both exist.
3. **Price-time priority:** best eligible price first, FIFO within a price level.
4. **No internal self-trade:** the coordinator conservatively rejects an incoming
   order if it could interact with any eligible order from the same participant.
5. **Limit execution prices:** a buyer cannot pay above its limit; a seller
   cannot receive below its limit.
6. **Asset conservation in simulated internal trades:** total base and quote
   quantities across accounts are unchanged by a completed trade.
7. **Held-balance integrity:** accepted resting orders retain the required
   collateral; canceling their remainder releases it.
8. **Failure rollback (single-writer, in-memory only):** a synchronous exception
   in coordinator submission/cancellation restores the preceding book, order,
   ID registry, and account balances.

## Time-in-force

- **GTC**: matched quantity executes; remaining quantity rests in the book.
- **IOC**: matched quantity executes; the unfilled remainder is immediately canceled.
- **FOK**: either the entire order executes immediately or the whole quantity is
  canceled without executions.

## Explicit limitations

The in-memory snapshot is O(open orders + accounts × assets) for each mutation.
It is not a durable journal or a crash-safe transaction. External mutation of
accounts, orders, or book during processing is unsupported. Price precision
and instrument-specific tick/lot constraints are not specified yet.
