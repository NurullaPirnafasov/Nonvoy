package uz.nonvoy.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.nonvoy.bot.entity.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order,Long> {

}
