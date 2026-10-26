1. Financial Domain Invariants

   - These are the physical laws of our system. If any of them are violated, the software fails 
     critically:


   - Conservation of Quantity: The original quantity of an order must always equal the sum of 
its 
     executed_quantity + remaining_quantity_in_order_book + canceled_quantity.


   - Crossing Rule (Positive Spread): The best bid price in the open order book can never be greater 
    than or equal to the best ask price. If it is, the engine has failed to execute a trade.


   - No (Internal) Wash Trading: An order cannot match against another order from the same 
     participant.


   - Execution Priority: A buyer will never pay a price higher than their limit price, and a   seller 
    will never receive a price lower than their ask price.