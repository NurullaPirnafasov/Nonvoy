package uz.nonvoy.bot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
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
        List<BotApiMethod<?>> methods;
        if (isTextOrContact(update)) {
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

    private boolean isTextOrContact(Update update) {
        return update.hasMessage() && (update.getMessage().hasText() || update.getMessage().hasContact());
    }

    @Override
    public void onUpdatesReceived(List<Update> updates) {
        super.onUpdatesReceived(updates);
    }
}
