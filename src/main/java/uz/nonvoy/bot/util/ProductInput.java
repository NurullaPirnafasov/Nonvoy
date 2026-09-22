package uz.nonvoy.bot.util;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kassa yozgan matnni tekshiradi. Xato matni shu yerda tug'iladi, chunki javob
 * foydalanuvchiga boradi — exception emas, o'qiladigan gap kerak (26-qaror).
 */
public final class ProductInput {

    /** Inline tugmaga nom ham, narx ham sig'ishi kerak. */
    private static final int MAX_NAME_LENGTH = 32;

    private static final Pattern NAME_AND_PRICE = Pattern.compile("(.+?)\\s+(\\d[\\d\\s_\u00A0]*)");

    private ProductInput() {
    }

    /** Muvaffaqiyat: {@code error == null}. Aks holda {@code value} bo'sh. */
    public record Result<T>(T value, String error) {
        public boolean ok() {
            return error == null;
        }

        static <T> Result<T> of(T value) {
            return new Result<>(value, null);
        }

        static <T> Result<T> error(String message) {
            return new Result<>(null, message);
        }
    }

    public record NewProduct(String name, BigDecimal price) {
    }

    public static Result<BigDecimal> price(String raw) {
        if (raw == null || raw.isBlank()) {
            return Result.error("Narxni raqam bilan yozing, masalan: 6000");
        }
        // "6 000", "6_000" va uzilmas bo'shliqli nusxalar ham qabul qilinadi
        String digits = raw.trim().replaceAll("[\\s\\u00A0_]", "");
        if (!digits.matches("\\d+")) {
            return Result.error("Narx faqat raqamdan iborat bo'lsin, masalan: 6000");
        }
        BigDecimal price = new BigDecimal(digits);
        if (price.signum() <= 0) {
            return Result.error("Narx 0 dan katta bo'lishi kerak");
        }
        return Result.of(price);
    }

    public static Result<String> name(String raw) {
        if (raw == null || raw.isBlank()) {
            return Result.error("Nom bo'sh bo'lmasin");
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.startsWith("/")) {
            return Result.error("Nom \"/\" bilan boshlanmasin — bu buyruqqa o'xshab qoladi");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return Result.error("Nom " + MAX_NAME_LENGTH + " belgidan oshmasin");
        }
        return Result.of(name);
    }

    /**
     * "Non 6000" — oxirgi token narx, qolgani nom. Shu tartib tufayli bo'sh joyli
     * nom ham bir ma'noli parse qilinadi (25-qaror).
     */
    public static Result<NewProduct> newProduct(String raw) {
        if (raw == null || raw.isBlank()) {
            return Result.error("Nom va narxni yozing, masalan: Non 6000");
        }
        // Narx "7 000" ko'rinishida ham kelishi mumkin, shuning uchun oxirgi "so'z" emas,
        // oxirgi raqamli bo'lak ajratiladi
        Matcher matcher = NAME_AND_PRICE.matcher(raw.trim());
        if (!matcher.matches()) {
            return Result.error("Nom va narxni birga yozing, masalan: Non 6000");
        }

        Result<BigDecimal> price = price(matcher.group(2));
        if (!price.ok()) {
            return Result.error(price.error());
        }
        Result<String> name = name(matcher.group(1));
        if (!name.ok()) {
            return Result.error(name.error());
        }
        return Result.of(new NewProduct(name.value(), price.value()));
    }
}
