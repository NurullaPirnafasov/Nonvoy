package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.OrderItem;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderService {

    /**
     * Statusni validatsiya bilan o'zgartiradi va saqlaydi.
     * Buyurtma id bo'yicha tranzaksiya ichida qayta o'qiladi — ikki admin bir vaqtda
     * tugma bosganda eskirgan holat ustidan yozib yuborilmasligi uchun.
     *
     * @throws IllegalStateException ruxsat etilmagan o'tishda
     */
    Order changeStatus(Long orderId, OrderStatus newStatus);

    Order createOrder(User user);

    Optional<Order> findById(Long orderId);

    List<OrderItem> findItems(Order order);

    BigDecimal calculateTotal(Product product, int quantity);
}