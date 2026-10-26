1. Functional Requirements
  
  - The system must receive order creation requests containing: participant ID, side (Buy/Sell), instrument (e.g., ticker), quantity, and limit price.
  
  - The system must execute an order immediately if there is a matching counterparty in the order book.
  
  - If the order is not fully matched, the remaining quantity must be queued in the order book, respecting the priority of best price and, at equal prices, the order of arrival (time).
  
  - The system must issue immutable Trade (execution) events detailing: the IDs of the orders involved, the execution price, the quantity executed, and the timestamp.
  
  - The system must allow the cancellation of open orders using their unique ID.



2. Non-functional requirements and quality attributes

   - Strict determinism: Given an exact sequence of order inputs, the final state of the
     book and the generated execution list must be identical 100% of the time.

   - Time Isolation (Testability): The engine must not read the system clock (System.
     currentTimeMillis()). The logical time or timestamp must be injected as a parameter at the input so that tests are reproducible.

   - Technology Isolation: The package containing the MatchingEngine and the OrderBook must not
     have a single import from Spring, databases, or network libraries.


3. Acceptance Criteria and Testing Strategy
   
   - Unit Testing (JUnit): Comprehensive coverage of an order’s state transitions.

   - Property-Based Testing: Instead of writing manual tests, we will generate thousands of random order sequences (fuzzing) and inject global assertions that continuously verify the  invariants from point 7.

   - Regression Capture: Any state that violates an invariant during a simulation will be  captured, serialized, and automatically converted into a permanent unit test.