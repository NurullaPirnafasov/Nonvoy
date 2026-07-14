package uz.nonvoy.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.nonvoy.bot.entity.OrderItem;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem,Long> {
}
