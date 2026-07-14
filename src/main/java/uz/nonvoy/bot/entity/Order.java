package uz.nonvoy.bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.nonvoy.bot.entity.base.Auditable;
import uz.nonvoy.bot.entity.enums.OrderStatus;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "orders")
public class Order extends Auditable {
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status=OrderStatus.NEW;
    private BigDecimal totalAmount;
}
