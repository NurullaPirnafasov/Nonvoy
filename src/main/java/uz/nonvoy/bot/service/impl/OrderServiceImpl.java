package uz.nonvoy.bot.service.impl;

import org.springframework.stereotype.Service;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.enums.OrderStatus;
import uz.nonvoy.bot.service.OrderService;

@Service
public class OrderServiceImpl implements OrderService {

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
}
