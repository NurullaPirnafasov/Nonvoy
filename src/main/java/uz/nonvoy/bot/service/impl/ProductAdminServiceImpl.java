package uz.nonvoy.bot.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.nonvoy.bot.entity.Product;
import uz.nonvoy.bot.service.ProductAdminService;
import uz.nonvoy.bot.service.ProductService;
import uz.nonvoy.bot.util.PriceFormatter;
import uz.nonvoy.bot.util.ProductInput;
import uz.nonvoy.bot.util.ProductPrompt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductAdminServiceImpl implements ProductAdminService {

    private final ProductService productService;

    private static final String PREFIX = "P:";
    private static final String LIST = "P:LIST";
    private static final String NEW = "P:NEW";
    private static final String VIEW = "P:VIEW:";
    private static final String PRICE = "P:PRICE:";
    private static final String NAME = "P:NAME:";
    private static final String DELETE = "P:DEL:";
    private static final String DELETE_CONFIRMED = "P:DELOK:";

    private static final String LIST_TITLE = "🍞 Mahsulotlar";
    private static final String EMPTY_LIST = "🍞 Mahsulotlar\n\nHozircha mahsulot yo'q.";

    @Override
    public List<PartialBotApiMethod<?>> openList(Long chatId) {
        return List.of(SendMessage.builder()
                .chatId(chatId)
                .text(listTitle())
                .replyMarkup(listKeyboard())
                .build());
    }

    @Override
    public boolean handlesCallback(String callbackData) {
        return callbackData != null && callbackData.startsWith(PREFIX);
    }

    @Override
    public List<PartialBotApiMethod<?>> handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        if (LIST.equals(data)) {
            return List.of(answer(callbackQuery, null), listScreen(chatId, messageId));
        }
        if (NEW.equals(data)) {
            return List.of(answer(callbackQuery, null),
                    prompt(chatId, callbackQuery.getFrom(),
                            "yangi mahsulotning nomi va narxini yozing, masalan: Non 6000",
                            new ProductPrompt(ProductPrompt.Action.CREATE, null, messageId)));
        }

        Long productId = parseId(data);
        if (productId == null) {
            return List.of(answer(callbackQuery, "Noma'lum tugma"));
        }
        Optional<Product> found = productService.findById(productId);
        if (found.isEmpty()) {
            // Boshqa admin o'chirib yuborgan bo'lishi mumkin — ro'yxatni qayta chizamiz
            return List.of(answer(callbackQuery, "Mahsulot topilmadi"), listScreen(chatId, messageId));
        }
        Product product = found.get();

        if (data.startsWith(VIEW)) {
            return List.of(answer(callbackQuery, null), cardScreen(chatId, messageId, product));
        }
        if (data.startsWith(PRICE)) {
            return List.of(answer(callbackQuery, null),
                    prompt(chatId, callbackQuery.getFrom(),
                            product.getName() + " uchun yangi narxni yozing (masalan: 6000)",
                            new ProductPrompt(ProductPrompt.Action.PRICE, productId, messageId)));
        }
        if (data.startsWith(NAME)) {
            return List.of(answer(callbackQuery, null),
                    prompt(chatId, callbackQuery.getFrom(),
                            product.getName() + " uchun yangi nomni yozing",
                            new ProductPrompt(ProductPrompt.Action.NAME, productId, messageId)));
        }
        if (data.startsWith(DELETE)) {
            return List.of(answer(callbackQuery, null), deleteScreen(chatId, messageId, product));
        }
        if (data.startsWith(DELETE_CONFIRMED)) {
            productService.delete(productId);
            return List.of(answer(callbackQuery, product.getName() + " o'chirildi"),
                    listScreen(chatId, messageId));
        }
        return List.of(answer(callbackQuery, "Noma'lum tugma"));
    }

    @Override
    public Optional<List<PartialBotApiMethod<?>>> handleReply(Message message) {
        Message repliedTo = message.getReplyToMessage();
        if (repliedTo == null || repliedTo.getFrom() == null || !Boolean.TRUE.equals(repliedTo.getFrom().getIsBot())) {
            return Optional.empty();
        }
        Optional<ProductPrompt> prompt = ProductPrompt.parse(repliedTo.getText());
        if (prompt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(applyReply(message, prompt.get()));
    }

    // --- Reply'ni qo'llash ---

    private List<PartialBotApiMethod<?>> applyReply(Message message, ProductPrompt prompt) {
        Long chatId = message.getChatId();
        if (!message.hasText()) {
            return List.of(retry(chatId, message.getFrom(), "Javobni matn bilan yozing", prompt));
        }
        String text = message.getText().trim();

        return switch (prompt.action()) {
            case CREATE -> create(chatId, message.getFrom(), text, prompt);
            case PRICE -> updatePrice(chatId, message.getFrom(), text, prompt);
            case NAME -> rename(chatId, message.getFrom(), text, prompt);
        };
    }

    private List<PartialBotApiMethod<?>> create(Long chatId, User from, String text, ProductPrompt prompt) {
        ProductInput.Result<ProductInput.NewProduct> parsed = ProductInput.newProduct(text);
        if (!parsed.ok()) {
            return List.of(retry(chatId, from, parsed.error(), prompt));
        }
        Product created;
        try {
            created = productService.create(parsed.value().name(), parsed.value().price());
        } catch (IllegalStateException e) {
            return List.of(retry(chatId, from, e.getMessage(), prompt));
        }
        return applied(chatId, prompt, "✅ Qo'shildi: " + label(created));
    }

    private List<PartialBotApiMethod<?>> updatePrice(Long chatId, User from, String text, ProductPrompt prompt) {
        ProductInput.Result<BigDecimal> parsed = ProductInput.price(text);
        if (!parsed.ok()) {
            return List.of(retry(chatId, from, parsed.error(), prompt));
        }
        return productService.updatePrice(prompt.productId(), parsed.value())
                .map(product -> applied(chatId, prompt, "✅ " + label(product)))
                .orElseGet(() -> applied(chatId, prompt, "Mahsulot topilmadi"));
    }

    private List<PartialBotApiMethod<?>> rename(Long chatId, User from, String text, ProductPrompt prompt) {
        ProductInput.Result<String> parsed = ProductInput.name(text);
        if (!parsed.ok()) {
            return List.of(retry(chatId, from, parsed.error(), prompt));
        }
        try {
            return productService.rename(prompt.productId(), parsed.value())
                    .map(product -> applied(chatId, prompt, "✅ " + label(product)))
                    .orElseGet(() -> applied(chatId, prompt, "Mahsulot topilmadi"));
        } catch (IllegalStateException e) {
            return List.of(retry(chatId, from, e.getMessage(), prompt));
        }
    }

    /**
     * Tasdiq xabari majburiy: ro'yxatni qayta chizish muvaffaqiyatsiz bo'lsa (xabar
     * o'chirilgan yoki juda eski) service buni bilmaydi, admin esa amal bajarilganini
     * ko'rishi kerak (21-qaror).
     */
    private List<PartialBotApiMethod<?>> applied(Long chatId, ProductPrompt prompt, String confirmation) {
        List<PartialBotApiMethod<?>> result = new ArrayList<>();
        result.add(SendMessage.builder().chatId(chatId).text(confirmation).build());
        result.add(listScreen(chatId, prompt.listMessageId()));
        return result;
    }

    /** Xato xabari ham quyruq bilan tugaydi — admin shunga reply qilib qayta uradi (20-qaror). */
    private SendMessage retry(Long chatId, User from, String error, ProductPrompt prompt) {
        return prompt(chatId, from, error, prompt);
    }

    // --- Ekranlar ---

    private EditMessageText listScreen(Long chatId, Integer messageId) {
        return EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(listTitle())
                .replyMarkup(listKeyboard())
                .build();
    }

    private EditMessageText cardScreen(Long chatId, Integer messageId, Product product) {
        return EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("🍞 " + label(product))
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(button("💵 Narxni o'zgartirish", PRICE + product.getId())))
                        .keyboardRow(List.of(button("✏️ Nomini o'zgartirish", NAME + product.getId())))
                        .keyboardRow(List.of(button("🗑 O'chirish", DELETE + product.getId())))
                        .keyboardRow(List.of(button("⬅️ Orqaga", LIST)))
                        .build())
                .build();
    }

    /** O'chirish qaytarilmaydigan amal, shuning uchun bu yerda tasdiq so'raladi (24-qaror). */
    private EditMessageText deleteScreen(Long chatId, Integer messageId, Product product) {
        return EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("🗑 " + label(product) + " o'chirilsinmi?\n\nEski buyurtmalar saqlanib qoladi.")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(
                                button("✅ Ha", DELETE_CONFIRMED + product.getId()),
                                button("⬅️ Yo'q", VIEW + product.getId())))
                        .build())
                .build();
    }

    /**
     * Savol alohida xabar bo'lib ketadi. {@code ForceReply(selective)} faqat matnda
     * mention qilingan foydalanuvchiga ishlaydi, shuning uchun admin HTML mention bilan
     * chaqiriladi — reply oynasi faqat unda ochiladi.
     */
    private SendMessage prompt(Long chatId, User admin, String question, ProductPrompt prompt) {
        return SendMessage.builder()
                .chatId(chatId)
                .parseMode("HTML")
                .text(mention(admin) + ", " + escape(question) + "\n" + prompt.tail())
                .replyMarkup(ForceReplyKeyboard.builder()
                        .forceReply(true)
                        .selective(true)
                        .build())
                .build();
    }

    private String listTitle() {
        return productService.findAll().isEmpty() ? EMPTY_LIST : LIST_TITLE;
    }

    private InlineKeyboardMarkup listKeyboard() {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
        for (Product product : productService.findAll()) {
            builder.keyboardRow(List.of(button(label(product), VIEW + product.getId())));
        }
        // Ro'yxatdan keyin: kunlik ish emas, lekin arxiv bo'lmagani uchun oxirgi qator
        builder.keyboardRow(List.of(button("➕ Yangi mahsulot", NEW)));
        return builder.build();
    }

    // --- Yordamchilar ---

    private String label(Product product) {
        return product.getName() + " — " + PriceFormatter.formatPrice(product.getPrice()) + " so'm";
    }

    private String mention(User admin) {
        return "<a href=\"tg://user?id=" + admin.getId() + "\">" + escape(admin.getFirstName()) + "</a>";
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Long parseId(String data) {
        int lastColon = data.lastIndexOf(':');
        if (lastColon < 0) {
            return null;
        }
        try {
            return Long.parseLong(data.substring(lastColon + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private AnswerCallbackQuery answer(CallbackQuery callbackQuery, String text) {
        return AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text(text)
                .build();
    }
}
