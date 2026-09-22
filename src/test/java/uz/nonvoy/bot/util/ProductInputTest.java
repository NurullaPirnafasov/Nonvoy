package uz.nonvoy.bot.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductInputTest {

    // --- Narx ---

    @Test
    void priceAcceptsPlainNumber() {
        assertEquals(BigDecimal.valueOf(6000), ProductInput.price("6000").value());
    }

    /** Nusxa olingan narxda ajratgich bo'lishi odatiy — uzilmas bo'shliq ham. */
    @Test
    void priceIgnoresSeparators() {
        assertEquals(BigDecimal.valueOf(6000), ProductInput.price(" 6 000 ").value());
        assertEquals(BigDecimal.valueOf(6000), ProductInput.price("6_000").value());
        assertEquals(BigDecimal.valueOf(6000), ProductInput.price("6" + (char) 0x00A0 + "000").value());
    }

    @Test
    void priceRejectsNonNumbers() {
        assertFalse(ProductInput.price("6000 so'm").ok());
        assertFalse(ProductInput.price("olti ming").ok());
        assertFalse(ProductInput.price("-500").ok());
        assertFalse(ProductInput.price("").ok());
        assertFalse(ProductInput.price(null).ok());
    }

    /** Tekin non sotilmaydi — 0 xato deb hisoblanadi. */
    @Test
    void priceRejectsZero() {
        assertFalse(ProductInput.price("0").ok());
    }

    // --- Nom ---

    @Test
    void nameIsTrimmedAndCollapsed() {
        assertEquals("Qora non", ProductInput.name("  Qora   non ").value());
    }

    @Test
    void nameRejectsCommandLikeAndTooLong() {
        assertFalse(ProductInput.name("/mahsulotlar").ok());
        assertFalse(ProductInput.name("   ").ok());
        assertFalse(ProductInput.name("a".repeat(33)).ok());
        assertTrue(ProductInput.name("a".repeat(32)).ok());
    }

    // --- Nom + narx ---

    /** Oxirgi token narx, qolgani nom — shuning uchun bo'sh joyli nom ham ishlaydi (25-qaror). */
    @Test
    void newProductSplitsNameFromTrailingPrice() {
        ProductInput.NewProduct parsed = ProductInput.newProduct("Qora bug'doy noni 7 000").value();

        assertEquals("Qora bug'doy noni", parsed.name());
        assertEquals(BigDecimal.valueOf(7000), parsed.price());
    }

    @Test
    void newProductNeedsBothParts() {
        assertFalse(ProductInput.newProduct("Non").ok());
        assertFalse(ProductInput.newProduct("").ok());
        assertFalse(ProductInput.newProduct(null).ok());
    }

    /** Xato matni foydalanuvchiga boradi — bo'sh bo'lmasligi kerak. */
    @Test
    void errorsExplainWhatToDo() {
        assertNotNull(ProductInput.newProduct("Non").error());
        assertFalse(ProductInput.newProduct("Non arzon").error().isBlank());
    }
}
