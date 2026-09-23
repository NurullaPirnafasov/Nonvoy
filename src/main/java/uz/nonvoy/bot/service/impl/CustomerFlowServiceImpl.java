package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.nonvoy.bot.entity.CartItem;
import uz.nonvoy.bot.entity.Order;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.entity.User;
import uz.nonvoy.bot.entity.enums.UserState;
import uz.nonvoy.bot.service.CartService;
import uz.nonvoy.bot.service.CustomerFlowService;
import uz.nonvoy.bot.service.OrderCardService;
import uz.nonvoy.bot.service.OrderService;
import uz.nonvoy.bot.service.ProductService;
import uz.nonvoy.bot.service.UserService;
import uz.nonvoy.bot.util.CartFormatter;
import uz.nonvoy.bot.util.PriceFormatter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mijoz flow'i: mahsulot → miqdor → savat → chek → yakuniy tasdiq → buyurtma.
 * Buyurtma faqat oxirgi tasdiqdan keyin yaratiladi, shuning uchun bazada chala Order qolmaydi (1-qaror).
 */
@Service
@RequiredArgsConstructor
public class CustomerFlowServiceImpl implements CustomerFlowService {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;
    private final CartService cartService;
    private final OrderCardService orderCardService;

    @Value("${bot.payment-card}")
    private String paymentCard;

    @Value("${bot.payment-card-holder}")
    private String paymentCardHolder;

    private static final String START_COMMAND = "/start";
    private static final String ORDER_BUTTON = "Buyurtma berish";

    private static final String PICK_PREFIX = "PICK:";
    private static final String CART_MORE = "CART:MORE";
    private static final String CART_OK = "CART:OK";
    private static final String CART_CANCEL = "CART:CANCEL";
    private static final String FINAL_OK = "FINAL:OK";
    private static final String FINAL_CANCEL = "FINAL:CANCEL";

    private static final String CANCEL_HINT = "\nBekor qilish uchun /start yuboring";

    /**
     * Albom bir nechta update bo'lib keladi — har biriga javob bersak bot bir xil xabarni
     * bir necha marta yozadi. Oxirgi rad etilgan albom shu yerda eslab qolinadi (7-qaror):
     * bu vaqtinchalik UI holati, bazaga yozilmaydi.
     */
    private final Map<Long, String> rejectedAlbums = new ConcurrentHashMap<>();

    @Override
    public List<PartialBotApiMethod<?>> handleMessage(Update update) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        User user = userService.findOrCreate(message.getFrom().getId(), message.getFrom().getFirstName());

        // /start har qanday state'dan ishlaydi: aks holda miqdor so'ralayotganda
        // foydalanuvchi raqam kiritmaguncha flow'dan chiqa olmaydi
        if (isStartCommand(message)) {
            return handleStart(user, chatId);
        }

