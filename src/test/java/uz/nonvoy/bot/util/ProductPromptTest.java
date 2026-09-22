package uz.nonvoy.bot.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProductPromptTest {

    @Test
    void tailCarriesActionProductAndListMessage() {
        assertEquals("[narx #3 @812]",
                new ProductPrompt(ProductPrompt.Action.PRICE, 3L, 812).tail());
        assertEquals("[nom #3 @812]",
                new ProductPrompt(ProductPrompt.Action.NAME, 3L, 812).tail());
    }

    /** Yangi mahsulotda hali id yo'q — quyruqda ham bo'lmaydi. */
    @Test
    void createTailHasNoProductId() {
        assertEquals("[yangi @812]",
                new ProductPrompt(ProductPrompt.Action.CREATE, null, 812).tail());
    }

    @Test
    void tailSurvivesRoundTrip() {
        for (ProductPrompt original : new ProductPrompt[]{
                new ProductPrompt(ProductPrompt.Action.PRICE, 3L, 812),
                new ProductPrompt(ProductPrompt.Action.NAME, 17L, 1),
                new ProductPrompt(ProductPrompt.Action.CREATE, null, 999)}) {
            assertEquals(Optional.of(original), ProductPrompt.parse("Savol matni\n" + original.tail()));
        }
    }

    /**
     * Xato xabari ham quyruq bilan tugaydi (20-qaror). Agar admin xato xabariga reply
     * qilsa, kontekst oxirgi quyruqdan olinishi kerak.
     */
    @Test
    void lastTailWins() {
        String text = "Eski [narx #1 @100] va yangi [nom #2 @200]";

        assertEquals(Optional.of(new ProductPrompt(ProductPrompt.Action.NAME, 2L, 200)),
                ProductPrompt.parse(text));
    }

    @Test
    void textWithoutTailIsNotAPrompt() {
        assertTrue(ProductPrompt.parse("Oddiy xabar").isEmpty());
        assertTrue(ProductPrompt.parse(null).isEmpty());
        assertTrue(ProductPrompt.parse("[notanish #1 @2]").isEmpty());
        assertTrue(ProductPrompt.parse("[narx #1]").isEmpty());
    }
}
