package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.enums.OrderStatus;
import uz.nonvoy.bot.service.OrderCardService;
import uz.nonvoy.bot.service.OrderService;
import uz.nonvoy.bot.util.CardAudience;
import uz.nonvoy.bot.util.OrderCardFormatter;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCardServiceImpl implements OrderCardService {

    /**
     * Telegram guruhga daqiqasiga ~20 ta xabar ruxsat beradi. Kassa bir kun ishlamay qolsa
     * ham, bitta buyruq guruhni kartalarga ko'mib tashlamasin.
     */
    static final int OPEN_CARDS_LIMIT = 20;

    private final OrderService orderService;

    @Value("${bot.admin-group-id}")
    private Long paymentGroupId;

    @Value("${bot.worker-group-id}")
    private Long workerGroupId;

    @Override
    public SendPhoto paymentCard(Order order) {
        return SendPhoto.builder()
                .chatId(String.valueOf(paymentGroupId))
                .photo(new InputFile(order.getReceiptFileId()))
                .caption(OrderCardFormatter.card(order, orderService.findItems(order), CardAudience.PAYMENT))
                .replyMarkup(OrderCardFormatter.keyboard(order, CardAudience.PAYMENT))
                .build();
    }

    @Override
    public SendMessage kitchenCard(Order order) {
        return SendMessage.builder()
                .chatId(workerGroupId)
                .text(OrderCardFormatter.card(order, orderService.findItems(order), CardAudience.KITCHEN))
                .replyMarkup(OrderCardFormatter.keyboard(order, CardAudience.KITCHEN))
                .build();
    }

    @Override
    public List<PartialBotApiMethod<?>> openCards(CardAudience audience) {
        // Kassada NEW oldin: asosiy ishi to'lovni tekshirish. ACCEPTED faqat bekor qilish
        // uchun kerak — ular ko'p bo'lsa ham yangi to'lovlarni limitdan siqib chiqarmasin
        List<OrderStatus> statuses = switch (audience) {
            case PAYMENT -> List.of(OrderStatus.NEW, OrderStatus.ACCEPTED);
            case KITCHEN -> List.of(OrderStatus.ACCEPTED);
        };
        Long chatId = audience == CardAudience.PAYMENT ? paymentGroupId : workerGroupId;

        List<Order> orders = new ArrayList<>();
        long total = 0;
        for (OrderStatus status : statuses) {
            orders.addAll(orderService.findOldest(status, OPEN_CARDS_LIMIT - orders.size()));
            total += orderService.countByStatus(status);
        }

        if (orders.isEmpty()) {
            return List.of(SendMessage.builder().chatId(chatId).text("Ochiq buyurtma yo'q ✅").build());
        }

        List<PartialBotApiMethod<?>> result = new ArrayList<>();
        for (Order order : orders) {
            result.add(audience == CardAudience.PAYMENT ? paymentCard(order) : kitchenCard(order));
        }
        long hidden = total - orders.size();
        if (hidden > 0) {
            result.add(SendMessage.builder()
                    .chatId(chatId)
                    .text("Yana " + hidden + " ta ochiq buyurtma bor. "
                            + "Yuqoridagilarni ko'rib chiqqach /buyurtmalar ni qayta yuboring")
                    .build());
        }
        return result;
    }
}
