package uz.nonvoy.bot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SendRetryPolicyTest {

    /** Kutubxona tarmoq xatosini shunday o'raydi: "Unable to execute ... method" + IOException. */
    private static final TelegramApiException NETWORK_ERROR =
            new TelegramApiException("Unable to execute sendphoto method", new SocketTimeoutException("Read timed out"));

    @Test
    void networkErrorIsRetriedWithGrowingDelay() {
        assertEquals(Optional.of(Duration.ofSeconds(1)), SendRetryPolicy.delayBeforeRetry(NETWORK_ERROR, 1));
        assertEquals(Optional.of(Duration.ofSeconds(3)), SendRetryPolicy.delayBeforeRetry(NETWORK_ERROR, 2));
    }

    @Test
    void networkErrorGivesUpAfterThirdAttempt() {
        assertEquals(Optional.empty(), SendRetryPolicy.delayBeforeRetry(NETWORK_ERROR, 3));
    }

    /** "chat not found", "message is not modified" — qayta urinish bilan tuzalmaydi. */
    @Test
    void apiErrorIsNotRetried() throws IOException {
        TelegramApiRequestException e = apiError("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}");

        assertEquals(Optional.empty(), SendRetryPolicy.delayBeforeRetry(e, 1));
    }

    @Test
    void errorWithoutNetworkCauseIsNotRetried() {
        assertEquals(Optional.empty(), SendRetryPolicy.delayBeforeRetry(new TelegramApiException("boshqa xato"), 1));
    }

    @Test
    void rateLimitWaitsAsTelegramAsks() throws IOException {
        TelegramApiRequestException e = apiError(
                "{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\",\"parameters\":{\"retry_after\":7}}");

        assertEquals(Optional.of(Duration.ofSeconds(7)), SendRetryPolicy.delayBeforeRetry(e, 1));
    }

    /** Uzoq kutish butun botni to'xtatib qo'yadi — bitta xabar bunga arzimaydi. */
    @Test
    void longRateLimitIsNotWaited() throws IOException {
        TelegramApiRequestException e = apiError(
                "{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\",\"parameters\":{\"retry_after\":60}}");

        assertEquals(Optional.empty(), SendRetryPolicy.delayBeforeRetry(e, 1));
    }

    private TelegramApiRequestException apiError(String json) throws IOException {
        ApiResponse<?> response = new ObjectMapper().readValue(json, ApiResponse.class);
        return new TelegramApiRequestException("Error executing query", response);
    }
}
