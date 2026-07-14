package uz.nonvoy.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.nonvoy.bot.entity.base.Auditable;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Product extends Auditable {
    @Column(unique = true)
    private String name;
    private BigDecimal price;
    private boolean available;
}
