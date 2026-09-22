package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.OrderItem;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.OrderStatus;

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
    StatusChange changeStatus(Long orderId, OrderStatus newStatus);

    /**
     * Savatdan buyurtma yasaydi: narxlar {@code priceAtOrder}da muzlatiladi, savat
     * va chek qoralamasi tozalanadi.
     *
     * @throws IllegalStateException savat bo'sh yoki chek yuborilmagan bo'lsa
     */
    Order createOrder(User user);

    /**
     * To'lovi hali tekshirilmagan buyurtma. Chek — oddiy rasm, uni hech kim avtomatik
     * tekshirmaydi, shuning uchun bitta mijozda bir vaqtda bitta tekshirilmagan buyurtma
     * bo'lishi mumkin — aks holda kassa guruhini soxta buyurtmalar bilan to'ldirib tashlash oson.
     */
    Optional<Order> findPendingPayment(User user);

    Optional<Order> findById(Long orderId);

    List<OrderItem> findItems(Order order);
}
