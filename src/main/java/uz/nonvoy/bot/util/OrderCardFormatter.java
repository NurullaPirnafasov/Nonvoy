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
 * Admin guruhga tushadigan buyurtma kartasini matn va tugmalarga aylantiradi.
 * Karta status o'zgarganda qayta chiziladi — shuning uchun matn ham, tugmalar ham
 * faqat Order holatidan kelib chiqadi (hech qanday tashqi holat saqlanmaydi).
 */
public final class OrderCardFormatter {

    private OrderCardFormatter() {
    }

    public static String card(Order order, List<OrderItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(header(order)).append('\n');
        sb.append(customerLine(order.getUser())).append('\n');

        for (OrderItem item : items) {
            sb.append("🍞 ")
                    .append(item.getProduct().getName())
                    .append(" × ")
                    .append(item.getQuantity())
                    .append(" — ")
                    .append(PriceFormatter.formatPrice(lineTotal(item)))
                    .append(" so'm\n");
        }

        // Bitta mahsulotda "Jami" qatori yuqoridagi qatorni takrorlaydi — faqat
        // bir nechta pozitsiya bo'lganda ma'no beradi
        if (items.size() > 1) {
            sb.append("💰 Jami: ")
                    .append(PriceFormatter.formatPrice(order.getTotalAmountMoney()))
                    .append(" so'm\n");
        }

        return sb.toString().stripTrailing();
    }

    /**
     * Statusga mos tugmalar. Yakuniy statuslarda (READY, CANCELLED) tugma qolmaydi —
     * shunda karta o'z holatini o'zi ko'rsatadi va tasodifiy bosish imkoni yo'q.
     */
    public static InlineKeyboardMarkup keyboard(Order order) {
        List<OrderAction> actions = switch (order.getStatus()) {
            case NEW -> List.of(OrderAction.ACCEPT, OrderAction.CANCEL);
            case ACCEPTED -> List.of(OrderAction.READY, OrderAction.CANCEL);
            case READY, CANCELLED -> List.of();
        };
        if (actions.isEmpty()) {
            return null;
        }
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (OrderAction action : actions) {
            row.add(InlineKeyboardButton.builder()
                    .text(action.getLabel())
                    .callbackData(action.callbackData(order.getId()))
                    .build());
        }
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row)
                .build();
    }

    private static String header(Order order) {
        return switch (order.getStatus()) {
            case NEW -> "🆕 Buyurtma #" + order.getId();
            case ACCEPTED -> "✅ Buyurtma #" + order.getId() + " — qabul qilindi";
            case READY -> "🍞 Buyurtma #" + order.getId() + " — tayyor";
            case CANCELLED -> "❌ Buyurtma #" + order.getId() + " — bekor qilindi";
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

    private static BigDecimal lineTotal(OrderItem item) {
        return item.getPriceAtOrder().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}