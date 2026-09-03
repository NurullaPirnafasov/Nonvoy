package uz.nonvoy.bot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
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
            methods = routeMessage(update);
        } else if (update.hasCallbackQuery()) {
            methods = routeCallback(update);
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

    /**
     * Mijoz flow'i faqat shaxsiy chatda ishlaydi. Avval "admin guruhi bo'lmasa - mijoz"
     * degan mantiq bor edi: bot boshqa guruhga qo'shilsa yoki admin-group-id noto'g'ri
     * bo'lsa, o'sha guruhda buyurtma klaviaturasi paydo bo'lardi.
     */
    private List<BotApiMethod<?>> routeMessage(Update update) {
        Message message = update.getMessage();
        if (message.getChat().isUserChat()) {
            return customerFlowService.handleMessage(update);
        }
        if (adminGroupId.equals(message.getChatId())) {
            return adminFlowService.handleMessage(update);
        }
        return List.of();
    }

    private List<BotApiMethod<?>> routeCallback(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        // 48 soatdan eski xabar uchun Telegram Message emas, InaccessibleMessage yuboradi:
        // chat ma'lumoti ham, matni ham yo'q, ya'ni kartani tahrirlab bo'lmaydi.
        // MaybeInaccessibleMessage.isUserMessage() bu holatda ishonchsiz (doim true),
        // shuning uchun turini o'zini tekshiramiz
        if (!(callbackQuery.getMessage() instanceof Message message)) {
            return List.of(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .text("Bu xabar juda eski. Botga /start yuboring")
                    .showAlert(true)
                    .build());
        }
        if (message.getChat().isUserChat()) {
            return customerFlowService.handleCallback(update);
        }
        if (adminGroupId.equals(message.getChatId())) {
            return adminFlowService.handleCallback(update);
        }
        return List.of();
    }

    /** Foydalanuvchi javobsiz qolmasin: nima bo'lganini bilmasa ham, xato borligini bilsin. */
    private void notifyFailure(Update update) {
        Message message = null;
        if (update.hasMessage()) {
            message = update.getMessage();
        } else if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() instanceof Message m) {
            message = m;
        }
        // Begona guruhda gapirmaymiz - u yerda bot umuman javob bermasligi kerak
        if (message == null || !(message.getChat().isUserChat() || adminGroupId.equals(message.getChatId()))) {
            return;
        }
        Long chatId = message.getChatId();
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
