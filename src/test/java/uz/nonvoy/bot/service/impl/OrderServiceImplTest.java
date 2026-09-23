package uz.nonvoy.bot.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.nonvoy.bot.entity.CartItem;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.OrderItem;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.OrderStatus;
import uz.nonvoy.bot.repository.OrderItemRepository;
import uz.nonvoy.bot.repository.OrderRepository;
import uz.nonvoy.bot.service.CartService;
import uz.nonvoy.bot.service.StatusChange;
import uz.nonvoy.bot.service.UserService;

import java.math.BigDecimal;
import java.util.List;
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
    private CartService cartService;
    @Mock
    private UserService userService;

    @InjectMocks
    private OrderServiceImpl orderService;

    // --- Status o'tishlari ---

    @Test
    void newCanBeAcceptedOrCancelled() {
        assertEquals(OrderStatus.ACCEPTED, transition(OrderStatus.NEW, OrderStatus.ACCEPTED).order().getStatus());
        assertEquals(OrderStatus.CANCELLED, transition(OrderStatus.NEW, OrderStatus.CANCELLED).order().getStatus());
    }

    @Test
    void acceptedCanBecomeReadyOrCancelled() {
        assertEquals(OrderStatus.READY, transition(OrderStatus.ACCEPTED, OrderStatus.READY).order().getStatus());
        assertEquals(OrderStatus.CANCELLED, transition(OrderStatus.ACCEPTED, OrderStatus.CANCELLED).order().getStatus());
    }

    /**
     * Mijozga boradigan matn eski statusdan chiqadi (11-qaror): NEW→CANCELLED "to'lov
     * topilmadi", ACCEPTED→CANCELLED "bekor qilindi". Shuning uchun eski holat qaytariladi.
     */
    @Test
    void changeReportsPreviousStatus() {
        assertEquals(OrderStatus.NEW, transition(OrderStatus.NEW, OrderStatus.CANCELLED).from());
        assertEquals(OrderStatus.ACCEPTED, transition(OrderStatus.ACCEPTED, OrderStatus.CANCELLED).from());
    }

    /** Qabul qilinmagan buyurtma tayyor bo'la olmaydi - navbat tartibi buzilmasin. */
    @Test
    void newCannotJumpToReady() {
        assertThrows(IllegalStateException.class, () -> transition(OrderStatus.NEW, OrderStatus.READY));
    }

    /** Bekor qilingan buyurtmani "Tayyor" qilish - ishchi tugmani adashib bosgan holat. */
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

    // --- Tekshirilmagan buyurtma ---

    /**
     * Faqat `NEW` yangi buyurtmani to'sadi. `ACCEPTED`da pul allaqachon kelgan, ya'ni bu
     * odam spamer emas — non tandirda turganda ham yana buyurtma bera olishi kerak.
     */
    @Test
    void onlyUnverifiedOrderBlocksTheNextOne() {
        User user = user();
        Order pending = order(OrderStatus.NEW);
        when(orderRepository.findFirstByUserIdAndStatusOrderByIdAsc(1L, OrderStatus.NEW))
                .thenReturn(Optional.of(pending));

        assertEquals(Optional.of(pending), orderService.findPendingPayment(user));
        verify(orderRepository).findFirstByUserIdAndStatusOrderByIdAsc(1L, OrderStatus.NEW);
    }

    @Test
    void noPendingOrderMeansOrderingIsOpen() {
        when(orderRepository.findFirstByUserIdAndStatusOrderByIdAsc(1L, OrderStatus.NEW))
                .thenReturn(Optional.empty());

        assertTrue(orderService.findPendingPayment(user()).isEmpty());
    }

    // --- Savatdan buyurtma yaratish ---

    /** Mahsulot narxi keyin o'zgarsa ham buyurtma tarixi buzilmasligi kerak. */
    @Test
    void orderItemsFreezePrice() {
        Product non = product("Non", 5000);
        Product patir = product("Patir", 7000);
        User user = userWithReceipt();
        cartContains(user, cartItem(non, 10), cartItem(patir, 5));
        when(cartService.calculateTotal(any())).thenReturn(BigDecimal.valueOf(85000));
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        Order order = orderService.createOrder(user);

        assertEquals(BigDecimal.valueOf(85000), order.getTotalAmountMoney());

        non.setPrice(BigDecimal.valueOf(6000));
        List<OrderItem> saved = captureSavedItems();
        assertEquals(2, saved.size());
        assertEquals(BigDecimal.valueOf(5000), saved.get(0).getPriceAtOrder());
        assertEquals(10, saved.get(0).getQuantity());
        assertEquals(BigDecimal.valueOf(7000), saved.get(1).getPriceAtOrder());
    }

    /**
     * Mijoz summa aytilgan paytdagi narxni to'lagan (34-qaror): chek yuborilayotganda narx
     * o'zgarsa ham buyurtma muzlatilgan narx bilan yaratiladi.
     */
    @Test
    void orderUsesPriceFrozenAtCheckout() {
        Product non = product("Non", 5000);
        CartItem item = frozen(cartItem(non, 10));
        non.setPrice(BigDecimal.valueOf(6000));
        non.setName("Oq non");
        User user = userWithReceipt();
        cartContains(user, item);
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        orderService.createOrder(user);

        OrderItem saved = captureSavedItems().get(0);
        assertEquals(BigDecimal.valueOf(5000), saved.getPriceAtOrder());
        assertEquals("Non", saved.getProductNameAtOrder());
    }

    /** Mahsulot o'chirilgan bo'lsa ham to'langan qator buyurtmaga kiradi. */
    @Test
    void frozenItemOfDeletedProductStillBecomesOrderItem() {
        CartItem item = frozen(cartItem(product("Patir", 7000), 2));
        item.setProduct(null);
        User user = userWithReceipt();
        cartContains(user, item);
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        orderService.createOrder(user);

        OrderItem saved = captureSavedItems().get(0);
        assertNull(saved.getProduct());
        assertEquals("Patir", saved.getProductNameAtOrder());
        assertEquals(BigDecimal.valueOf(7000), saved.getPriceAtOrder());
    }

    /** Chek buyurtma bilan birga saqlanadi — kassa kartasi aynan shu rasm bilan chiqadi. */
    @Test
    void orderKeepsReceipt() {
        User user = userWithReceipt();
        cartContains(user, cartItem(product("Non", 5000), 1));
        when(cartService.calculateTotal(any())).thenReturn(BigDecimal.valueOf(5000));
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertEquals("chek-file-id", orderService.createOrder(user).getReceiptFileId());
    }

    /** Buyurtma yaratilgach savat ham, qoralama ham qolmasligi kerak. */
    @Test
    void createOrderClearsCartAndDraft() {
        User user = userWithReceipt();
        cartContains(user, cartItem(product("Non", 5000), 1));
        when(cartService.calculateTotal(any())).thenReturn(BigDecimal.valueOf(5000));
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        orderService.createOrder(user);

        verify(cartService).clear(user);
        verify(userService).resetToIdle(user);
    }

    @Test
    void emptyCartCannotBecomeOrder() {
        User user = userWithReceipt();
        cartContains(user);

        assertThrows(IllegalStateException.class, () -> orderService.createOrder(user));
        verify(orderRepository, never()).save(any());
    }

    /** Chek yo'q bo'lsa kassa tekshiradigan narsa qolmaydi. */
    @Test
    void orderWithoutReceiptIsRejected() {
        User user = user();
        cartContains(user, cartItem(product("Non", 5000), 1));

        assertThrows(IllegalStateException.class, () -> orderService.createOrder(user));
        verify(orderRepository, never()).save(any());
    }

    // --- Yordamchilar ---

    private StatusChange transition(OrderStatus from, OrderStatus to) {
        Order order = order(from);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        return orderService.changeStatus(1L, to);
    }

    private void cartContains(User user, CartItem... items) {
        when(cartService.findItems(user)).thenReturn(List.of(items));
    }

    @SuppressWarnings("unchecked")
    private List<OrderItem> captureSavedItems() {
        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private Order order(OrderStatus status) {
        Order order = Order.builder().status(status).build();
        order.setId(1L);
        return order;
    }

    private Product product(String name, long price) {
        Product product = Product.builder().name(name).price(BigDecimal.valueOf(price)).build();
        product.setId(1L);
        return product;
    }

    private CartItem cartItem(Product product, int quantity) {
        return CartItem.builder().product(product).quantity(quantity).build();
    }

    private CartItem frozen(CartItem item) {
        item.setNameAtCheckout(item.getProduct().getName());
        item.setPriceAtCheckout(item.getProduct().getPrice());
        return item;
    }

    private User user() {
        User user = User.builder().telegramId(123L).name("Alisher").build();
        user.setId(1L);
        return user;
    }

    private User userWithReceipt() {
        User user = user();
        user.setDraftReceiptFileId("chek-file-id");
        return user;
    }
}
