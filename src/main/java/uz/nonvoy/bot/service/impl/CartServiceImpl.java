package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.nonvoy.bot.entity.CartItem;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.repository.CartItemRepository;
import uz.nonvoy.bot.service.CartService;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;

    @Transactional
    @Override
    public CartItem add(User user, Product product, int quantity) {
        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                .orElseGet(() -> CartItem.builder()
                        .user(user)
                        .product(product)
                        .quantity(0)
                        .build());
        item.setQuantity(item.getQuantity() + quantity);
        return cartItemRepository.save(item);
    }

    @Override
    public List<CartItem> findItems(User user) {
        return cartItemRepository.findByUserIdOrderByIdAsc(user.getId());
    }

    @Override
    public BigDecimal calculateTotal(List<CartItem> items) {
        return items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    @Override
    public void freeze(User user) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByIdAsc(user.getId());
        for (CartItem item : items) {
            // Qayta chaqirilsa va'da qilingan narx qayta yozilmasin
            if (item.isFrozen()) {
                continue;
            }
            item.setNameAtCheckout(item.getProduct().getName());
            item.setPriceAtCheckout(item.getProduct().getPrice());
        }
        cartItemRepository.saveAll(items);
    }

    @Override
    public boolean isEmpty(User user) {
        return cartItemRepository.findByUserIdOrderByIdAsc(user.getId()).isEmpty();
    }

    @Transactional
    @Override
    public void clear(User user) {
        cartItemRepository.deleteByUserId(user.getId());
    }
}
