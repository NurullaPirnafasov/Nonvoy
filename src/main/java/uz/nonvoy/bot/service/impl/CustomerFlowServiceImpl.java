package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.UserState;
import uz.nonvoy.bot.service.CustomerFlowService;
import uz.nonvoy.bot.service.OrderService;
import uz.nonvoy.bot.service.ProductService;
import uz.nonvoy.bot.service.UserService;
import uz.nonvoy.bot.util.OrderCardFormatter;
import uz.nonvoy.bot.util.PriceFormatter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerFlowServiceImpl implements CustomerFlowService {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;

    @Value("${bot.admin-group-id}")
    private Long adminGroupId;

    private static final String ORDER_BUTTON = "Buyurtma berish";
    private static final String CONFIRM_YES = "CONFIRM:YES";
    private static final String CONFIRM_NO = "CONFIRM:NO";

    @Override
    public List<BotApiMethod<?>> handleMessage(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String name = update.getMessage().getFrom().getFirstName();
        User user = userService.findOrCreate(telegramId, name);
        return switch (user.getState()) {
            case NEW -> handleNew(update, user, chatId);
            case WAITING_PHONE -> handleWaitingPhone(update, user, chatId);
            case IDLE -> handleIdle(update, user, chatId);
            case WAITING_QUANTITY -> handleWaitingQuantity(update, user, chatId);
            case CONFIRMING -> handleConfirming(update, user, chatId);
        };
    }

    @Override
    public List<BotApiMethod<?>> handleCallback(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String id = callbackQuery.getId();
        String data = callbackQuery.getData();
        Long telegramId = callbackQuery.getFrom().getId();
        String name = callbackQuery.getFrom().getFirstName();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        User user = userService.findOrCreate(telegramId, name);
        List<BotApiMethod<?>> list = new ArrayList<>();
        if (user.getState() != UserState.CONFIRMING) {
            AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
                    .callbackQueryId(id)
                    .text("Bu buyurtma allaqachon rasmiylashtirilgan")
                    .build();
            list.add(answerCallbackQuery);
            return list;
        }
        String answerText;
        if (CONFIRM_YES.equals(data) || CONFIRM_NO.equals(data)) {
            answerText = null;
        } else {
            answerText = "Buyurtmani rasmiylashtirish uchun Ha yoki Yo'qni bosing";
        }
        AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
                .callbackQueryId(id)
                .text(answerText)
                .build();
        list.add(answerCallbackQuery);
        if (CONFIRM_YES.equals(data)) {
            Integer quantity = user.getDraftQuantity();
            Order order;
            try {
                order = orderService.createOrder(user);
            } catch (IllegalStateException e) {
                // Miqdor kiritilgandan keyin novvoy mahsulotni "tugadi" qilib qo'ygan bo'lishi mumkin
                userService.resetToIdle(user);
                list.add(EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text("Kechirasiz, mahsulot tugadi. Buyurtma rasmiylashtirilmadi")
                        .build());
                list.add(message(chatId, "Keyinroq urinib ko'ring", orderKeyboard()));
                return list;
            }
            EditMessageText edit = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text("Buyurtma #" + order.getId() + " qabul qilindi ✅ Jami: " + quantity + " ta non — " + PriceFormatter.formatPrice(order.getTotalAmountMoney()) + " so'm")
                    .build();
            list.add(edit);
            list.add(adminCard(order));
        } else if (CONFIRM_NO.equals(data)) {
            userService.resetToIdle(user);
            EditMessageText edit = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text("Buyurtma bekor qilindi")
                    .build();
            list.add(edit);
        }
        return list;
    }

    private List<BotApiMethod<?>> handleConfirming(Update update, User user, Long chatId) {
        return reply(chatId, "buyurtmani tasdiqlash uchun yuqoridagi Ha yoki Yo'qni bosing");
    }

    private List<BotApiMethod<?>> handleWaitingQuantity(Update update, User user, Long chatId) {
        if (!update.getMessage().hasText()) {
            return reply(chatId, "Iltimos raqam kiriting");
        }
        int quantity;
        try {
            quantity = Integer.parseInt(update.getMessage().getText().trim());
        } catch (NumberFormatException e) {
            return reply(chatId, "miqdorni to'g'ri kiriting");
        }
        if (quantity <= 0) {
            return reply(chatId, "miqdorni to'g'ri kiriting");
        }
        Product product = productService.getActiveProduct().orElse(null);
        if (product == null) {
            userService.resetToIdle(user);
            return reply(chatId, "Kechirasiz, mahsulot tugadi", orderKeyboard());
        }
        userService.saveQuantity(quantity, user);
        BigDecimal totalPrice = orderService.calculateTotal(product, quantity);
        return reply(chatId,
                quantity + " ta " + product.getName() + " — " + PriceFormatter.formatPrice(totalPrice) + " so'm. Buyurtmani tasdiqlaysizmi?",
                confirmKeyboard());
    }

    private List<BotApiMethod<?>> handleIdle(Update update, User user, Long chatId) {
        if (update.getMessage().hasText() && update.getMessage().getText().equals(ORDER_BUTTON)) {
            if (productService.getActiveProduct().isPresent()) {
                userService.updateState(user, UserState.WAITING_QUANTITY);
                return reply(chatId, "buyurtma qilmoqchi bo'lgan mahsulot miqdorini kiriting");
            } else {
                return reply(chatId, "Hozircha non tugagan", orderKeyboard());
            }
        } else {
            return reply(chatId, "buyurtma berish uchun tugmani bosing", orderKeyboard());
        }
    }

    private List<BotApiMethod<?>> handleWaitingPhone(Update update, User user, Long chatId) {
        if (update.getMessage().hasContact()) {
            if (user.getTelegramId().equals(update.getMessage().getContact().getUserId())) {
                userService.savePhone(user, update.getMessage().getContact().getPhoneNumber());
                return reply(chatId, "Rahmat! Endi bemalol buyurtma bera olasiz", orderKeyboard());
            } else {
                return reply(chatId, "O'zingizning raqamingizni yuboring", contactKeyboard());
            }
        } else {
            return reply(chatId, "Iltimos telefon raqamingizni yuboring", contactKeyboard());
        }
    }

    private List<BotApiMethod<?>> handleNew(Update update, User user, Long chatId) {
        if (update.getMessage().hasText() && update.getMessage().getText().equals("/start")) {
            userService.updateState(user, UserState.WAITING_PHONE);
            return reply(chatId,
                    "Botdan to'liq foydalanishingiz uchun telefon raqamingizni yuboring",
                    contactKeyboard());
        } else {
            return reply(chatId, "/start buyrug'ini yuboring");
        }
    }

    /** Yangi buyurtma kartasi — novvoy guruhda ko'radi va shu yerda statusni boshqaradi. */
    private SendMessage adminCard(Order order) {
        return SendMessage.builder()
                .chatId(adminGroupId)
                .text(OrderCardFormatter.card(order, orderService.findItems(order)))
                .replyMarkup(OrderCardFormatter.keyboard(order))
                .build();
    }

    private List<BotApiMethod<?>> reply(Long chatId, String text) {
        return List.of(message(chatId, text));
    }

    private SendMessage message(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
    }

    private List<BotApiMethod<?>> reply(Long chatId, String text, ReplyKeyboard keyboard) {
        return List.of(message(chatId, text, keyboard));
    }

    private SendMessage message(Long chatId, String text, ReplyKeyboard keyboard) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .build();
    }

    private InlineKeyboardMarkup confirmKeyboard() {
        InlineKeyboardButton button1 = InlineKeyboardButton.builder()
                .text("Ha")
                .callbackData(CONFIRM_YES)
                .build();
        InlineKeyboardButton button2 = InlineKeyboardButton.builder()
                .text("Yo'q")
                .callbackData(CONFIRM_NO)
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button1, button2))
                .build();
    }

    private ReplyKeyboardMarkup orderKeyboard() {
        KeyboardButton button = KeyboardButton.builder()
                .text(ORDER_BUTTON)
                .build();
        return ReplyKeyboardMarkup
                .builder()
                .keyboardRow(new KeyboardRow(List.of(button)))
                .resizeKeyboard(true)
                .build();
    }

    private ReplyKeyboardMarkup contactKeyboard() {
        KeyboardButton contactButton = KeyboardButton.builder()
                .text("\uD83D\uDCDE Telefon raqamingizni yuboring")
                .requestContact(true)
                .build();

        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(List.of(contactButton)))
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();
    }
}
