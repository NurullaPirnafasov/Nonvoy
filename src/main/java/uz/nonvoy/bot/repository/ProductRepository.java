package uz.nonvoy.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.nonvoy.bot.entity.Product;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product,Long> {
    Optional<Product> findFirstByAvailableTrueOrderByIdAsc();

    List<Product> findAllByOrderByIdAsc();
}