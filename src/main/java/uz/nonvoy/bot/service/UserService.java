package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.UserState;

public interface UserService {
    User findOrCreate(Long telegramId, String name);

    void savePhone(User user, String phone);

    void updateState(User user, UserState newState);

    /** Miqdor so'rashdan oldin tanlangan mahsulotni eslab qoladi. */
    void saveDraftProduct(User user, Long productId);

    /** Chek rasmi buyurtma yaratilgungacha shu yerda kutadi. */
    void saveReceipt(User user, String receiptFileId);

    /** Qoralama (mahsulot va chek) tozalanadi — yarim qolgan buyurtma keyingisiga aralashmasin. */
    void resetToIdle(User user);
}
