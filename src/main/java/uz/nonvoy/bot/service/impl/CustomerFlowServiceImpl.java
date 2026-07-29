package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
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

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerFlowServiceImpl implements CustomerFlowService {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;

    private static final String ORDER_BUTTON = "Buyurtma berish";
    private static final String CONFIRM_YES = "Ha";
    private static final String CONFIRM_NO = "Yo'q";

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
        return null;
    }

    private List<BotApiMethod<?>> handleConfirming(Update update, User user, Long chatId) {
        if (update.getMessage().hasText()) {
            if (CONFIRM_YES.equals(update.getMessage().getText())) {
                Order order = orderService.createOrder(user);
                return reply(chatId,
                        "Buyurtma #" + order.getId() + " yuborildi. Jami: " + order.getTotalAmount() + " so'm",
                        orderKeyboard());
            } else if (CONFIRM_NO.equals(update.getMessage().getText())) {
                userService.resetToIdle(user);
                return reply(chatId, "Buyurtma bekor qilindi", orderKeyboard());
            }
        }
        return reply(chatId, "buyurtmani tasdiqlash uchun Ha yoki Yo'q ni tanlang", confirmKeyboard());
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
                quantity + " ta " + product.getName() + " - " + totalPrice + " so'm. Buyurtmani tasdiqlaysizmi?",
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

    private List<BotApiMethod<?>> reply(Long chatId, String text) {
        return List.of(message(chatId, text));
    }

    private SendMessage message(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
    }

    private List<BotApiMethod<?>> reply(Long chatId, String text, ReplyKeyboardMarkup keyboard) {
        return List.of(message(chatId, text, keyboard));
    }

    private SendMessage message(Long chatId, String text, ReplyKeyboardMarkup keyboard) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .build();
    }

    private ReplyKeyboardMarkup confirmKeyboard() {
        KeyboardButton button1 = KeyboardButton.builder()
                .text(CONFIRM_YES)
                .build();
        KeyboardButton button2 = KeyboardButton.builder()
                .text(CONFIRM_NO)
                .build();
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(List.of(button1, button2)))
                .resizeKeyboard(true)
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
