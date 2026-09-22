package uz.nonvoy.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.nonvoy.bot.entity.OrderItem;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    /** Mahsulot o'chirilganda eski buyurtmalar qolsin: ishora uziladi, qator qoladi. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItem oi set oi.product = null where oi.product.id = :productId")
    void detachProduct(@Param("productId") Long productId);
}
