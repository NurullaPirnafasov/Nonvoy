package uz.nonvoy.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.*;
import uz.nonvoy.bot.entity.base.Auditable;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product extends Auditable {
    @Column(unique = true)
    private String name;
    private BigDecimal price;
    private boolean available;
}
