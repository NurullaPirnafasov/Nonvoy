package uz.nonvoy.bot.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.mock.env.MockEnvironment;

import java.time.DateTimeException;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class TimeZoneInitializerTest {

    private TimeZone original;

    @BeforeEach
    void rememberDefault() {
        original = TimeZone.getDefault();
        // Chet el VPS'ini taqlid qilamiz
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restoreDefault() {
        TimeZone.setDefault(original);
    }

    /** Server UTC'da bo'lsa ham, sozlanmagan holatda vaqt Toshkent bo'yicha. */
    @Test
    void defaultsToTashkent() {
        fire(new MockEnvironment());

        assertEquals("Asia/Tashkent", TimeZone.getDefault().getID());
    }

    @Test
    void usesConfiguredZone() {
        fire(new MockEnvironment().withProperty(TimeZoneInitializer.PROPERTY, "Asia/Samarkand"));

        assertEquals("Asia/Samarkand", TimeZone.getDefault().getID());
    }

    /** Xato yozilgan nom jimgina GMT bo'lib qolmasin — dastur ishga tushmasin. */
    @Test
    void misspelledZoneFailsStartup() {
        MockEnvironment environment = new MockEnvironment().withProperty(TimeZoneInitializer.PROPERTY, "Asia/Tashkemt");

        assertThrows(DateTimeException.class, () -> fire(environment));
        assertEquals("UTC", TimeZone.getDefault().getID());
    }

    /** Logging tinglovchisidan oldin ishlashi kerak — aks holda log vaqtlari UTC'da qoladi. */
    @Test
    void runsBeforeLoggingIsConfigured() {
        assertTrue(new TimeZoneInitializer().getOrder() < new LoggingApplicationListener().getOrder());
    }

    private void fire(MockEnvironment environment) {
        new TimeZoneInitializer().onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(), new SpringApplication(), new String[0], environment));
    }
}
