package main.engine;

import main.domain.OrderStatus;
import main.domain.TimeInForce;
import main.domain.Trade;

import java.util.List;
import java.util.Objects;

/**
 * Immutable response to an accepted idempotent submission. Values describe
 * the order at command acceptance, not its potentially newer live state.
 * Receipts exist only in RAM until durable command persistence is designed.
 */
public record SubmissionReceipt(
        String commandId,
        long orderId,
        TimeInForce timeInForce,
        long timestamp,
        OrderStatus statusAtAcceptance,
        long initialQuantity,
        long executedQuantity,
        long remainingQuantity,
        long canceledQuantity,
        List<Trade> trades) {

    public SubmissionReceipt {
        if (commandId == null || commandId.isBlank() || orderId < 0) {
            throw new IllegalArgumentException("Valid command and order IDs required");
        }
        Objects.requireNonNull(timeInForce, "timeInForce");
        Objects.requireNonNull(statusAtAcceptance, "statusAtAcceptance");
        trades = List.copyOf(Objects.requireNonNull(trades, "trades"));
        if (statusAtAcceptance == OrderStatus.CREATED
                || initialQuantity <= 0 || executedQuantity < 0
                || remainingQuantity < 0 || canceledQuantity < 0
                || Math.addExact(Math.addExact(executedQuantity, remainingQuantity),
                        canceledQuantity) != initialQuantity) {
            throw new IllegalArgumentException("Invalid accepted order quantity conservation");
        }
    }
}
