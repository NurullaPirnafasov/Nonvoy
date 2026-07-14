package uz.nonvoy.bot.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
public class NonvoyTelegramBot extends TelegramLongPollingBot {

    @Value("${bot.token}")
    private String token;

    @Value("${bot.username}")
    private String username;

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
        if (update.hasMessage() && update.getMessage().hasText()) {
            if (update.getMessage().getText().startsWith("/start")) {
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(update.getMessage().getChatId().toString());
                sendMessage.setText("Assalomu aleykum!");
                try {
                    this.execute(sendMessage);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public void onUpdatesReceived(List<Update> updates) {
        super.onUpdatesReceived(updates);
    }
}
