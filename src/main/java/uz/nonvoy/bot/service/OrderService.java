package uz.nonvoy.bot.service;

import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.enums.OrderStatus;

public interface OrderService {
    void changeStatus(Order order, OrderStatus newStatus);
}
