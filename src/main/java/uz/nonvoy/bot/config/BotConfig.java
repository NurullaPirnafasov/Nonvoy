package uz.nonvoy.bot.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.nonvoy.bot.telegram.NonvoyTelegramBot;

@Configuration
public class BotConfig {
    private final NonvoyTelegramBot nonvoyTelegramBot;


    public BotConfig(NonvoyTelegramBot nonvoyTelegramBot) {
        this.nonvoyTelegramBot = nonvoyTelegramBot;
    }

    @PostConstruct
    public void init() throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(nonvoyTelegramBot);
    }
}
