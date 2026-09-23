package uz.nonvoy.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.nonvoy.bot.entity.CartItem;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByIdAsc(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserId(Long userId);

    /** Summasi hali aytilmagan savat qatorlari: mahsulot o'chsa ular ham o'chadi. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CartItem ci where ci.product.id = :productId and ci.priceAtCheckout is null")
    void deleteUnfrozenByProductId(@Param("productId") Long productId);

    /** Muzlatilgan qatorlar qoladi, faqat mahsulotga ishora uziladi (34-qaror). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CartItem ci set ci.product = null where ci.product.id = :productId")
    void detachProduct(@Param("productId") Long productId);
}
