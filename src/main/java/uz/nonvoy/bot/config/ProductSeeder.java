package uz.nonvoy.bot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.repository.ProductRepository;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ProductSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) throws Exception {
        if (productRepository.count() == 0) {
            Product product = Product.builder()
                    .name("Non")
                    .price(BigDecimal.valueOf(5000))
                    .available(true)
                    .build();
            productRepository.save(product);
        }
    }
}
