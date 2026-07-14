package uz.nonvoy.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.nonvoy.bot.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product,Long> {
}
