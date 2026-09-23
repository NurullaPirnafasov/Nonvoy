package uz.nonvoy.bot.entity;

import jakarta.persistence.*;
import lombok.*;
import uz.nonvoy.bot.entity.base.Auditable;

import java.math.BigDecimal;

/**
 * Tugallanmagan buyurtma qoralamasi. Buyurtma faqat oxirgi tasdiqdan keyin yaratiladi,
 * shuning uchun bazada chala {@link Order} hech qachon qolmaydi (5-bosqich, 1-qaror).
 * <p>
 * Savat ko'rilayotganda mahsulotning joriy narxi ko'rsatiladi (4-qaror). Mijozga to'lanadigan
 * summa aytilgan paytda ({@code CART_OK}) esa nom va narx muzlatiladi: mijoz aynan shu summani
 * o'tkazadi, keyingi narx o'zgarishi yoki mahsulot o'chirilishi unga ta'sir qilmaydi (34-qaror).
 */
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
        name = "cart_items",
        // Bir xil mahsulot ikkinchi marta qo'shilsa miqdor qo'shiladi, yangi qator emas (3-qaror).
        // Birlashtirish service'da bo'lsa ham, kafolat baza darajasida turadi.
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"})
)
public class CartItem extends Auditable {
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Muzlatilgan qatorda mahsulot o'chirilsa null bo'ladi — nom va narx quyida saqlangan,
     * xuddi {@link OrderItem} kabi (23-qaror).
     */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private int quantity;

    /** Summa aytilgan paytdagi nom. Undan oldin null. */
    private String nameAtCheckout;

    /** Summa aytilgan paytdagi narx. Undan oldin null. */
    private BigDecimal priceAtCheckout;

    public boolean isFrozen() {
        return priceAtCheckout != null;
    }

    /** Muzlatilgan bo'lsa o'sha, aks holda mahsulotning joriy nomi. */
    public String displayName() {
        return isFrozen() ? nameAtCheckout : product.getName();
    }

    /** Muzlatilgan bo'lsa o'sha, aks holda mahsulotning joriy narxi. */
    public BigDecimal unitPrice() {
        return isFrozen() ? priceAtCheckout : product.getPrice();
    }
}
