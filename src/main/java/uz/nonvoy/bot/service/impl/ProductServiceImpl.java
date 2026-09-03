package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.repository.ProductRepository;
import uz.nonvoy.bot.service.ProductService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    public Optional<Product> getActiveProduct() {
        return productRepository.findFirstByAvailableTrueOrderByIdAsc();
    }

    @Override
    public List<Product> findAll() {
        return productRepository.findAllByOrderByIdAsc();
    }

    @Transactional
    @Override
    public Optional<Product> toggleAvailability(Long productId) {
        return productRepository.findById(productId)
                .map(product -> {
                    product.setAvailable(!product.isAvailable());
                    return productRepository.save(product);
                });
    }
}