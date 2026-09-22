package uz.nonvoy.bot.service;

import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.Optional;

/**
 * Kassa guruhidagi mahsulot boshqaruvi: ro'yxat, karta, qo'shish/tahrirlash/o'chirish.
 * Buyurtma kartalaridan ajratilgan — ikkalasi bitta klassda turса AdminFlowServiceImpl
 * o'qilmas bo'lib ketadi.
 */
public interface ProductAdminService {

    /** {@code /mahsulotlar} javobi. */
    List<PartialBotApiMethod<?>> openList(Long chatId);

    boolean handlesCallback(String callbackData);

    List<PartialBotApiMethod<?>> handleCallback(CallbackQuery callbackQuery);

    /**
     * Bot savoliga qilingan reply bo'lsa — javobni qaytaradi, aks holda bo'sh Optional
     * (xabar boshqa birov uchun, aralashmaymiz).
     */
    Optional<List<PartialBotApiMethod<?>>> handleReply(Message message);
}
