package main.domain;

/** How long the unfilled quantity of an incoming order may remain active. */
public enum TimeInForce {
    /** Good 'til canceled: unfilled quantity rests in the order book. */
    GTC,
    /** Immediate or cancel: execute available liquidity, cancel the remainder. */
    IOC,
    /** Fill or kill: execute all immediately, or cancel without any executions. */
    FOK
}
