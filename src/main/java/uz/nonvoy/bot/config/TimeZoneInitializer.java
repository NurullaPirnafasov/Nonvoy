package uz.nonvoy.bot.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Dastur vaqtini server sozlamasidan mustaqil qiladi (36-qaror). {@code createdAt} /
 * {@code updatedAt} {@code LocalDateTime.now()} bilan to'ldiriladi, u esa JVM'ning standart
 * mintaqasini oladi — chet el VPS'i odatda UTC'da, ya'ni vaqt 5 soat orqada yozilardi.
 * <p>
 * Kontekst yaratilishidan oldin ishlaydi: sozlamalar (yml, env, profil) o'qilgan, lekin
 * hali hech bir bean — Flyway ham, DB ulanishlari ham — ishga tushmagan.
 */
public class TimeZoneInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    static final String PROPERTY = "bot.time-zone";
    static final String DEFAULT_ZONE = "Asia/Tashkent";

    /**
     * Spring'ning logging tinglovchisi ham shu hodisada ishga tushadi va log vaqti formatini
     * o'sha paytdagi mintaqa bilan o'rnatadi. Undan oldin ishlamasak, bazada vaqt Toshkent
     * bo'yicha, loglarda esa UTC bo'lib, serverda log o'qiganda 5 soat chalg'itadi.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String zone = event.getEnvironment().getProperty(PROPERTY, DEFAULT_ZONE);
        // ZoneId.of noto'g'ri nomda xato beradi. TimeZone.getTimeZone esa jimgina GMT'ga
        // qaytardi — "Asia/Tashkemt" kabi xato yozuv hech qachon payqalmay qolardi
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)));
    }
}
