package uz.nonvoy.bot.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kassa guruhida matn so'rash konteksti. Admin state DB'da saqlanmaydi (18-qaror):
 * bot savolining oxiriga mashina o'qiydigan quyruq yoziladi va reply kelganda kontekst
 * o'sha quyruqdan tiklanadi.
 * <p>
 * Ko'rinishi: {@code [narx #3 @812]} — amal, mahsulot id'si va ro'yxat xabarining
 * messageId'si. Oxirgisi tufayli javob qayta ishlangach o'sha ro'yxat joyida yangilanadi,
 * guruhda yangi-yangi ro'yxatlar to'planmaydi (19-qaror).
 */
public record ProductPrompt(Action action, Long productId, Integer listMessageId) {

    public enum Action {
        PRICE("narx"),
        NAME("nom"),
        CREATE("yangi");

        private final String token;

        Action(String token) {
            this.token = token;
        }

        static Optional<Action> of(String token) {
            for (Action action : values()) {
                if (action.token.equals(token)) {
                    return Optional.of(action);
                }
            }
            return Optional.empty();
        }
    }

    private static final Pattern PATTERN = Pattern.compile("\\[(\\p{L}+)(?: #(\\d+))? @(\\d+)]");

    public String tail() {
        StringBuilder sb = new StringBuilder("[").append(action.token);
        if (productId != null) {
            sb.append(" #").append(productId);
        }
        return sb.append(" @").append(listMessageId).append(']').toString();
    }

    /** Reply qilingan xabardagi quyruqni o'qiydi. Quyruq bo'lmasa — bo'sh Optional. */
    public static Optional<ProductPrompt> parse(String promptText) {
        if (promptText == null) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(promptText);
        // Xato xabari ham quyruq bilan tugaydi (20-qaror), shuning uchun oxirgisi olinadi
        String token = null;
        String id = null;
        String listMessageId = null;
        while (matcher.find()) {
            token = matcher.group(1);
            id = matcher.group(2);
            listMessageId = matcher.group(3);
        }
        if (token == null) {
            return Optional.empty();
        }
        Optional<Action> action = Action.of(token);
        if (action.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ProductPrompt(
                    action.get(),
                    id == null ? null : Long.parseLong(id),
                    Integer.parseInt(listMessageId)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
