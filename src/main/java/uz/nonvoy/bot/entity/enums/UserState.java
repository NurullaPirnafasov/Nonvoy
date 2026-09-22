package uz.nonvoy.bot.entity.enums;

/**
 * Mijozning bot flow'idagi qadami. Redis kerak emas — holat DB'da (User.state) yashaydi.
 */
public enum UserState {
    NEW,
    WAITING_PHONE,
    /** Savatga qo'shish uchun mahsulot tanlanmoqda. */
    WAITING_PRODUCT,
    /** Tanlangan mahsulot (User.draftProductId) uchun miqdor kutilmoqda. */
    WAITING_QUANTITY,
    /** Savat ko'rsatildi: "Yana" / "To'g'ri" / "Bekor". */
    CART_REVIEW,
    /** Summa kartaga o'tkazildi deb hisoblanadi, chek rasmi kutilmoqda. */
    WAITING_RECEIPT,
    /** Chek + savat ko'rsatildi, yakuniy tasdiq kutilmoqda. */
    FINAL_CONFIRM,
    IDLE
}
