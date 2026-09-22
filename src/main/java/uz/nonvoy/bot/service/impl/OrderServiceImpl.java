package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.nonvoy.bot.entity.CartItem;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.OrderItem;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.OrderStatus;
import uz.nonvoy.bot.repository.OrderItemRepository;
import uz.nonvoy.bot.repository.OrderRepository;
import uz.nonvoy.bot.service.CartService;
import uz.nonvoy.bot.service.OrderService;
import uz.nonvoy.bot.service.StatusChange;
import uz.nonvoy.bot.service.UserService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final UserService userService;

    @Transactional
    @Override
    public StatusChange changeStatus(Long orderId, OrderStatus newStatus) {
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

        OrderStatus previous = order.getStatus();
        order.setStatus(newStatus);
        return new StatusChange(orderRepository.save(order), previous);
    }

    @Override
    public Optional<Order> findPendingPayment(User user) {
        return orderRepository.findFirstByUserIdAndStatusOrderByIdAsc(user.getId(), OrderStatus.NEW);
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
        List<CartItem> cartItems = cartService.findItems(user);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Savat bo'sh");
        }
        String receiptFileId = user.getDraftReceiptFileId();
        if (receiptFileId == null || receiptFileId.isBlank()) {
            throw new IllegalStateException("Chek yuborilmagan");
        }

        Order order = orderRepository.save(Order.builder()
                .user(user)
                .receiptFileId(receiptFileId)
                .totalAmountMoney(cartService.calculateTotal(cartItems))
                .build());

        // Narx ham, miqdor ham shu daqiqada muzlatiladi: mahsulot narxi keyin
        // o'zgarsa buyurtma tarixi buzilmasligi kerak
        orderItemRepository.saveAll(cartItems.stream()
                .map(cartItem -> OrderItem.builder()
                        .order(order)
                        .product(cartItem.getProduct())
                        .quantity(cartItem.getQuantity())
                        .productNameAtOrder(cartItem.getProduct().getName())
                        .priceAtOrder(cartItem.getProduct().getPrice())
                        .build())
                .toList());

        cartService.clear(user);
        userService.resetToIdle(user);
        return order;
    }
}
