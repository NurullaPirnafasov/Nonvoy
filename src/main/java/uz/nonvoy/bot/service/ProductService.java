package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductService {

    List<Product> findAll();

    Optional<Product> findById(Long productId);

    /** "Bor / tugadi" holatini teskarisiga o'giradi. Mahsulot topilmasa — bo'sh Optional. */
    Optional<Product> toggleAvailability(Long productId);
}
