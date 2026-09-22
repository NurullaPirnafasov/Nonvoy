package uz.nonvoy.bot.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeChat;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
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

    @Value("${bot.worker-group-id}")
    private Long workerGroupId;

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    /**
     * Buyruqlarni Telegram menyusida ko'rsatadi: novvoy "/mahsulotlar"ni qo'lda yozmasin,
     * menyudan bosib ochsin. Kassa buyrug'i faqat o'sha guruh scope'ida — mijozlar
     * ro'yxatida ko'rinmaydi.
     */
    @Override
    public void onRegister() {
        super.onRegister();
        try {
            execute(SetMyCommands.builder()
                    .command(new BotCommand("start", "Botni boshlash"))
                    .scope(new BotCommandScopeDefault())
                    .build());
            execute(SetMyCommands.builder()
                    .command(new BotCommand("mahsulotlar", "Mahsulotlarni boshqarish"))
                    .scope(new BotCommandScopeChat(String.valueOf(adminGroupId)))
                    .build());
        } catch (TelegramApiException e) {
            // Buyruq menyusi bo'lmasa ham bot ishlayveradi
            log.error("Buyruqlar ro'yxati o'rnatilmadi", e);
        }
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
        List<PartialBotApiMethod<?>> methods;
        if (isHandledMessage(update)) {
            methods = routeMessage(update);
        } else if (update.hasCallbackQuery()) {
            methods = routeCallback(update);
        } else return;

        if (methods == null || methods.isEmpty()) return;
        for (PartialBotApiMethod<?> method : methods) {
            try {
                send(method);
            } catch (TelegramApiException e) {
                if (isAlreadyUpToDate(e)) {
                    log.debug("Xabar o'zgarmadi, tahrirlash o'tkazib yuborildi: {}", method.getClass().getSimpleName());
                } else {
                    log.error("Telegram API Exception", e);
                }
            }
        }
    }

    /**
     * Kartani haqiqiy holatga moslash urinishi (15-qaror) karta allaqachon to'g'ri bo'lganda
     * shu xatoga tushadi — masalan admin bir tugmani ikki marta bosganda. Telegram bir xil
     * kontent bilan tahrirlashni rad etadi, lekin bu xato emas: natija baribir kutilganidek.
     * Haqiqiy xatolar ko'rinib tursin uchun ERROR'ga aralashtirmaymiz.
     */
    private boolean isAlreadyUpToDate(TelegramApiException e) {
        return e.getMessage() != null && e.getMessage().contains("message is not modified");
    }

    /**
     * Chek rasmi bilan ishlash uchun service'lar {@link PartialBotApiMethod} qaytaradi:
     * {@link SendPhoto} {@link BotApiMethod} emas (rasm multipart bilan ketadi), shuning
     * uchun yagona {@code execute} overload'i yetmaydi va tur shu yerda ajratiladi.
     */
    private void send(PartialBotApiMethod<?> method) throws TelegramApiException {
        if (method instanceof BotApiMethod<?> apiMethod) {
            execute(apiMethod);
        } else if (method instanceof SendPhoto photo) {
            execute(photo);
        } else {
            log.error("Qo'llab-quvvatlanmaydigan metod turi: {}", method.getClass());
        }
    }

    /**
     * Mijoz flow'i faqat shaxsiy chatda ishlaydi. Avval "admin guruhi bo'lmasa - mijoz"
     * degan mantiq bor edi: bot boshqa guruhga qo'shilsa yoki admin-group-id noto'g'ri
     * bo'lsa, o'sha guruhda buyurtma klaviaturasi paydo bo'lardi.
     */
    private List<PartialBotApiMethod<?>> routeMessage(Update update) {
        Message message = update.getMessage();
        if (message.getChat().isUserChat()) {
            return customerFlowService.handleMessage(update);
        }
        if (isGroupChat(message.getChatId())) {
            return adminFlowService.handleMessage(update);
        }
        return List.of();
    }

    private List<PartialBotApiMethod<?>> routeCallback(Update update) {
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
        if (isGroupChat(message.getChatId())) {
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
        if (message == null || !(message.getChat().isUserChat() || isGroupChat(message.getChatId()))) {
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
    /** Kassa va ishchilar guruhlari — ikkalasi ham AdminFlowService'ga boradi, u yerda ajratiladi. */
    private boolean isGroupChat(Long chatId) {
        return adminGroupId.equals(chatId) || workerGroupId.equals(chatId);
    }

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
