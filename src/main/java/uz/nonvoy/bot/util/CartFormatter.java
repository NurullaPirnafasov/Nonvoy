package uz.nonvoy.bot.util;

import uz.nonvoy.bot.entity.CartItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * Savat ekrani. Narxlar joriy holatdan olinadi — savatda narx muzlatilmaydi (4-qaror),
 * shuning uchun bu yerda ham hisob-kitob yo'q: tayyor qiymatlar kiritiladi.
 */
public final class CartFormatter {

    private CartFormatter() {
    }

    public static String cart(List<CartItem> items, BigDecimal total) {
        StringBuilder sb = new StringBuilder("🧺 Savatingiz:\n");
        for (CartItem item : items) {
            sb.append("🍞 ")
                    .append(item.getProduct().getName())
                    .append(" × ")
                    .append(item.getQuantity())
                    .append(" — ")
                    .append(PriceFormatter.formatPrice(lineTotal(item)))
                    .append(" so'm\n");
        }
        // Mijoz aynan shu summani kartaga o'tkazadi, shuning uchun bitta pozitsiyada ham ko'rsatiladi
        sb.append("💰 Jami: ").append(PriceFormatter.formatPrice(total)).append(" so'm");
        return sb.toString();
    }

    private static BigDecimal lineTotal(CartItem item) {
        return item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}
