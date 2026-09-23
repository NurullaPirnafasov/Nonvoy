package uz.nonvoy.bot.telegram;

import lombok.Getter;
import uz.nonvoy.bot.entity.enums.OrderStatus;

import java.util.Optional;

/**
 * Admin guruhdagi buyurtma kartasi tugmalari.
 * Callback data formati: ACTION:orderId (masalan "ACCEPT:47").
 * <p>
 * Ishchilar kartasidagi {@code READY} kassa kartasining messageId'sini ham olib yuradi:
 * "READY:47:812". Shu tufayli non tayyor bo'lganda kassa kartasi ham yopiladi, bazada
 * messageId saqlanmaydi (35-qaror). Uchinchi qism ixtiyoriy — {@code /buyurtmalar} bilan
 * qayta chiqarilgan karta va deploy'dan oldingi tugmalarda u yo'q.
 */
@Getter
public enum OrderAction {
    ACCEPT("✅ To'lov tasdiqlandi", OrderStatus.ACCEPTED),
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

    /** @param paymentMessageId kassa kartasining messageId'si; null bo'lsa ikki qismli format */
    public String callbackData(Long orderId, Integer paymentMessageId) {
        return paymentMessageId == null
                ? callbackData(orderId)
                : callbackData(orderId) + SEPARATOR + paymentMessageId;
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

    /** Kassa kartasining messageId'si — faqat uch qismli formatda bor. */
    public static Optional<Integer> parsePaymentMessageId(String data) {
        Optional<String[]> parts = splitData(data);
        if (parts.isEmpty() || parts.get().length < 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(parts.get()[2]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<String[]> splitData(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        String[] parts = data.split(SEPARATOR, 3);
        if (parts.length < 2) {
            return Optional.empty();
        }
        for (String part : parts) {
            if (part.isBlank()) {
                return Optional.empty();
            }
        }
        return Optional.of(parts);
    }
}