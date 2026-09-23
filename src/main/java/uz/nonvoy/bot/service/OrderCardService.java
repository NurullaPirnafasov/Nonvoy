package uz.nonvoy.bot.service;

import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.util.CardAudience;

import java.util.List;

/**
 * Guruhlarga yuboriladigan buyurtma kartalari. Karta ikki joyda yasaladi — buyurtma
 * yaratilganda / status o'zganda va {@code /buyurtmalar} so'ralganda — shuning uchun bitta joyda.
 */
public interface OrderCardService {

    /** Kassa kartasi: chek rasmi + caption + status bo'yicha tugmalar (13-qaror). */
    SendPhoto paymentCard(Order order);

    /** Ishchilar kartasi: oddiy matn, chek ham, telefon ham, narx ham yo'q (13-qaror). */
    SendMessage kitchenCard(Order order);

    /**
     * Guruhdagi ochiq buyurtmalar kartalarini qayta chiqaradi. Karta yuborilayotganda tarmoq
     * uzilsa, service buni bilmaydi (15-qaror) — guruh o'zi so'rab oladi.
     */
    List<PartialBotApiMethod<?>> openCards(CardAudience audience);
}
