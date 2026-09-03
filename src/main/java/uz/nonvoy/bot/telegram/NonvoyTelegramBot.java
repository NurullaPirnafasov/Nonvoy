package uz.nonvoy.bot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.nonvoy.bot.service.AdminFlowService;
import uz.nonvoy.bot.service.CustomerFlowService;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NonvoyTelegramBot extends TelegramLongPollingBot {

    private final CustomerFlowService customerFlowService;
    private final AdminFlowService adminFlowService;

    @Value("${bot.token}")
    private String token;

    @Value("${bot.username}")
    private String username;

    @Value("${bot.admin-group-id}")
    private Long adminGroupId;

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onRegister() {
        super.onRegister();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            route(update);
        } catch (RuntimeException e) {
            // Bitta update'dagi xato long polling session'ini to'xtatib qo'ymasin
            log.error("Update ishlanmadi: {}", update.getUpdateId(), e);
            notifyFailure(update);
        }
    }

    private void route(Update update) {
        List<BotApiMethod<?>> methods;
        if (isHandledMessage(update)) {
            if (adminGroupId.equals(update.getMessage().getChatId())) {
                methods = adminFlowService.handleMessage(update);
            } else {
                methods = customerFlowService.handleMessage(update);
            }
        } else if (update.hasCallbackQuery()) {
            if (adminGroupId.equals(update.getCallbackQuery().getMessage().getChatId())) {
                methods = adminFlowService.handleCallback(update);
            } else {
                methods = customerFlowService.handleCallback(update);
            }
        } else return;

        if (methods == null || methods.isEmpty()) return;
        for (BotApiMethod<?> method : methods) {
            try {
                execute(method);
            } catch (TelegramApiException e) {
                log.error("Telegram API Exception", e);
            }
        }
    }

    /** Foydalanuvchi javobsiz qolmasin: nima bo'lganini bilmasa ham, xato borligini bilsin. */
    private void notifyFailure(Update update) {
        Long chatId = null;
        if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();
        } else if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
        }
        if (chatId == null) {
            return;
        }
        try {
            execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Xatolik yuz berdi, birozdan keyin urinib ko'ring")
                    .build());
        } catch (TelegramApiException e) {
            log.error("Xatolik haqida xabar yuborilmadi", e);
        }
    }

    /**
     * Matn/kontakt filtri ataylab yo'q: rasm, stiker, ovoz ham flow'ga o'tadi, aks holda
     * foydalanuvchi javobsiz qoladi va nima kutilayotganini bilmaydi. Kerakli turni har bir
     * state o'zi tekshiradi va "hozir nima kerakligi"ni eslatadi.
     * Bot xabarlari va from'siz service update'lar (kanal post, guruhga qo'shilish) tashlanadi.
     */
    private boolean isHandledMessage(Update update) {
        if (!update.hasMessage() || update.getMessage().getFrom() == null) {
            return false;
        }
        return !Boolean.TRUE.equals(update.getMessage().getFrom().getIsBot());
    }

    @Override
    public void onUpdatesReceived(List<Update> updates) {
        super.onUpdatesReceived(updates);
    }
}
