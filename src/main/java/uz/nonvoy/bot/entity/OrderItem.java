package uz.nonvoy.bot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.*;
import uz.nonvoy.bot.entity.base.Auditable;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderItem extends Auditable {
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    /**
     * Mahsulot o'chirilsa null bo'ladi — eski buyurtma kartasi baribir to'liq chiqishi
     * uchun nom va narx quyida muzlatilgan (23-qaror).
     */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private int quantity;

    /** Buyurtma paytidagi nom: mahsulot keyin qayta nomlansa yoki o'chirilsa ham tarix buzilmaydi. */
    private String productNameAtOrder;

    /** Buyurtma paytidagi narx: mahsulot narxi keyin o'zgarsa buyurtma summasi o'zgarmaydi. */
    private BigDecimal priceAtOrder;
}
