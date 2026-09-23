package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.repository.CartItemRepository;
import uz.nonvoy.bot.repository.OrderItemRepository;
import uz.nonvoy.bot.repository.ProductRepository;
import uz.nonvoy.bot.service.ProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;

    @Override
    public List<Product> findAll() {
        return productRepository.findAllByOrderByIdAsc();
    }

    @Override
    public Optional<Product> findById(Long productId) {
        return productId == null ? Optional.empty() : productRepository.findById(productId);
    }

    @Transactional
    @Override
    public Product create(String name, BigDecimal price) {
        requireFreeName(name, null);
        return productRepository.save(Product.builder().name(name).price(price).build());
    }

    @Transactional
    @Override
    public Optional<Product> updatePrice(Long productId, BigDecimal price) {
        return findById(productId).map(product -> {
            product.setPrice(price);
            return productRepository.save(product);
        });
    }

    @Transactional
    @Override
    public Optional<Product> rename(Long productId, String name) {
        return findById(productId).map(product -> {
            requireFreeName(name, productId);
            product.setName(name);
            return productRepository.save(product);
        });
    }

    @Transactional
    @Override
    public boolean delete(Long productId) {
        Optional<Product> product = findById(productId);
        if (product.isEmpty()) {
            return false;
        }
        // Tartib muhim: FK'lar bo'shatilmaguncha qatorni o'chirib bo'lmaydi
        orderItemRepository.detachProduct(productId);
        // Hali ko'rilayotgan savatdan mahsulot shunchaki tushib qoladi. Summasi aytilgan
        // (muzlatilgan) qator esa qoladi: mijoz uni allaqachon to'lagan bo'lishi mumkin (34-qaror)
        cartItemRepository.deleteUnfrozenByProductId(productId);
        cartItemRepository.detachProduct(productId);
        productRepository.delete(product.get());
        return true;
    }

    /** Nom unique: takroriy nom guruhda ikki bir xil tugma chiqaradi va chalkashlik beradi. */
    private void requireFreeName(String name, Long allowedId) {
        productRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(allowedId))
                .ifPresent(existing -> {
                    throw new IllegalStateException("\"" + existing.getName() + "\" allaqachon bor");
                });
    }
}
