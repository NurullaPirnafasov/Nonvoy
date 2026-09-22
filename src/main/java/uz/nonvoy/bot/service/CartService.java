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
     */
    CartItem add(User user, Product product, int quantity);

    List<CartItem> findItems(User user);

    /** Joriy narxlar bo'yicha hisoblanadi — savatda narx muzlatilmaydi (4-qaror). */
    BigDecimal calculateTotal(List<CartItem> items);

    boolean isEmpty(User user);

    /** `/start`, "Bekor" va buyurtma yaratilgandan keyin chaqiriladi. */
    void clear(User user);
}
