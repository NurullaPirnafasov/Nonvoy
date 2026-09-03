package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductService {

    Optional<Product> getActiveProduct();

    List<Product> findAll();

    /** "Bor / tugadi" holatini teskarisiga o'giradi. Mahsulot topilmasa — bo'sh Optional. */
    Optional<Product> toggleAvailability(Long productId);
}