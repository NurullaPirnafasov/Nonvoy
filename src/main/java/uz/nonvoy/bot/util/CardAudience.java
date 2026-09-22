package uz.nonvoy.bot.util;

/**
 * Buyurtma kartasi kimga chiqayotgani. Yangi status qo'shilmaydi (10-qaror) — bitta
 * Order ikki guruhda ikki xil ko'rinadi, farq faqat shu yerda.
 */
public enum CardAudience {
    /** Kassa guruhi: chek rasmi, telefon, narxlar — to'lovni tekshirish uchun. */
    PAYMENT,
    /** Ishchilar guruhi: faqat nima yopish kerakligi. Chek ham, telefon ham, narx ham yo'q (13-qaror). */
    KITCHEN
}
