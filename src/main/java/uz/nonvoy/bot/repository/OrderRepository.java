package uz.nonvoy.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.enums.OrderStatus;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findFirstByUserIdAndStatusOrderByIdAsc(Long userId, OrderStatus status);
}
