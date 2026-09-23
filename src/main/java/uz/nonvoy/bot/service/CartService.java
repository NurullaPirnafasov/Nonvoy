package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.CartItem;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;

import java.math.BigDecimal;
import java.util.List;

public interface CartService {

    /**
     * Savatga qo'shadi. Mahsulot allaqachon savatda bo'lsa yangi qator ochilmaydi,
     * miqdor mavjudiga qo'shiladi (3-qaror).
     *
     * @throws IllegalArgumentException jami miqdor {@code int}ga sig'masa
     */
    CartItem add(User user, Product product, int quantity);

    List<CartItem> findItems(User user);

    /**
     * Summa aytilguncha joriy narxlardan (4-qaror), muzlatilgandan keyin muzlatilgan
     * narxlardan hisoblanadi (34-qaror).
     */
    BigDecimal calculateTotal(List<CartItem> items);

    /**
     * Mijozga to'lanadigan summa aytilishidan oldin chaqiriladi: nom va narx muzlatiladi,
     * mijoz aynan shu summani o'tkazadi (34-qaror).
     */
    void freeze(User user);

    boolean isEmpty(User user);

    /** `/start`, "Bekor" va buyurtma yaratilgandan keyin chaqiriladi. */
    void clear(User user);
}
