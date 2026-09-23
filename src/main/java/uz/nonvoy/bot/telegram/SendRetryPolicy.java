package uz.nonvoy.bot.telegram;

import org.telegram.telegrambots.meta.api.objects.ResponseParameters;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Telegram so'rovi xato bilan tugaganda qayta urinish kerakmi va qancha kutish kerakligini
 * aytadi. Service'lar yuborish natijasini ko'rmaydi (15, 28-qarorlar), ya'ni kassa kartasi
 * yetib bormasa buni hech kim bilmaydi — vaqtincha uzilish shu yerda, bitta joyda yopiladi.
 * <p>
 * Qayta uriladi faqat:
 * <ul>
 *     <li>tarmoq xatosi — so'rov Telegram'ga yetmagan yoki javob kelmagan;</li>
 *     <li>429 (limit) — Telegram o'zi aytgan vaqt qisqa bo'lsa.</li>
 * </ul>
 * Boshqa API xatolari ({@code chat not found}, {@code message is not modified}) qayta
 * urinish bilan tuzalmaydi — ular darhol qaytariladi.
 */
public final class SendRetryPolicy {

    /** Birinchi xatodan keyin 1 s, ikkinchisidan keyin 3 s. Uchinchi xato — taslim. */
    static final List<Duration> NETWORK_DELAYS = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3));

    /**
     * Update'lar bitta oqimda ishlanadi, ya'ni kutish paytida hamma mijoz kutadi.
     * Limitdan uzoqroq kutish so'ralsa, bitta xabar uchun butun botni to'xtatmaymiz.
     */
    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);

    private static final int TOO_MANY_REQUESTS = 429;

    private SendRetryPolicy() {
    }

    /**
     * @param failedAttempts shu paytgacha nechta urinish muvaffaqiyatsiz bo'lgani (1 dan boshlab)
     * @return keyingi urinishdan oldin kutish vaqti; bo'sh — qayta urinmaslik
     */
    public static Optional<Duration> delayBeforeRetry(TelegramApiException e, int failedAttempts) {
        if (failedAttempts > NETWORK_DELAYS.size()) {
            return Optional.empty();
        }
        if (e instanceof TelegramApiRequestException request) {
            return retryAfter(request);
        }
        return isNetworkError(e) ? Optional.of(NETWORK_DELAYS.get(failedAttempts - 1)) : Optional.empty();
    }

    /** API javob berdi, ya'ni so'rov yetib borgan va rad etilgan — faqat limit kutib o'tadi. */
    private static Optional<Duration> retryAfter(TelegramApiRequestException request) {
        ResponseParameters parameters = request.getParameters();
        if (request.getErrorCode() == null || request.getErrorCode() != TOO_MANY_REQUESTS
                || parameters == null || parameters.getRetryAfter() == null) {
            return Optional.empty();
        }
        Duration wait = Duration.ofSeconds(parameters.getRetryAfter());
        return wait.compareTo(MAX_RETRY_AFTER) <= 0 ? Optional.of(wait) : Optional.empty();
    }

    private static boolean isNetworkError(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
