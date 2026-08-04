package uz.nonvoy.bot.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public final class PriceFormatter {

    // Uzilmas bo'shliq (NBSP): Telegram tor ekranda "50 000" ni ikki qatorga bo'lib yubormasligi uchun
    private static final char GROUPING_SEPARATOR = (char) 0x00A0;
    private static final String PATTERN = "#,###";

    private PriceFormatter() {
    }

    public static String formatPrice(BigDecimal price) {
        if (price == null) {
            return "0";
        }
        // Tiyin ko'rsatilmaydi: non narxi butun so'mda
        BigDecimal rounded = price.setScale(0, RoundingMode.HALF_UP);

        // DecimalFormat thread-safe emas, shuning uchun har chaqiruvda yangisi yaratiladi.
        // Ajratgich locale'dan olinmaydi — server JVM locale'i qanday bo'lishidan qat'i
        // nazar natija bir xil bo'lsin
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
        symbols.setGroupingSeparator(GROUPING_SEPARATOR);

        return new DecimalFormat(PATTERN, symbols).format(rounded);
    }
}