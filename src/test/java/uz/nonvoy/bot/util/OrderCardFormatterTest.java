package uz.nonvoy.bot.util;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.OrderItem;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderCardFormatterTest {

    @Test
    void cardShowsOrderNumberCustomerAndItems() {
        assertEquals(normalize("""
                🆕 Buyurtma #47
                👤 Alisher (+998901234567)
                🍞 Non × 10 — 50 000 so'm"""), cardFor(OrderStatus.NEW));
    }

    /** Narxdagi ajratgich uzilmas bo'shliq - Telegram tor ekranda "50 000" ni ikkiga bo'lmasin. */
    @Test
    void pricesUseNonBreakingSpace() {
        String raw = OrderCardFormatter.card(order(OrderStatus.NEW), List.of(item("Non", 10, 5000)));

        assertTrue(raw.contains("50" + (char) 0x00A0 + "000"), raw);
    }

    /** Kartaning birinchi qatori statusni ko'rsatadi - novvoy guruhni varaqlab ko'radi. */
    @Test
    void headerFollowsStatus() {
        assertTrue(cardFor(OrderStatus.NEW).startsWith("🆕 Buyurtma #47"));
        assertTrue(cardFor(OrderStatus.ACCEPTED).startsWith("✅ Buyurtma #47 — qabul qilindi"));
        assertTrue(cardFor(OrderStatus.READY).startsWith("🍞 Buyurtma #47 — tayyor"));
        assertTrue(cardFor(OrderStatus.CANCELLED).startsWith("❌ Buyurtma #47 — bekor qilindi"));
    }

    /** Bitta mahsulotda "Jami" qatori yuqoridagini takrorlaydi - shuning uchun yo'q. */
    @Test
    void totalLineAppearsOnlyWithSeveralItems() {
        assertFalse(cardFor(OrderStatus.NEW).contains("Jami"));

        Order order = order(OrderStatus.NEW);
        order.setTotalAmountMoney(BigDecimal.valueOf(70000));
        String card = normalize(OrderCardFormatter.card(order,
                List.of(item("Non", 10, 5000), item("Patir", 2, 10000))));

        assertTrue(card.contains("🍞 Patir × 2 — 20 000 so'm"));
        assertTrue(card.contains("💰 Jami: 70 000 so'm"));
    }

    @Test
    void missingPhoneIsOmitted() {
        Order order = order(OrderStatus.NEW);
        order.getUser().setPhone(null);

        String card = normalize(OrderCardFormatter.card(order, List.of(item("Non", 1, 5000))));

        assertTrue(card.contains("👤 Alisher\n"), card);
        assertFalse(card.contains("("));
    }

    @Test
    void newOrderOffersAcceptAndCancel() {
        assertEquals(List.of("ACCEPT:47", "CANCEL:47"), callbackData(OrderStatus.NEW));
    }

    @Test
    void acceptedOrderOffersReadyAndCancel() {
        assertEquals(List.of("READY:47", "CANCEL:47"), callbackData(OrderStatus.ACCEPTED));
    }

    /** Yakuniy statusda tugma qolmasligi kerak: tasodifan bosishning oldi olinadi. */
    @Test
    void finalStatusesHaveNoButtons() {
        assertNull(OrderCardFormatter.keyboard(order(OrderStatus.READY)));
        assertNull(OrderCardFormatter.keyboard(order(OrderStatus.CANCELLED)));
    }

    // --- Yordamchilar ---

    private String cardFor(OrderStatus status) {
        return normalize(OrderCardFormatter.card(order(status), List.of(item("Non", 10, 5000))));
    }

    /** Narx ajratgichi uzilmas bo'shliq - kutilgan matnni o'qishli yozish uchun oddiy probelga o'giramiz. */
    private String normalize(String card) {
        return card.replace((char) 0x00A0, ' ');
    }

    private List<String> callbackData(OrderStatus status) {
        InlineKeyboardMarkup markup = OrderCardFormatter.keyboard(order(status));
        assertEquals(1, markup.getKeyboard().size(), "tugmalar bitta qatorda bo'lsin");
        return markup.getKeyboard().get(0).stream()
                .map(InlineKeyboardButton::getCallbackData)
                .toList();
    }

    private Order order(OrderStatus status) {
        User user = User.builder().telegramId(123L).name("Alisher").phone("998901234567").build();
        Order order = Order.builder()
                .user(user)
                .status(status)
                .totalAmountMoney(BigDecimal.valueOf(50000))
                .build();
        order.setId(47L);
        return order;
    }

    private OrderItem item(String name, int quantity, long price) {
        return OrderItem.builder()
                .product(Product.builder().name(name).price(BigDecimal.valueOf(price)).available(true).build())
                .quantity(quantity)
                .priceAtOrder(BigDecimal.valueOf(price))
                .build();
    }
}