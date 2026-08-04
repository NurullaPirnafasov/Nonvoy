package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.OrderItem;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.OrderStatus;
import uz.nonvoy.bot.repository.OrderItemRepository;
import uz.nonvoy.bot.repository.OrderRepository;
import uz.nonvoy.bot.service.OrderService;
import uz.nonvoy.bot.service.ProductService;
import uz.nonvoy.bot.service.UserService;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductService productService;
    private final UserService userService;

    @Override
    public void changeStatus(Order order, OrderStatus newStatus) {
        switch (order.getStatus()) {
            case NEW:
                if (newStatus == OrderStatus.ACCEPTED || newStatus == OrderStatus.CANCELLED) {
                    order.setStatus(newStatus);
                    break;
                } else {
                    throw new IllegalStateException(order.getStatus() + "->" + newStatus + " o'tish ruxsat etilmagan");
                }
            case ACCEPTED:
                if (newStatus == OrderStatus.READY || newStatus == OrderStatus.CANCELLED) {
                    order.setStatus(newStatus);
                    break;
                } else {
                    throw new IllegalStateException(order.getStatus() + "->" + newStatus + " o'tish ruxsat etilmagan");
                }
            default:
                throw new IllegalStateException(order.getStatus() + "->" + newStatus + " o'tish ruxsat etilmagan");
        }
    }

    @Override
    public BigDecimal calculateTotal(Product product, int quantity) {
        return product.getPrice().multiply(BigDecimal.valueOf(quantity));
    }

    @Transactional
    @Override
    public Order createOrder(User user) {
        Product product = productService.getActiveProduct()
                .orElseThrow(() -> new IllegalStateException("Faol mahsulot yo'q"));
        int quantity = user.getDraftQuantity();
        Order order = Order.builder()
                .totalAmountMoney(calculateTotal(product, quantity))
                .user(user)
                .build();
        Order savedOrder = orderRepository.save(order);
        OrderItem item = OrderItem.builder()
                .order(savedOrder)
                .product(product)
                .quantity(quantity)
                .priceAtOrder(product.getPrice())
                .build();
        orderItemRepository.save(item);
        userService.resetToIdle(user);
        return savedOrder;
    }
}
