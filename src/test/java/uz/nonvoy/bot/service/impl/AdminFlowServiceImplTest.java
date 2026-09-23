package uz.nonvoy.bot.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.OrderStatus;
import uz.nonvoy.bot.service.OrderCardService;
import uz.nonvoy.bot.service.OrderService;
import uz.nonvoy.bot.service.ProductAdminService;
import uz.nonvoy.bot.service.StatusChange;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminFlowServiceImplTest {

    private static final long PAYMENT_GROUP = -100L;
    private static final long WORKER_GROUP = -200L;
    private static final int PAYMENT_CARD_ID = 812;
    private static final int KITCHEN_CARD_ID = 900;

    @Mock
    private OrderService orderService;
    @Mock
    private ProductAdminService productAdminService;
    @Mock
    private OrderCardService orderCardService;

    @InjectMocks
    private AdminFlowServiceImpl adminFlowService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminFlowService, "paymentGroupId", PAYMENT_GROUP);
        ReflectionTestUtils.setField(adminFlowService, "workerGroupId", WORKER_GROUP);
        lenient().when(orderService.findItems(any())).thenReturn(List.of());
    }

    /** Kassa ✅ bosganda uning kartasining id'si ishchilar kartasiga uzatiladi (35-qaror). */
    @Test
    void acceptPassesPaymentCardIdToKitchenCard() {
        transition(OrderStatus.NEW, OrderStatus.ACCEPTED);
        when(orderCardService.kitchenCard(any(), any())).thenReturn(SendMessage.builder().chatId(WORKER_GROUP).text("karta").build());

        adminFlowService.handleCallback(callback("ACCEPT:47", PAYMENT_GROUP, PAYMENT_CARD_ID));

        verify(orderCardService).kitchenCard(any(), eq(PAYMENT_CARD_ID));
    }

    /** Non tayyor bo'lgach kassa kartasida eskirgan [❌ Bekor] qolmasin. */
    @Test
    void readyClosesPaymentCard() {
        transition(OrderStatus.ACCEPTED, OrderStatus.READY);

        List<PartialBotApiMethod<?>> result =
                adminFlowService.handleCallback(callback("READY:47:" + PAYMENT_CARD_ID, WORKER_GROUP, KITCHEN_CARD_ID));

        EditMessageCaption paymentCard = result.stream()
                .filter(EditMessageCaption.class::isInstance)
                .map(EditMessageCaption.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("kassa kartasi qayta chizilmadi"));
        assertEquals(String.valueOf(PAYMENT_GROUP), paymentCard.getChatId());
        assertEquals(PAYMENT_CARD_ID, paymentCard.getMessageId());
        assertNull(paymentCard.getReplyMarkup(), "READY kassa kartasida tugma qolmasligi kerak");
        assertTrue(paymentCard.getCaption().startsWith("🍞 Buyurtma #47 — tayyor"), paymentCard.getCaption());
    }

    /** /buyurtmalar bilan chiqarilgan yoki eski karta: kassa kartasi noma'lum, tegilmaydi. */
    @Test
    void readyWithoutPaymentCardIdLeavesPaymentGroupAlone() {
        transition(OrderStatus.ACCEPTED, OrderStatus.READY);

        List<PartialBotApiMethod<?>> result =
                adminFlowService.handleCallback(callback("READY:47", WORKER_GROUP, KITCHEN_CARD_ID));

        assertTrue(result.stream().noneMatch(EditMessageCaption.class::isInstance));
    }

    /**
     * Kassa kartasi yopilmagan holatda (masalan, qayta chiqarilgan karta) eskirgan "Bekor"
     * bosilsa: exception emas, javob va karta tugmalari haqiqiy holatga moslanadi (15-qaror).
     */
    @Test
    void cancelAfterReadyIsAnsweredAndButtonsRemoved() {
        Order order = order(OrderStatus.READY);
        when(orderService.findById(47L)).thenReturn(Optional.of(order));
        when(orderService.changeStatus(47L, OrderStatus.CANCELLED))
                .thenThrow(new IllegalStateException("READY->CANCELLED o'tish ruxsat etilmagan"));

        List<PartialBotApiMethod<?>> result =
                adminFlowService.handleCallback(callback("CANCEL:47", PAYMENT_GROUP, PAYMENT_CARD_ID));

        AnswerCallbackQuery answer = assertInstanceOf(AnswerCallbackQuery.class, result.get(0));
        assertEquals("Buyurtma allaqachon tayyor", answer.getText());
        EditMessageReplyMarkup markup = assertInstanceOf(EditMessageReplyMarkup.class, result.get(1));
        assertNull(markup.getReplyMarkup());
    }

    private void transition(OrderStatus from, OrderStatus to) {
        when(orderService.findById(47L)).thenReturn(Optional.of(order(from)));
        when(orderService.changeStatus(47L, to)).thenReturn(new StatusChange(order(to), from));
    }

    private Update callback(String data, long chatId, int messageId) {
        Chat chat = new Chat();
        chat.setId(chatId);
        chat.setType("supergroup");
        Message message = new Message();
        message.setChat(chat);
        message.setMessageId(messageId);
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setId("cb");
        callbackQuery.setData(data);
        callbackQuery.setMessage(message);
        Update update = new Update();
        update.setCallbackQuery(callbackQuery);
        return update;
    }

    private Order order(OrderStatus status) {
        User user = User.builder().telegramId(123L).name("Alisher").phone("998901234567").build();
        Order order = Order.builder()
                .user(user)
                .status(status)
                .receiptFileId("receipt")
                .totalAmountMoney(BigDecimal.valueOf(50000))
                .build();
        order.setId(47L);
        return order;
    }
}
