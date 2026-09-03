package uz.nonvoy.bot.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceFormatterTest {

    // Uzilmas bo'shliq. Escape bilan yozilgan: manba faylda oddiy probeldan
    // ko'zga ko'rinmaydi, adashib almashtirib qo'yish oson
    private static final String NBSP = String.valueOf((char) 0x00A0);

    @Test
    void groupsThousandsWithNonBreakingSpace() {
        assertEquals("50" + NBSP + "000", PriceFormatter.formatPrice(BigDecimal.valueOf(50000)));
        assertEquals("1" + NBSP + "000" + NBSP + "000", PriceFormatter.formatPrice(BigDecimal.valueOf(1_000_000)));
    }

    @Test
    void smallAmountsHaveNoSeparator() {
        assertEquals("500", PriceFormatter.formatPrice(BigDecimal.valueOf(500)));
        assertEquals("0", PriceFormatter.formatPrice(BigDecimal.ZERO));
    }

    /** Tiyin ko'rsatilmaydi: non narxi butun so'mda. */
    @Test
    void fractionsAreRounded() {
        assertEquals("5" + NBSP + "001", PriceFormatter.formatPrice(new BigDecimal("5000.5")));
        assertEquals("5" + NBSP + "000", PriceFormatter.formatPrice(new BigDecimal("5000.4")));
    }

    @Test
    void nullIsNotAnError() {
        assertEquals("0", PriceFormatter.formatPrice(null));
    }
}