package uz.nonvoy.bot.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.OrderItem;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.telegram.OrderAction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Guruhga tushadigan buyurtma kartasini matn va tugmalarga aylantiradi.
 * Karta status o'zgarganda qayta chiziladi — shuning uchun matn ham, tugmalar ham
 * faqat Order holatidan va auditoriyadan kelib chiqadi (hech qanday tashqi holat saqlanmaydi).
 */
public final class OrderCardFormatter {

    private OrderCardFormatter() {
    }

    public static String card(Order order, List<OrderItem> items, CardAudience audience) {
        StringBuilder sb = new StringBuilder();
        sb.append(header(order, audience)).append('\n');

        if (audience == CardAudience.PAYMENT) {
            sb.append(customerLine(order.getUser())).append('\n');
        }

        for (OrderItem item : items) {
            sb.append("🍞 ")
                    .append(itemName(item))
                    .append(" × ")
                    .append(item.getQuantity());
            if (audience == CardAudience.PAYMENT) {
                sb.append(" — ")
                        .append(PriceFormatter.formatPrice(lineTotal(item)))
                        .append(" so'm");
            }
            sb.append('\n');
        }

        // Kassa chekdagi summani karta bilan solishtiradi, shuning uchun "Jami" bitta
        // pozitsiyada ham ko'rsatiladi. Ishchilarga pul umuman kerak emas.
        if (audience == CardAudience.PAYMENT) {
            sb.append("💰 Jami: ")
                    .append(PriceFormatter.formatPrice(order.getTotalAmountMoney()))
                    .append(" so'm\n");
        }

        return sb.toString().stripTrailing();
    }

    /**
     * Har guruhda bitta oldinga siljituvchi tugma: kassada ✅ (NEW→ACCEPTED),
     * ishchilarda 🍞 (ACCEPTED→READY). Bekor qilish faqat kassada (12-qaror).
     * Yakuniy statuslarda tugma qolmaydi — karta o'z holatini o'zi ko'rsatadi.
     */
    public static InlineKeyboardMarkup keyboard(Order order, CardAudience audience) {
        return keyboard(order, audience, null);
    }

    /**
     * @param paymentMessageId kassa kartasining messageId'si. Faqat ishchilar tugmasiga yoziladi:
     *                         non tayyor bo'lganda kassa kartasi ham yopiladi (35-qaror).
     *                         Noma'lum bo'lsa null — kassa kartasi yangilanmaydi, xolos
     */
    public static InlineKeyboardMarkup keyboard(Order order, CardAudience audience, Integer paymentMessageId) {
        List<OrderAction> actions = switch (audience) {
            case PAYMENT -> switch (order.getStatus()) {
                case NEW -> List.of(OrderAction.ACCEPT, OrderAction.CANCEL);
                case ACCEPTED -> List.of(OrderAction.CANCEL);
                case READY, CANCELLED -> List.<OrderAction>of();
            };
            case KITCHEN -> switch (order.getStatus()) {
                case ACCEPTED -> List.of(OrderAction.READY);
                case NEW, READY, CANCELLED -> List.<OrderAction>of();
            };
        };
        if (actions.isEmpty()) {
            return null;
        }
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (OrderAction action : actions) {
            row.add(InlineKeyboardButton.builder()
                    .text(action.getLabel())
                    .callbackData(action == OrderAction.READY
                            ? action.callbackData(order.getId(), paymentMessageId)
                            : action.callbackData(order.getId()))
                    .build());
        }
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row)
                .build();
    }

    private static String header(Order order, CardAudience audience) {
        String number = " Buyurtma #" + order.getId();
        return switch (order.getStatus()) {
            case NEW -> "🆕" + number + (audience == CardAudience.PAYMENT ? " — to'lov tekshirilmoqda" : "");
            case ACCEPTED -> "✅" + number
                    + (audience == CardAudience.PAYMENT ? " — to'lov tasdiqlandi" : " — tayyorlash kerak");
            case READY -> "🍞" + number + " — tayyor";
            case CANCELLED -> "❌" + number + " — bekor qilindi";
        };
    }

    private static String customerLine(User user) {
        StringBuilder sb = new StringBuilder("👤 ").append(user.getName());
        String phone = user.getPhone();
        if (phone != null && !phone.isBlank()) {
            sb.append(" (").append(normalizePhone(phone)).append(')');
        }
        return sb.toString();
    }

    /** Telegram contact raqamni "+" bilan ham, "+"siz ham beradi — nusxa olish uchun bir xil ko'rinishga keltiramiz. */
    private static String normalizePhone(String phone) {
        String trimmed = phone.trim();
        return trimmed.startsWith("+") ? trimmed : "+" + trimmed;
    }

    /**
     * Nom buyurtma paytida muzlatiladi. Eski qatorlarda (snapshot kiritilgunga qadar)
     * u yo'q, shuning uchun mahsulotning o'zi zaxira sifatida qoladi.
     */
    private static String itemName(OrderItem item) {
        String frozen = item.getProductNameAtOrder();
        if (frozen != null && !frozen.isBlank()) {
            return frozen;
        }
        return item.getProduct() == null ? "Mahsulot" : item.getProduct().getName();
    }

    private static BigDecimal lineTotal(OrderItem item) {
        return item.getPriceAtOrder().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}
