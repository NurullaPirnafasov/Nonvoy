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

    // --- Kassa kartasi ---

    @Test
    void paymentCardShowsCustomerItemsAndTotal() {
        assertEquals(normalize("""
                🆕 Buyurtma #47 — to'lov tekshirilmoqda
                👤 Alisher (+998901234567)
                🍞 Non × 10 — 50 000 so'm
                💰 Jami: 50 000 so'm"""), cardFor(OrderStatus.NEW, CardAudience.PAYMENT));
    }

    /** Kassa chekdagi summani karta bilan solishtiradi — "Jami" bitta pozitsiyada ham kerak. */
    @Test
    void paymentCardAlwaysShowsTotal() {
        assertTrue(cardFor(OrderStatus.NEW, CardAudience.PAYMENT).contains("💰 Jami:"));
    }

    /** Narxdagi ajratgich uzilmas bo'shliq - Telegram tor ekranda "50 000" ni ikkiga bo'lmasin. */
    @Test
    void pricesUseNonBreakingSpace() {
        String raw = OrderCardFormatter.card(order(OrderStatus.NEW), items(), CardAudience.PAYMENT);

        assertTrue(raw.contains("50" + (char) 0x00A0 + "000"), raw);
    }

    @Test
    void missingPhoneIsOmitted() {
        Order order = order(OrderStatus.NEW);
        order.getUser().setPhone(null);

        String card = normalize(OrderCardFormatter.card(order, items(), CardAudience.PAYMENT));

        assertTrue(card.contains("👤 Alisher\n"), card);
        assertFalse(card.contains("("));
    }

    // --- Ishchilar kartasi ---

    /** Ishchilarga nima yopish kerakligi kerak, pul ham, telefon ham emas (13-qaror). */
    @Test
    void kitchenCardHidesMoneyAndPhone() {
        String card = cardFor(OrderStatus.ACCEPTED, CardAudience.KITCHEN);

        assertEquals(normalize("""
                ✅ Buyurtma #47 — tayyorlash kerak
                🍞 Non × 10"""), card);
        assertFalse(card.contains("so'm"));
        assertFalse(card.contains("Alisher"));
    }

    /** Bir xil Order ikki guruhda ikki xil sarlavha oladi — yangi status qo'shilmaydi (10-qaror). */
    @Test
    void headerDependsOnAudience() {
        assertTrue(cardFor(OrderStatus.ACCEPTED, CardAudience.PAYMENT).startsWith("✅ Buyurtma #47 — to'lov tasdiqlandi"));
        assertTrue(cardFor(OrderStatus.ACCEPTED, CardAudience.KITCHEN).startsWith("✅ Buyurtma #47 — tayyorlash kerak"));
        assertTrue(cardFor(OrderStatus.READY, CardAudience.KITCHEN).startsWith("🍞 Buyurtma #47 — tayyor"));
        assertTrue(cardFor(OrderStatus.CANCELLED, CardAudience.KITCHEN).startsWith("❌ Buyurtma #47 — bekor qilindi"));
    }

    /**
     * Caption limiti 1024 belgi. 3-qarorga ko'ra savatda bir mahsulot bir marta uchraydi,
     * ya'ni qatorlar soni mahsulot turlari soni bilan chegaralangan.
     */
    @Test
    void captionStaysUnderTelegramLimit() {
        List<OrderItem> many = List.of(
                item("Qora bug'doy noni", 100, 12000),
                item("Kungaboqar urug'li patir", 100, 15000),
                item("Shirmoy non katta", 100, 9000),
                item("Sutli non uzun", 100, 8000),
                item("Tandir non kichik", 100, 5000));

        assertTrue(OrderCardFormatter.card(order(OrderStatus.NEW), many, CardAudience.PAYMENT).length() < 1024);
    }

    /**
     * Mahsulot o'chirilsa OrderItem'dagi ishora null bo'ladi (23-qaror) — eski karta
     * baribir to'liq chiqishi kerak, nom va narx unda muzlatilgan.
     */
    @Test
    void deletedProductStillRendersFromSnapshot() {
        OrderItem orphan = OrderItem.builder()
                .quantity(10)
                .productNameAtOrder("Shirmoy")
                .priceAtOrder(BigDecimal.valueOf(5000))
                .build();

        String card = normalize(OrderCardFormatter.card(order(OrderStatus.READY), List.of(orphan), CardAudience.PAYMENT));

        assertTrue(card.contains("🍞 Shirmoy × 10 — 50 000 so'm"), card);
    }

    // --- Tugmalar ---

    @Test
    void paymentGroupDrivesOrderForward() {
        assertEquals(List.of("ACCEPT:47", "CANCEL:47"), callbackData(OrderStatus.NEW, CardAudience.PAYMENT));
        assertEquals(List.of("CANCEL:47"), callbackData(OrderStatus.ACCEPTED, CardAudience.PAYMENT));
    }

    /** Ishchilarda faqat "Tayyor": bekor qilish — pul masalasi, faqat kassada (12-qaror). */
    @Test
    void kitchenGroupOnlyMarksReady() {
        assertEquals(List.of("READY:47"), callbackData(OrderStatus.ACCEPTED, CardAudience.KITCHEN));
        assertNull(OrderCardFormatter.keyboard(order(OrderStatus.NEW), CardAudience.KITCHEN));
    }

    /** Yakuniy statusda tugma qolmasligi kerak: tasodifan bosishning oldi olinadi. */
    @Test
    void finalStatusesHaveNoButtons() {
        for (CardAudience audience : CardAudience.values()) {
            assertNull(OrderCardFormatter.keyboard(order(OrderStatus.READY), audience));
            assertNull(OrderCardFormatter.keyboard(order(OrderStatus.CANCELLED), audience));
        }
    }

    // --- Yordamchilar ---

    private String cardFor(OrderStatus status, CardAudience audience) {
        return normalize(OrderCardFormatter.card(order(status), items(), audience));
    }

    /** Narx ajratgichi uzilmas bo'shliq - kutilgan matnni o'qishli yozish uchun oddiy probelga o'giramiz. */
    private String normalize(String card) {
        return card.replace((char) 0x00A0, ' ');
    }

    private List<String> callbackData(OrderStatus status, CardAudience audience) {
        InlineKeyboardMarkup markup = OrderCardFormatter.keyboard(order(status), audience);
        assertEquals(1, markup.getKeyboard().size(), "tugmalar bitta qatorda bo'lsin");
        return markup.getKeyboard().get(0).stream()
                .map(InlineKeyboardButton::getCallbackData)
                .toList();
    }

    private List<OrderItem> items() {
        return List.of(item("Non", 10, 5000));
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
                .product(Product.builder().name(name).price(BigDecimal.valueOf(price)).build())
                .quantity(quantity)
                .priceAtOrder(BigDecimal.valueOf(price))
                .build();
    }
}
