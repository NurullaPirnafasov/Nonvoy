package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.service.AdminFlowService;
import uz.nonvoy.bot.service.OrderService;
import uz.nonvoy.bot.service.ProductService;
import uz.nonvoy.bot.telegram.OrderAction;
import uz.nonvoy.bot.util.OrderCardFormatter;
import uz.nonvoy.bot.util.PriceFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Novvoy (admin) guruhidagi update'lar. Alohida panel yo'q: buyurtma kartasi
 * guruhga tushadi, status inline tugmalar orqali o'zgaradi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminFlowServiceImpl implements AdminFlowService {

    private final OrderService orderService;
    private final ProductService productService;

    private static final String PRODUCTS_COMMAND = "/mahsulotlar";
    private static final String PRODUCT_TOGGLE_PREFIX = "PRODUCT:";
    private static final String PRODUCTS_TITLE = "🍞 Mahsulotlar\n\nHolatni o'zgartirish uchun tugmani bosing:";

    @Override
    public List<BotApiMethod<?>> handleMessage(Update update) {
        Message message = update.getMessage();
        if (message == null || !message.hasText()) {
            // Guruhda har xabarga javob berish shovqin — faqat buyruqlarga javob beramiz
            return List.of();
        }
        if (isProductsCommand(message.getText())) {
            return List.of(SendMessage.builder()
                    .chatId(message.getChatId())
                    .text(PRODUCTS_TITLE)
                    .replyMarkup(productsKeyboard())
                    .build());
        }
        return List.of();
    }

    @Override
    public List<BotApiMethod<?>> handleCallback(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String data = callbackQuery.getData();

        if (data != null && data.startsWith(PRODUCT_TOGGLE_PREFIX)) {
            return handleProductToggle(callbackQuery, data);
        }
        return handleOrderAction(callbackQuery, data);
    }

    private List<BotApiMethod<?>> handleOrderAction(CallbackQuery callbackQuery, String data) {
        Optional<OrderAction> action = OrderAction.parse(data);
        Optional<Long> orderId = OrderAction.parseOrderId(data);
        if (action.isEmpty() || orderId.isEmpty()) {
            return List.of(answer(callbackQuery, "Noma'lum tugma"));
        }

        Optional<Order> existing = orderService.findById(orderId.get());
        if (existing.isEmpty()) {
            return List.of(answer(callbackQuery, "Buyurtma #" + orderId.get() + " topilmadi"));
        }

        Order order;
        try {
            order = orderService.changeStatus(orderId.get(), action.get().getStatus());
        } catch (IllegalStateException e) {
            // Ikki marta bosish yoki bekor qilingan buyurtmani "Tayyor" qilish shu yerga tushadi.
            // Kartani qayta chizmaymiz — matn o'zgarmagani uchun Telegram baribir rad etardi;
            // o'rniga kartadagi tugmalarni haqiqiy holatga moslaymiz
            log.info("Ruxsatsiz status o'tishi: order={}, action={}", orderId.get(), action.get(), e);
            List<BotApiMethod<?>> result = new ArrayList<>();
            result.add(answer(callbackQuery, alreadyHandledText(existing.get())));
            result.add(EditMessageReplyMarkup.builder()
                    .chatId(callbackQuery.getMessage().getChatId())
                    .messageId(callbackQuery.getMessage().getMessageId())
                    .replyMarkup(OrderCardFormatter.keyboard(existing.get()))
                    .build());
            return result;
        }

        List<BotApiMethod<?>> result = new ArrayList<>();
        result.add(answer(callbackQuery, null));
        result.add(EditMessageText.builder()
                .chatId(callbackQuery.getMessage().getChatId())
                .messageId(callbackQuery.getMessage().getMessageId())
                .text(OrderCardFormatter.card(order, orderService.findItems(order)))
                .replyMarkup(OrderCardFormatter.keyboard(order))
                .build());
        result.add(SendMessage.builder()
                .chatId(order.getUser().getTelegramId())
                .text(customerNotification(order))
                .build());
        return result;
    }

    private List<BotApiMethod<?>> handleProductToggle(CallbackQuery callbackQuery, String data) {
        long productId;
        try {
            productId = Long.parseLong(data.substring(PRODUCT_TOGGLE_PREFIX.length()));
        } catch (NumberFormatException e) {
            return List.of(answer(callbackQuery, "Noma'lum tugma"));
        }

        Optional<Product> toggled = productService.toggleAvailability(productId);
        if (toggled.isEmpty()) {
            return List.of(answer(callbackQuery, "Mahsulot topilmadi"));
        }

        Product product = toggled.get();
        List<BotApiMethod<?>> result = new ArrayList<>();
        result.add(answer(callbackQuery, product.getName() + (product.isAvailable() ? " — bor" : " — tugadi")));
        // Sarlavha o'zgarmaydi, faqat tugmalar — shuning uchun EditMessageText emas
        // (bir xil matn bilan tahrirlash Telegram'da xato beradi)
        result.add(EditMessageReplyMarkup.builder()
                .chatId(callbackQuery.getMessage().getChatId())
                .messageId(callbackQuery.getMessage().getMessageId())
                .replyMarkup(productsKeyboard())
                .build());
        return result;
    }

    private InlineKeyboardMarkup productsKeyboard() {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
        for (Product product : productService.findAll()) {
            String label = product.getName()
                    + " — " + PriceFormatter.formatPrice(product.getPrice()) + " so'm"
                    + (product.isAvailable() ? " ✅ bor" : " ❌ tugadi");
            builder.keyboardRow(List.of(InlineKeyboardButton.builder()
                    .text(label)
                    .callbackData(PRODUCT_TOGGLE_PREFIX + product.getId())
                    .build()));
        }
        return builder.build();
    }

    private String customerNotification(Order order) {
        return switch (order.getStatus()) {
            case ACCEPTED -> "Buyurtmangiz #" + order.getId() + " qabul qilindi ✅";
            case READY -> "Noningiz tayyor, olib ketishingiz mumkin 🍞 (buyurtma #" + order.getId() + ")";
            case CANCELLED -> "Afsuski buyurtmangiz #" + order.getId() + " bekor qilindi ❌";
            case NEW -> "Buyurtmangiz #" + order.getId() + " navbatda";
        };
    }

    private String alreadyHandledText(Order order) {
        return switch (order.getStatus()) {
            case NEW -> "Buyurtma hali navbatda";
            case ACCEPTED -> "Buyurtma allaqachon qabul qilingan";
            case READY -> "Buyurtma allaqachon tayyor";
            case CANCELLED -> "Buyurtma bekor qilingan";
        };
    }

    private AnswerCallbackQuery answer(CallbackQuery callbackQuery, String text) {
        return AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text(text)
                .build();
    }

    /** Guruhda buyruq "/mahsulotlar@bot_username" ko'rinishida ham keladi. */
    private boolean isProductsCommand(String text) {
        String command = text.trim().split("\\s+")[0];
        int at = command.indexOf('@');
        if (at > 0) {
            command = command.substring(0, at);
        }
        return PRODUCTS_COMMAND.equals(command);
    }
}