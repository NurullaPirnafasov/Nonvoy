package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.UserState;
import uz.nonvoy.bot.service.CustomerFlowService;
import uz.nonvoy.bot.service.ProductService;
import uz.nonvoy.bot.service.UserService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerFlowServiceImpl implements CustomerFlowService {

    private final UserService userService;

    private final ProductService productService;

    private static final String ORDER_BUTTON = "Buyurtma berish";

    @Override
    public SendMessage handleUpdate(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String name = update.getMessage().getFrom().getFirstName();
        User user = userService.findOrCreate(telegramId, name);
        return switch (user.getState()) {
            case NEW -> handleNew(update, user, chatId);
            case WAITING_PHONE -> handleWaitingPhone(update, user, chatId);
            case IDLE -> handleIdle(update, user, chatId);
            default -> handleIdle(update, user, chatId);
        };
    }

    private SendMessage handleIdle(Update update, User user, Long chatId) {
        if (update.getMessage().hasText() && update.getMessage().getText().equals(ORDER_BUTTON)) {
            if (productService.getActiveProduct().isPresent()) {
                userService.updateState(user, UserState.WAITING_QUANTITY);
                return SendMessage.builder()
                        .chatId(chatId)
                        .text("buyurtma qilmoqchi bo'lgan mahsulot miqdorini kiriting")
                        .build();
            }else {
                return SendMessage.builder()
                        .chatId(chatId)
                        .text("Hozircha non tugagan")
                        .replyMarkup(orderKeyboard())
                        .build();
            }
        }else {
            return SendMessage.builder()
                    .chatId(chatId)
                    .text("buyurtma berish uchun tugmani bosing")
                    .replyMarkup(orderKeyboard())
                    .build();
        }
    }

    private SendMessage handleWaitingPhone(Update update, User user, Long chatId) {
        if (update.getMessage().hasContact()) {
            if (user.getTelegramId().equals(update.getMessage().getContact().getUserId())) {
                userService.savePhone(user, update.getMessage().getContact().getPhoneNumber());
                return SendMessage.builder()
                        .chatId(chatId)
                        .text("Rahmat! Endi bemalol buyurtma bera olasiz")
                        .replyMarkup(orderKeyboard())
                        .build();
            } else {
                return SendMessage.builder()
                        .chatId(chatId)
                        .text("O'zingizning raqamingizni yuboring")
                        .replyMarkup(contactKeyboard())
                        .build();
            }
        } else {
            return SendMessage.builder()
                    .chatId(chatId)
                    .text("Iltimos telefon raqamingizni yuboring")
                    .replyMarkup(contactKeyboard())
                    .build();
        }
    }

    private SendMessage handleNew(Update update, User user, Long chatId) {
        if (update.getMessage().hasText() && update.getMessage().getText().equals("/start")) {
            userService.updateState(user, UserState.WAITING_PHONE);
            String text = "Botdan to'liq foydalanishingiz uchun telefon raqamingizni yuboring";
            return SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(contactKeyboard())
                    .build();
        } else {
            return SendMessage.builder()
                    .chatId(chatId)
                    .text("/start buyrug'ini yuboring")
                    .build();
        }
    }

    private ReplyKeyboardMarkup orderKeyboard() {
        KeyboardButton button = KeyboardButton.builder()
                .text(ORDER_BUTTON)
                .build();
        return ReplyKeyboardMarkup.builder()
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
