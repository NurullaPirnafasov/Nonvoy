package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.OrderStatus;

import java.math.BigDecimal;

public interface OrderService {
    void changeStatus(Order order, OrderStatus newStatus);

    Order createOrder(User user);

    BigDecimal calculateTotal(Product product, int quantity);
}
