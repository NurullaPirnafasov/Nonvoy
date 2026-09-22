package uz.nonvoy.bot.entity;

import jakarta.persistence.*;
import lombok.*;
import uz.nonvoy.bot.entity.base.Auditable;

/**
 * Tugallanmagan buyurtma qoralamasi. Buyurtma faqat oxirgi tasdiqdan keyin yaratiladi,
 * shuning uchun bazada chala {@link Order} hech qachon qolmaydi (5-bosqich, 1-qaror).
 * <p>
 * Narx maydoni ataylab yo'q: savatda mahsulotning joriy narxi ko'rsatiladi, narx muzlatish
 * esa {@link OrderItem#getPriceAtOrder()}da (4-qaror).
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

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private int quantity;
}
