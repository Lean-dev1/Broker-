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
        long tradeQuantity = 0L;
        for (Trade trade : trades) {
            if (trade.aggressiveOrderId() != orderId
                    || trade.timestamp() != timestamp
                    || trade.executedQuantity() <= 0) {
                throw new IllegalArgumentException("Receipt contains an unrelated trade");
            }
            tradeQuantity = Math.addExact(tradeQuantity, trade.executedQuantity());
        }
        if (tradeQuantity != executedQuantity) {
            throw new IllegalArgumentException("Receipt executed quantity differs from trades");
        }
        boolean lifecycleConsistent = switch (statusAtAcceptance) {
            case CREATED -> false;
            case OPEN -> executedQuantity == 0 && canceledQuantity == 0
                    && remainingQuantity > 0;
            case PARTIALLY_FILLED -> executedQuantity > 0 && canceledQuantity == 0
                    && remainingQuantity > 0;
            case FILLED -> executedQuantity == initialQuantity
                    && remainingQuantity == 0 && canceledQuantity == 0;
            case CANCELED -> canceledQuantity > 0 && remainingQuantity == 0;
        };
        if (!lifecycleConsistent) {
            throw new IllegalArgumentException("Receipt lifecycle and quantities disagree");
        }
    }
}
