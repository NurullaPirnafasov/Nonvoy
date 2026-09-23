package uz.nonvoy.bot.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.OrderStatus;
import uz.nonvoy.bot.service.OrderService;
import uz.nonvoy.bot.util.CardAudience;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCardServiceImplTest {

    private static final long PAYMENT_GROUP = -100L;
    private static final long WORKER_GROUP = -200L;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderCardServiceImpl orderCardService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderCardService, "paymentGroupId", PAYMENT_GROUP);
        ReflectionTestUtils.setField(orderCardService, "workerGroupId", WORKER_GROUP);
        lenient().when(orderService.findItems(any())).thenReturn(List.of());
    }

    @Test
    void paymentGroupGetsReceiptCardsForNewAndAccepted() {
        stubStatus(OrderStatus.NEW, 1, 2);
        stubStatus(OrderStatus.ACCEPTED, 3);

        List<PartialBotApiMethod<?>> result = orderCardService.openCards(CardAudience.PAYMENT);

        assertEquals(3, result.size());
        for (PartialBotApiMethod<?> method : result) {
            SendPhoto photo = assertInstanceOf(SendPhoto.class, method);
            assertEquals(String.valueOf(PAYMENT_GROUP), photo.getChatId());
            assertEquals("receipt", photo.getPhoto().getAttachName());
        }
    }

    /** Ishchilarga faqat yopish kerak bo'lganlar; NEW — hali to'lanmagan, u kassaniki. */
    @Test
    void kitchenGetsOnlyAcceptedTextCards() {
        stubStatus(OrderStatus.ACCEPTED, 3, 4);

        List<PartialBotApiMethod<?>> result = orderCardService.openCards(CardAudience.KITCHEN);

        assertEquals(2, result.size());
        for (PartialBotApiMethod<?> method : result) {
            SendMessage message = assertInstanceOf(SendMessage.class, method);
            assertEquals(String.valueOf(WORKER_GROUP), message.getChatId());
            assertNotNull(message.getReplyMarkup(), "ACCEPTED kartada 🍞 tugmasi bo'lishi kerak");
        }
        verify(orderService, never()).findOldest(eq(OrderStatus.NEW), anyInt());
    }

    /**
     * Limit umumiy: NEW oldin oladi, ACCEPTED qolgan joyni. Ko'p ACCEPTED yangi to'lovlarni
     * siqib chiqarmasin — kassaning asosiy ishi aynan ular.
     */
    @Test
    void newOrdersComeFirstAndLimitIsShared() {
        stubStatus(OrderStatus.NEW, LongStream.rangeClosed(1, 15).toArray());
        when(orderService.findOldest(OrderStatus.ACCEPTED, 5)).thenReturn(orders(OrderStatus.ACCEPTED, 16, 17, 18, 19, 20));
        when(orderService.countByStatus(OrderStatus.ACCEPTED)).thenReturn(12L);

        List<PartialBotApiMethod<?>> result = orderCardService.openCards(CardAudience.PAYMENT);

        assertEquals(OrderCardServiceImpl.OPEN_CARDS_LIMIT + 1, result.size());
        SendPhoto first = assertInstanceOf(SendPhoto.class, result.get(0));
        assertTrue(first.getCaption().startsWith("🆕 Buyurtma #1"));
        SendMessage note = assertInstanceOf(SendMessage.class, result.get(result.size() - 1));
        assertTrue(note.getText().startsWith("Yana 7 ta"), note.getText());
    }

    @Test
    void noNoteWhenEverythingFits() {
        stubStatus(OrderStatus.ACCEPTED, 3);

        List<PartialBotApiMethod<?>> result = orderCardService.openCards(CardAudience.KITCHEN);

        assertEquals(1, result.size());
    }

    @Test
    void emptyGroupGetsShortAnswer() {
        stubStatus(OrderStatus.NEW);
        stubStatus(OrderStatus.ACCEPTED);

        List<PartialBotApiMethod<?>> result = orderCardService.openCards(CardAudience.PAYMENT);

        SendMessage message = assertInstanceOf(SendMessage.class, result.get(0));
        assertEquals(1, result.size());
        assertEquals("Ochiq buyurtma yo'q ✅", message.getText());
        assertEquals(String.valueOf(PAYMENT_GROUP), message.getChatId());
    }

    private void stubStatus(OrderStatus status, long... ids) {
        lenient().when(orderService.findOldest(eq(status), anyInt())).thenReturn(orders(status, ids));
        lenient().when(orderService.countByStatus(status)).thenReturn((long) ids.length);
    }

    private List<Order> orders(OrderStatus status, long... ids) {
        return LongStream.of(ids).mapToObj(id -> order(id, status)).toList();
    }

    private Order order(long id, OrderStatus status) {
        User user = new User();
        user.setName("Alisher");
        user.setPhone("998901234567");
        Order order = Order.builder()
                .user(user)
                .status(status)
                .receiptFileId("receipt")
                .totalAmountMoney(BigDecimal.valueOf(5000))
                .build();
        order.setId(id);
        return order;
    }
}
