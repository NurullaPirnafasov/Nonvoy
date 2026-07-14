package uz.nonvoy.bot.entity;


import jakarta.persistence.*;
import lombok.*;
import uz.nonvoy.bot.entity.base.Auditable;
import uz.nonvoy.bot.entity.enums.UserRole;
import uz.nonvoy.bot.entity.enums.UserState;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users")
public class User extends Auditable {
    @Column(unique = true, nullable = false)
    private Long telegramId;
    private String  name;
    private String phone;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserRole role=UserRole.CUSTOMER;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserState state=UserState.NEW;
}
