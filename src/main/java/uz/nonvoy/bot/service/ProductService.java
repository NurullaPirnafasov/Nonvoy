package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.Product;

import java.util.Optional;

public interface ProductService {
    Optional<Product> getActiveProduct();
}
