package uz.nonvoy.bot.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.nonvoy.bot.telegram.NonvoyTelegramBot;

import java.time.Duration;

/**
 * Botni Telegram'da ro'yxatdan o'tkazadi. Ro'yxatdan o'tish tarmoq talab qiladi
 * ({@code registerBot} ichida {@code deleteWebhook} chaqiriladi), shuning uchun u
 * alohida oqimda va qayta urinishlar bilan bajariladi: tarmoq yo'qligi dasturni
 * ko'tarilmaydigan qilib qo'ymasin — ulanish paydo bo'lishi bilan bot o'zi ishga tushadi.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BotConfig {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final NonvoyTelegramBot nonvoyTelegramBot;

    private Thread registrar;

    @PostConstruct
    public void init() {
        registrar = new Thread(this::registerUntilSuccess, "telegram-registrar");
        registrar.setDaemon(true);
        registrar.start();
    }

    @PreDestroy
    public void shutdown() {
        if (registrar != null) {
            registrar.interrupt();
        }
    }

    private void registerUntilSuccess() {
        TelegramBotsApi api;
        try {
            api = new TelegramBotsApi(DefaultBotSession.class);
        } catch (TelegramApiException e) {
            // Sessiya turini yaratish tarmoqqa bog'liq emas — bu yerdagi xato sozlama xatosi
            log.error("TelegramBotsApi yaratilmadi", e);
            return;
        }

        int attempt = 0;
        while (!Thread.currentThread().isInterrupted()) {
            attempt++;
            try {
                api.registerBot(nonvoyTelegramBot);
                log.info("Bot Telegram'da ro'yxatdan o'tdi ({}-urinish)", attempt);
                nonvoyTelegramBot.registerCommands();
                return;
            } catch (TelegramApiException e) {
                // Sabab matni logda qolsin: tarmoq timeout'i bilan noto'g'ri token
                // ("Unauthorized") shu yerda ajralib turadi
                log.warn("Telegram'ga ulanib bo'lmadi ({}-urinish): {}. {} soniyadan keyin qayta urinamiz",
                        attempt, rootMessage(e), RETRY_DELAY.toSeconds());
            }
            try {
                Thread.sleep(RETRY_DELAY.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
