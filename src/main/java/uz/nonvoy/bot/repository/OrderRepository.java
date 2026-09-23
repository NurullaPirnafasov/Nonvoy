package uz.nonvoy.bot.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.enums.OrderStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findFirstByUserIdAndStatusOrderByIdAsc(Long userId, OrderStatus status);

    List<Order> findByStatusOrderByIdAsc(OrderStatus status, Limit limit);

    long countByStatus(OrderStatus status);
}
