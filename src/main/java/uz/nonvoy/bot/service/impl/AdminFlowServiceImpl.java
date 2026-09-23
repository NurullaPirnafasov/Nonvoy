package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.enums.OrderStatus;
import uz.nonvoy.bot.service.AdminFlowService;
import uz.nonvoy.bot.service.OrderCardService;
import uz.nonvoy.bot.service.OrderService;
import uz.nonvoy.bot.service.ProductAdminService;
import uz.nonvoy.bot.service.StatusChange;
import uz.nonvoy.bot.telegram.OrderAction;
import uz.nonvoy.bot.util.CardAudience;
import uz.nonvoy.bot.util.OrderCardFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ikki guruh: kassa to'lovni tekshiradi, ishchilar nonni yopadi. Alohida panel yo'q —
 * buyurtma kartasi guruhga tushadi, status inline tugmalar orqali o'zgaradi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminFlowServiceImpl implements AdminFlowService {

    private final OrderService orderService;
    private final ProductAdminService productAdminService;
    private final OrderCardService orderCardService;

    @Value("${bot.admin-group-id}")
    private Long paymentGroupId;

    @Value("${bot.worker-group-id}")
    private Long workerGroupId;

    private static final String PRODUCTS_COMMAND = "/mahsulotlar";
    private static final String ORDERS_COMMAND = "/buyurtmalar";

    @Override
    public List<PartialBotApiMethod<?>> handleMessage(Update update) {
        Message message = update.getMessage();
        if (message == null) {
            return List.of();
        }
        // Ochiq kartalar ikkala guruhda: har guruh faqat o'zinikini oladi
        if (message.hasText() && isCommand(message.getText(), ORDERS_COMMAND)) {
            CardAudience audience = audienceOf(message.getChatId());
            return audience == null ? List.of() : orderCardService.openCards(audience);
        }
        // Mahsulot boshqaruvi — pul masalasi, shuning uchun faqat kassada (12-qaror)
        if (!paymentGroupId.equals(message.getChatId())) {
            return List.of();
        }
        // Privacy mode tufayli bot guruhda faqat buyruqlarni va o'z xabariga qilingan
        // reply'larni ko'radi — shuning uchun boshqa shovqin bu yergacha yetib kelmaydi (18-qaror)
        Optional<List<PartialBotApiMethod<?>>> reply = productAdminService.handleReply(message);
        if (reply.isPresent()) {
            return reply.get();
        }
        if (message.hasText() && isCommand(message.getText(), PRODUCTS_COMMAND)) {
            return productAdminService.openList(message.getChatId());
        }
        return List.of();
    }

    @Override
    public List<PartialBotApiMethod<?>> handleCallback(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        if (productAdminService.handlesCallback(data)) {
            if (!paymentGroupId.equals(chatId)) {
                return List.of(answer(callbackQuery, "Bu tugma bu guruhda ishlamaydi"));
            }
            return productAdminService.handleCallback(callbackQuery);
        }
        return handleOrderAction(callbackQuery, data, chatId);
    }

    private List<PartialBotApiMethod<?>> handleOrderAction(CallbackQuery callbackQuery, String data, Long chatId) {
        Optional<OrderAction> action = OrderAction.parse(data);
        Optional<Long> orderId = OrderAction.parseOrderId(data);
        if (action.isEmpty() || orderId.isEmpty()) {
            return List.of(answer(callbackQuery, "Noma'lum tugma"));
        }

        CardAudience audience = audienceOf(chatId);
        // Tugma u yerda chizilmasa ham himoya arzon (17-qaror)
        if (audience == null || !isAllowed(action.get(), audience)) {
            return List.of(answer(callbackQuery, "Bu tugma bu guruhda ishlamaydi"));
        }

        Optional<Order> existing = orderService.findById(orderId.get());
        if (existing.isEmpty()) {
            return List.of(answer(callbackQuery, "Buyurtma #" + orderId.get() + " topilmadi"));
        }

        StatusChange change;
        try {
            change = orderService.changeStatus(orderId.get(), action.get().getStatus());
        } catch (IllegalStateException e) {
            // Ikki marta bosish yoki eskirgan karta (15-qaror) shu yerga tushadi.
            // Kartani qayta chizmaymiz — matn o'zgarmagani uchun Telegram baribir rad etardi;
            // o'rniga kartadagi tugmalarni haqiqiy holatga moslaymiz
            // Kutilgan holat, xato emas — stack trace kerak emas, u faqat chalg'itadi
            log.info("Eskirgan tugma rad etildi: order={}, action={}: {}", orderId.get(), action.get(), e.getMessage());
            List<PartialBotApiMethod<?>> result = new ArrayList<>();
            result.add(answer(callbackQuery, alreadyHandledText(existing.get())));
            result.add(EditMessageReplyMarkup.builder()
                    .chatId(chatId)
                    .messageId(callbackQuery.getMessage().getMessageId())
                    .replyMarkup(OrderCardFormatter.keyboard(existing.get(), audience))
                    .build());
            return result;
        }

        Order order = change.order();
        List<PartialBotApiMethod<?>> result = new ArrayList<>();
        result.add(answer(callbackQuery, null));
        result.add(redrawOwnCard(order, audience, chatId, callbackQuery.getMessage().getMessageId()));
        result.addAll(notifyWorkers(change));
        result.add(SendMessage.builder()
                .chatId(order.getUser().getTelegramId())
                .text(customerNotification(change))
                .build());
        return result;
    }

    /**
     * Chek rasmi faqat kassa xabarida, shuning uchun u yerda caption tahrirlanadi,
     * ishchilarda esa oddiy matn (13-qaror).
     */
    private PartialBotApiMethod<?> redrawOwnCard(Order order, CardAudience audience, Long chatId, Integer messageId) {
        String text = OrderCardFormatter.card(order, orderService.findItems(order), audience);
        InlineKeyboardMarkup keyboard = OrderCardFormatter.keyboard(order, audience);
        if (audience == CardAudience.PAYMENT) {
            return EditMessageCaption.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .caption(text)
                    .replyMarkup(keyboard)
                    .build();
        }
        return EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .replyMarkup(keyboard)
                .build();
    }

    /** Ishchilar guruhi faqat kassa qaror qabul qilganda xabar oladi. */
    private List<PartialBotApiMethod<?>> notifyWorkers(StatusChange change) {
        Order order = change.order();
        if (change.from() == OrderStatus.NEW && order.getStatus() == OrderStatus.ACCEPTED) {
            return List.of(orderCardService.kitchenCard(order));
        }
        // Non allaqachon tandirda bo'lishi mumkin — ishchilar buni bilishi shart (12-qaror)
        if (change.from() == OrderStatus.ACCEPTED && order.getStatus() == OrderStatus.CANCELLED) {
            return List.of(SendMessage.builder()
                    .chatId(workerGroupId)
                    .text("❌ Buyurtma #" + order.getId() + " bekor qilindi — yopmang")
                    .build());
        }
        return List.of();
    }

    private CardAudience audienceOf(Long chatId) {
        if (paymentGroupId.equals(chatId)) {
            return CardAudience.PAYMENT;
        }
        if (workerGroupId.equals(chatId)) {
            return CardAudience.KITCHEN;
        }
        return null;
    }

    private boolean isAllowed(OrderAction action, CardAudience audience) {
        return switch (audience) {
            case PAYMENT -> action == OrderAction.ACCEPT || action == OrderAction.CANCEL;
            case KITCHEN -> action == OrderAction.READY;
        };
    }

    /** Matn farqi eski statusdan chiqadi — alohida REJECTED status kerak emas (11-qaror). */
    private String customerNotification(StatusChange change) {
        Order order = change.order();
        String number = "#" + order.getId();
        return switch (order.getStatus()) {
            case ACCEPTED -> "To'lovingiz tasdiqlandi ✅ Buyurtma " + number + " tayyorlanmoqda";
            case READY -> "Noningiz tayyor, olib ketishingiz mumkin 🍞 (" + number + ")";
            case CANCELLED -> change.from() == OrderStatus.NEW
                    ? "To'lov topilmadi ❌ Iltimos, qaytadan buyurtma bering (" + number + ")"
                    : "Buyurtmangiz " + number + " bekor qilindi ❌";
            case NEW -> "Buyurtmangiz " + number + " navbatda";
        };
    }

    private String alreadyHandledText(Order order) {
        return switch (order.getStatus()) {
            case NEW -> "Buyurtma hali to'lov tekshiruvida";
            case ACCEPTED -> "To'lov allaqachon tasdiqlangan";
            case READY -> "Buyurtma allaqachon tayyor";
            case CANCELLED -> "Buyurtma bekor qilingan";
        };
    }

    private AnswerCallbackQuery answer(CallbackQuery callbackQuery, String text) {
        return AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text(text)
                .build();
    }

    /** Guruhda buyruq "/mahsulotlar@bot_username" ko'rinishida ham keladi. */
    private boolean isCommand(String text, String expected) {
        String command = text.trim().split("\\s+")[0];
        int at = command.indexOf('@');
        if (at > 0) {
            command = command.substring(0, at);
        }
        return expected.equals(command);
    }
}
