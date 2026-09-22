package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductService {

    List<Product> findAll();

    Optional<Product> findById(Long productId);

    /**
     * Yangi mahsulot qo'shadi.
     *
     * @throws IllegalStateException shu nomli mahsulot allaqachon bo'lsa
     */
    Product create(String name, BigDecimal price);

    Optional<Product> updatePrice(Long productId, BigDecimal price);

    /**
     * @throws IllegalStateException yangi nom band bo'lsa
     */
    Optional<Product> rename(Long productId, String name);

    /**
     * Mahsulotni butunlay o'chiradi. Eski buyurtmalar saqlanib qoladi: OrderItem'dagi
     * ishora null'ga qo'yiladi, nom va narx u yerda allaqachon muzlatilgan (23-qaror).
     * Savatlardagi qatorlar esa o'chiriladi — sotilmaydigan narsa savatda turmasin.
     */
    boolean delete(Long productId);
}
