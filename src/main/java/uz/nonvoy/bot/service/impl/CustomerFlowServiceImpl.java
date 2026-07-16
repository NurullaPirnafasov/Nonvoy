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
import uz.nonvoy.bot.repository.UserRepository;
import uz.nonvoy.bot.service.CustomerFlowService;
import uz.nonvoy.bot.service.UserService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerFlowServiceImpl implements CustomerFlowService {

    private final UserService userService;

    @Override
    public SendMessage handleUpdate(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        String name = update.getMessage().getFrom().getFirstName();
        User user = userService.findOrCreate(telegramId, name);
        switch (user.getState()) {
            case NEW:
                return handleNew(update, user);
            case WAITING_PHONE:
                return handleWaitingPhone(update,user);
            default: return null;
        }
    }

    private SendMessage handleWaitingPhone(Update update, User user) {

        return null;
    }

    private SendMessage handleNew(Update update, User user) {
        if (update.getMessage().getText().equals("/start")) {
            KeyboardButton contactButton = KeyboardButton.builder()
                    .text("\uD83D\uDCDE Telefon raqamingizni yuboring")
                    .requestContact(true)
                    .build();

            ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                    .keyboardRow(new KeyboardRow(List.of(contactButton)))
                    .resizeKeyboard(true)
                    .oneTimeKeyboard(true)
                    .build();
            userService.updateState(user, UserState.WAITING_PHONE);
            String text = "Botdan to'liq foydalanishingiz uchun telefon raqamingizni yuboring";
            return SendMessage.builder().chatId(update.getMessage().getChatId()).text(text).replyMarkup(keyboard).build();
        } else {
            return SendMessage.builder().chatId(update.getMessage().getChatId()).text("/start buyrug'ini yuboring").build();
        }
    }


}
