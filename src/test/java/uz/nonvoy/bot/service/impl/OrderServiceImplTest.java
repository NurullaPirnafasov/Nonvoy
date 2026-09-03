package uz.nonvoy.bot.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.OrderItem;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.OrderStatus;
import uz.nonvoy.bot.repository.OrderItemRepository;
import uz.nonvoy.bot.repository.OrderRepository;
import uz.nonvoy.bot.service.ProductService;
import uz.nonvoy.bot.service.UserService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductService productService;
    @Mock
    private UserService userService;

    @InjectMocks
    private OrderServiceImpl orderService;

    // --- Status o'tishlari ---

    @Test
    void newCanBeAcceptedOrCancelled() {
        assertEquals(OrderStatus.ACCEPTED, transition(OrderStatus.NEW, OrderStatus.ACCEPTED).getStatus());
        assertEquals(OrderStatus.CANCELLED, transition(OrderStatus.NEW, OrderStatus.CANCELLED).getStatus());
    }

    @Test
    void acceptedCanBecomeReadyOrCancelled() {
        assertEquals(OrderStatus.READY, transition(OrderStatus.ACCEPTED, OrderStatus.READY).getStatus());
        assertEquals(OrderStatus.CANCELLED, transition(OrderStatus.ACCEPTED, OrderStatus.CANCELLED).getStatus());
    }

    /** Qabul qilinmagan buyurtma tayyor bo'la olmaydi - navbat tartibi buzilmasin. */
    @Test
    void newCannotJumpToReady() {
        assertThrows(IllegalStateException.class, () -> transition(OrderStatus.NEW, OrderStatus.READY));
    }

    /** Bekor qilingan buyurtmani "Tayyor" qilish - novvoy tugmani adashib bosgan holat. */
    @Test
    void cancelledIsFinal() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThrows(IllegalStateException.class, () -> transition(OrderStatus.CANCELLED, target),
                    "CANCELLED -> " + target);
        }
    }

    /** Ikki marta bosishning ikkinchisi shu yerga tushadi. */
    @Test
    void readyIsFinal() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThrows(IllegalStateException.class, () -> transition(OrderStatus.READY, target),
                    "READY -> " + target);
        }
    }

    @Test
    void sameStatusTwiceIsRejected() {
        assertThrows(IllegalStateException.class, () -> transition(OrderStatus.ACCEPTED, OrderStatus.ACCEPTED));
    }

    @Test
    void rejectedTransitionIsNotSaved() {
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> orderService.changeStatus(1L, OrderStatus.READY));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void missingOrderThrows() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> orderService.changeStatus(99L, OrderStatus.ACCEPTED));
    }

    // --- Buyurtma yaratish ---

    /** Mahsulot narxi keyin o'zgarsa ham buyurtma tarixi buzilmasligi kerak. */
    @Test
    void orderItemFreezesPrice() {
        Product product = product(BigDecimal.valueOf(5000));
        User user = user(10);
        when(productService.getActiveProduct()).thenReturn(Optional.of(product));
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        Order order = orderService.createOrder(user);

        assertEquals(BigDecimal.valueOf(50000), order.getTotalAmountMoney());

        product.setPrice(BigDecimal.valueOf(6000));
        OrderItem saved = captureSavedItem();
        assertEquals(BigDecimal.valueOf(5000), saved.getPriceAtOrder());
        assertEquals(10, saved.getQuantity());
    }

    @Test
    void createOrderResetsUserToIdle() {
        when(productService.getActiveProduct()).thenReturn(Optional.of(product(BigDecimal.valueOf(5000))));
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        User user = user(3);

        orderService.createOrder(user);

        verify(userService).resetToIdle(user);
    }

    /** Miqdor kiritilgach novvoy nonni "tugadi" qilib qo'ysa. */
    @Test
    void createOrderFailsWhenNothingIsAvailable() {
        when(productService.getActiveProduct()).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> orderService.createOrder(user(5)));
        verify(orderRepository, never()).save(any());
    }

    // --- Yordamchilar ---

    private Order transition(OrderStatus from, OrderStatus to) {
        Order order = order(from);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        return orderService.changeStatus(1L, to);
    }

    private OrderItem captureSavedItem() {
        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository).save(captor.capture());
        return captor.getValue();
    }

    private Order order(OrderStatus status) {
        Order order = Order.builder().status(status).build();
        order.setId(1L);
        return order;
    }

    private Product product(BigDecimal price) {
        Product product = Product.builder().name("Non").price(price).available(true).build();
        product.setId(1L);
        return product;
    }

    private User user(int draftQuantity) {
        User user = User.builder().telegramId(123L).name("Alisher").draftQuantity(draftQuantity).build();
        user.setId(1L);
        return user;
    }
}