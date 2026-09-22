package uz.nonvoy.bot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;

/**
 * Bot HTTP sozlamalari. Asosiy sababi proxy: O'zbekistondan {@code api.telegram.org}
 * provayder darajasida to'siladi, ya'ni lokal ishlab chiqishda ulanish timeout beradi va
 * bot hatto ro'yxatdan ham o'ta olmaydi ({@code registerBot} ichida {@code deleteWebhook}
 * chaqiriladi). VPS chet elda bo'lsa proxy kerak emas — shuning uchun default o'chiq.
 */
@Configuration
@Slf4j
public class BotOptionsConfig {

    @Value("${bot.proxy.type:NO_PROXY}")
    private DefaultBotOptions.ProxyType proxyType;

    @Value("${bot.proxy.host:}")
    private String proxyHost;

    @Value("${bot.proxy.port:0}")
    private int proxyPort;

    @Bean
    public DefaultBotOptions botOptions() {
        DefaultBotOptions options = new DefaultBotOptions();
        if (proxyType == DefaultBotOptions.ProxyType.NO_PROXY) {
            return options;
        }
        if (proxyHost.isBlank() || proxyPort <= 0) {
            // Yarim sozlangan proxy jim qolsa, sabab tushunarsiz timeout bo'lib ko'rinadi
            log.warn("bot.proxy.type={} berilgan, lekin host/port yo'q — proxy'siz ishlaymiz", proxyType);
            return options;
        }
        options.setProxyType(proxyType);
        options.setProxyHost(proxyHost);
        options.setProxyPort(proxyPort);
        log.info("Telegram uchun proxy: {} {}:{}", proxyType, proxyHost, proxyPort);
        return options;
    }
}
