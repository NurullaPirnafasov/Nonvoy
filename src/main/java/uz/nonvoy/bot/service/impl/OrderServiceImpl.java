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
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductService productService;
    private final UserService userService;

    @Transactional
    @Override
    public Order changeStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Buyurtma #" + orderId + " topilmadi"));

        boolean allowed = switch (order.getStatus()) {
            case NEW -> newStatus == OrderStatus.ACCEPTED || newStatus == OrderStatus.CANCELLED;
            case ACCEPTED -> newStatus == OrderStatus.READY || newStatus == OrderStatus.CANCELLED;
            case READY, CANCELLED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException(order.getStatus() + "->" + newStatus + " o'tish ruxsat etilmagan");
        }

        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    @Override
    public BigDecimal calculateTotal(Product product, int quantity) {
        return product.getPrice().multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    @Override
    public List<OrderItem> findItems(Order order) {
        return orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
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