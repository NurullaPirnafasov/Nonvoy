package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.repository.ProductRepository;
import uz.nonvoy.bot.service.ProductService;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    @Override
    public Optional<Product> getActiveProduct() {
        return productRepository.findFirstByAvailableTrue();
    }
}