        return switch (user.getState()) {
            case NEW -> reply(chatId, "/start buyrug'ini yuboring");
            case WAITING_PHONE -> handleWaitingPhone(message, user, chatId);
            case IDLE -> handleIdle(message, user, chatId);
            case WAITING_PRODUCT -> reply(chatId, "Mahsulotni yuqoridagi tugmalardan tanlang." + CANCEL_HINT);
            case WAITING_QUANTITY -> handleWaitingQuantity(message, user, chatId);
            case CART_REVIEW -> reply(chatId, "Savat ostidagi tugmalardan birini tanlang." + CANCEL_HINT);
            case WAITING_RECEIPT, FINAL_CONFIRM -> handleReceiptPhoto(message, user, chatId);
        };
    }

    @Override
    public List<PartialBotApiMethod<?>> handleCallback(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        User user = userService.findOrCreate(callbackQuery.getFrom().getId(), callbackQuery.getFrom().getFirstName());

        UserState state = user.getState();
        if (state == UserState.WAITING_PRODUCT && data != null && data.startsWith(PICK_PREFIX)) {
            return handlePick(callbackQuery, user, chatId, messageId, data);
        }
        if (state == UserState.CART_REVIEW && isCartAction(data)) {
            return handleCartAction(callbackQuery, user, chatId, messageId, data);
        }
        if (state == UserState.FINAL_CONFIRM && isFinalAction(data)) {
            return handleFinalAction(callbackQuery, user, chatId, messageId, data);
        }
        // Eskirgan ekrandagi tugma: foydalanuvchi allaqachon boshqa qadamda
        return List.of(answer(callbackQuery, "Bu tugma eskirgan. /start yuboring"));
    }

    // --- Qadamlar ---

    /** Ro'yxatdan o'tmaganni telefon so'rashga, o'tganni boshlang'ich holatga qaytaradi. */
    private List<PartialBotApiMethod<?>> handleStart(User user, Long chatId) {
        cartService.clear(user);
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            userService.updateState(user, UserState.WAITING_PHONE);
            return reply(chatId,
                    "Botdan to'liq foydalanishingiz uchun telefon raqamingizni yuboring",
                    contactKeyboard());
        }
        userService.resetToIdle(user);
        return reply(chatId, "Buyurtma berish uchun tugmani bosing", orderKeyboard());
    }

    private List<PartialBotApiMethod<?>> handleWaitingPhone(Message message, User user, Long chatId) {
        if (!message.hasContact()) {
            return reply(chatId, "Iltimos telefon raqamingizni yuboring", contactKeyboard());
        }
        if (!user.getTelegramId().equals(message.getContact().getUserId())) {
            return reply(chatId, "O'zingizning raqamingizni yuboring", contactKeyboard());
        }
        userService.savePhone(user, message.getContact().getPhoneNumber());
        return reply(chatId, "Rahmat! Endi bemalol buyurtma bera olasiz", orderKeyboard());
    }

    private List<PartialBotApiMethod<?>> handleIdle(Message message, User user, Long chatId) {
        if (message.hasText() && ORDER_BUTTON.equals(message.getText().trim())) {
            return startOrder(user, chatId);
        }
        return reply(chatId, "Buyurtma berish uchun tugmani bosing", orderKeyboard());
    }

    /** Eski savat qolib ketmasin: yangi buyurtma doim toza savatdan boshlanadi. */
    private List<PartialBotApiMethod<?>> startOrder(User user, Long chatId) {
        // Chek — oddiy rasm, uni hech kim avtomatik tekshirmaydi. Shuning uchun bir vaqtda
        // bitta tekshirilmagan buyurtma: aks holda kassa guruhini soxta buyurtma bilan
        // to'ldirib tashlash oson. To'lov tasdiqlangach (ACCEPTED) qulf o'zi ochiladi.
        Optional<Order> pending = orderService.findPendingPayment(user);
        if (pending.isPresent()) {
            return reply(chatId,
                    "Oldingi buyurtmangiz #" + pending.get().getId() + " to'lov tekshiruvida ⏳\n"
                            + "Tasdiqlangach yangi buyurtma bera olasiz\n"
                            + "Uzoq cho'zilsa, novvoyxonaga ayting",
                    orderKeyboard());
        }

        cartService.clear(user);
        List<Product> products = productService.findAll();
        if (products.isEmpty()) {
            userService.resetToIdle(user);
            return reply(chatId, "Hozircha mahsulot yo'q", orderKeyboard());
        }
        userService.updateState(user, UserState.WAITING_PRODUCT);
        return reply(chatId, "Mahsulotni tanlang:", productKeyboard(products));
    }

    private List<PartialBotApiMethod<?>> handlePick(CallbackQuery callbackQuery, User user,
                                                    Long chatId, Integer messageId, String data) {
        Long productId = parseId(data.substring(PICK_PREFIX.length()));
        Product product = productId == null ? null : productService.findById(productId).orElse(null);
        if (product == null) {
            return List.of(answer(callbackQuery, "Mahsulot topilmadi"));
        }

        userService.saveDraftProduct(user, product.getId());
        List<PartialBotApiMethod<?>> result = new ArrayList<>();
        result.add(answer(callbackQuery, null));
        // Tugmalar olib tashlanadi — bitta ekrandan ikki marta tanlash chalkashlik beradi
        result.add(stripKeyboard(chatId, messageId, "Tanlandi: " + product.getName()));
        result.add(message(chatId, "Nechta " + product.getName() + " kerak? Raqam bilan yozing." + CANCEL_HINT));
        return result;
    }

    private List<PartialBotApiMethod<?>> handleWaitingQuantity(Message message, User user, Long chatId) {
        if (!message.hasText()) {
            return reply(chatId, "Miqdorni raqam bilan yozing, masalan: 10." + CANCEL_HINT);
        }
        int quantity;
        try {
            quantity = Integer.parseInt(message.getText().trim());
        } catch (NumberFormatException e) {
            return reply(chatId, "Miqdorni raqam bilan yozing, masalan: 10." + CANCEL_HINT);
        }
        // Yuqori chegara yo'q: muzlatkichga ko'p miqdorda olish odatiy hol
        if (quantity <= 0) {
            return reply(chatId, "Miqdor kamida 1 ta bo'lishi kerak." + CANCEL_HINT);
        }

        Product product = productService.findById(user.getDraftProductId()).orElse(null);
        if (product == null) {
            // Miqdor yozilguncha mahsulot o'chirilgan bo'lishi mumkin
            return startOrder(user, chatId);
        }

        cartService.add(user, product, quantity);
        userService.updateState(user, UserState.CART_REVIEW);
        return List.of(cartScreen(user, chatId));
    }

    private List<PartialBotApiMethod<?>> handleCartAction(CallbackQuery callbackQuery, User user,
                                                          Long chatId, Integer messageId, String data) {
        List<PartialBotApiMethod<?>> result = new ArrayList<>();
        result.add(answer(callbackQuery, null));

        switch (data) {
            case CART_MORE -> {
                List<Product> products = productService.findAll();
                if (products.isEmpty()) {
                    result.add(message(chatId, "Boshqa mahsulot yo'q"));
                    return result;
                }
                userService.updateState(user, UserState.WAITING_PRODUCT);
                result.add(stripKeyboard(chatId, messageId, cartText(user)));
                result.add(message(chatId, "Mahsulotni tanlang:", productKeyboard(products)));
            }
            case CART_OK -> {
                List<CartItem> items = cartService.findItems(user);
                if (items.isEmpty()) {
                    userService.resetToIdle(user);
                    result.add(message(chatId, "Savat bo'sh", orderKeyboard()));
                    return result;
                }
                userService.updateState(user, UserState.WAITING_RECEIPT);
                result.add(stripKeyboard(chatId, messageId, cartText(user)));
                result.add(message(chatId, paymentInstruction(cartService.calculateTotal(items))));
            }
            case CART_CANCEL -> {
                cartService.clear(user);
                userService.resetToIdle(user);
                result.add(stripKeyboard(chatId, messageId, "Buyurtma bekor qilindi ❌"));
                result.add(message(chatId, "Buyurtma berish uchun tugmani bosing", orderKeyboard()));
            }
            default -> result.add(message(chatId, "Noma'lum tugma"));
        }
        return result;
    }

    /**
     * Chek qadami. FINAL_CONFIRM'da yangi rasm eskisini almashtiradi (9-qaror): mijoz aynan
     * shu qadamda chekini ko'radi va xato yuborganini payqaydi; rad etilsa butun buyurtmani
     * qaytadan boshlashga majbur bo'lardi.
     */
    private List<PartialBotApiMethod<?>> handleReceiptPhoto(Message message, User user, Long chatId) {
        if (message.getMediaGroupId() != null) {
            String previous = rejectedAlbums.put(user.getTelegramId(), message.getMediaGroupId());
            if (message.getMediaGroupId().equals(previous)) {
                // O'sha albomning qolgan rasmlari — bir marta javob berdik, yetarli
                return List.of();
            }
            return reply(chatId, "Iltimos, faqat bitta rasm yuboring");
        }

        if (!message.hasPhoto()) {
            // Chek faqat rasm: document/PDF qabul qilinmaydi (6-qaror)
            return reply(chatId, "Chekni rasm ko'rinishida yuboring." + CANCEL_HINT);
        }

        rejectedAlbums.remove(user.getTelegramId());
        userService.saveReceipt(user, largestPhoto(message.getPhoto()));
        return List.of(finalConfirmScreen(user, chatId));
    }

    private List<PartialBotApiMethod<?>> handleFinalAction(CallbackQuery callbackQuery, User user,
                                                           Long chatId, Integer messageId, String data) {
        List<PartialBotApiMethod<?>> result = new ArrayList<>();
        result.add(answer(callbackQuery, null));

        if (FINAL_CANCEL.equals(data)) {
            cartService.clear(user);
            userService.resetToIdle(user);
            result.add(removeScreen(chatId, messageId));
            result.add(message(chatId, "Buyurtma bekor qilindi ❌\nBuyurtma berish uchun tugmani bosing",
                    orderKeyboard()));
            return result;
        }

        Order order;
        try {
            order = orderService.createOrder(user);
        } catch (IllegalStateException e) {
            // Savat yoki chek yo'qolgan holat. Ikki marta bosishning ikkinchisi ham shu yerga
            // tushadi: birinchisi savatni tozalab ulgurgan bo'ladi, ya'ni ikkinchi buyurtma
            // yaratilmaydi
            cartService.clear(user);
            userService.resetToIdle(user);
            result.add(removeScreen(chatId, messageId));
            result.add(message(chatId, "Buyurtma rasmiylashtirilmadi. Qaytadan urinib ko'ring",
                    orderKeyboard()));
            return result;
        }

        result.add(removeScreen(chatId, messageId));
        result.add(message(chatId,
                "Buyurtmangiz #" + order.getId() + " qabul qilindi. To'lov tekshirilmoqda ⏳",
                orderKeyboard()));
        result.add(orderCardService.paymentCard(order));
        return result;
    }

    // --- Ekranlar ---

    private SendMessage cartScreen(User user, Long chatId) {
        return message(chatId, cartText(user), InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button("➕ Yana", CART_MORE),
                        button("✅ To'g'ri", CART_OK)))
                .keyboardRow(List.of(button("❌ Bekor", CART_CANCEL)))
                .build());
    }

    private SendPhoto finalConfirmScreen(User user, Long chatId) {
        List<CartItem> items = cartService.findItems(user);
        String caption = CartFormatter.cart(items, cartService.calculateTotal(items))
                + "\n\nChek to'g'rimi? Tasdiqlang:";
        return SendPhoto.builder()
                .chatId(chatId)
                .photo(new InputFile(user.getDraftReceiptFileId()))
                .caption(caption)
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(
                                button("✅ Tasdiqlash", FINAL_OK),
                                button("❌ Bekor", FINAL_CANCEL)))
                        .build())
                .build();
    }

    private String cartText(User user) {
        List<CartItem> items = cartService.findItems(user);
        return CartFormatter.cart(items, cartService.calculateTotal(items));
    }

    private String paymentInstruction(BigDecimal total) {
        return "💳 " + PriceFormatter.formatPrice(total) + " so'mni quyidagi kartaga o'tkazing:\n\n"
                + paymentCard + "\n"
                + paymentCardHolder + "\n\n"
                + "So'ng chek rasmini shu yerga yuboring." + CANCEL_HINT;
    }

    private InlineKeyboardMarkup productKeyboard(List<Product> products) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
        for (Product product : products) {
            builder.keyboardRow(List.of(button(
                    product.getName() + " — " + PriceFormatter.formatPrice(product.getPrice()) + " so'm",
                    PICK_PREFIX + product.getId())));
        }
        return builder.build();
    }

    // --- Yordamchilar ---

    /**
     * Telegram bitta rasmni bir necha o'lchamda yuboradi — bu albom emas (8-qaror),
     * eng kattasi olinadi.
     */
    private String largestPhoto(List<PhotoSize> photos) {
        return photos.stream()
                .max(Comparator.comparingInt(photo -> photo.getWidth() * photo.getHeight()))
                .map(PhotoSize::getFileId)
                .orElseThrow(() -> new IllegalStateException("Rasm bo'sh"));
    }

    private EditMessageText stripKeyboard(Long chatId, Integer messageId, String text) {
        return EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .build();
    }

    /**
     * Chek rasmi tasdiqdan keyin mijozga hech narsa aytmaydi — caption almashtirish o'rniga
     * ekran butunlay olib tashlanadi, chatda faqat natija matni qoladi.
     */
    private DeleteMessage removeScreen(Long chatId, Integer messageId) {
        return DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build();
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private Long parseId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isCartAction(String data) {
        return CART_MORE.equals(data) || CART_OK.equals(data) || CART_CANCEL.equals(data);
    }

    private boolean isFinalAction(String data) {
        return FINAL_OK.equals(data) || FINAL_CANCEL.equals(data);
    }

    private boolean isStartCommand(Message message) {
        return message.hasText() && START_COMMAND.equals(message.getText().trim());
    }

    private AnswerCallbackQuery answer(CallbackQuery callbackQuery, String text) {
        return AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text(text)
                .build();
    }

    private List<PartialBotApiMethod<?>> reply(Long chatId, String text) {
        return List.of(message(chatId, text));
    }

    private List<PartialBotApiMethod<?>> reply(Long chatId, String text, ReplyKeyboard keyboard) {
        return List.of(message(chatId, text, keyboard));
    }

    private SendMessage message(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
    }

    private SendMessage message(Long chatId, String text, ReplyKeyboard keyboard) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .build();
    }

    private ReplyKeyboardMarkup orderKeyboard() {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(List.of(KeyboardButton.builder().text(ORDER_BUTTON).build())))
                .resizeKeyboard(true)
                .build();
    }

    private ReplyKeyboardMarkup contactKeyboard() {
        KeyboardButton contactButton = KeyboardButton.builder()
                .text("📞 Telefon raqamingizni yuboring")
                .requestContact(true)
                .build();
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(List.of(contactButton)))
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();
    }
}
