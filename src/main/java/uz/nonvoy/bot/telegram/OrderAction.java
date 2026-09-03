package uz.nonvoy.bot.telegram;

import lombok.Getter;
import uz.nonvoy.bot.entity.enums.OrderStatus;

import java.util.Optional;

/**
 * Admin guruhdagi buyurtma kartasi tugmalari.
 * Callback data formati: ACTION:orderId (masalan "ACCEPT:47").
 */
@Getter
public enum OrderAction {
    ACCEPT("✅ Qabul", OrderStatus.ACCEPTED),
    READY("🍞 Tayyor", OrderStatus.READY),
    CANCEL("❌ Bekor", OrderStatus.CANCELLED);

    private final String label;
    private final OrderStatus status;

    private static final String SEPARATOR = ":";

    OrderAction(String label, OrderStatus status) {
        this.label = label;
        this.status = status;
    }

    public String callbackData(Long orderId) {
        return name() + SEPARATOR + orderId;
    }

    public static Optional<OrderAction> parse(String data) {
        Optional<String[]> parts = splitData(data);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        String prefix = parts.get()[0];
        for (OrderAction action : values()) {
            if (action.name().equals(prefix)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    public static Optional<Long> parseOrderId(String data) {
        Optional<String[]> parts = splitData(data);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(parts.get()[1]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<String[]> splitData(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        String[] parts = data.split(SEPARATOR, 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parts);
    }
}