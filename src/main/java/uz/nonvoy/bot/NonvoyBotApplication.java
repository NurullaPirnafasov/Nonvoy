package uz.nonvoy.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import uz.nonvoy.bot.config.TimeZoneInitializer;

@SpringBootApplication
@EnableJpaAuditing
public class NonvoyBotApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(NonvoyBotApplication.class);
        // Bean emas, listener: vaqt mintaqasi kontekst yaratilishidan oldin o'rnatilishi kerak
        application.addListeners(new TimeZoneInitializer());
        application.run(args);
    }

}
