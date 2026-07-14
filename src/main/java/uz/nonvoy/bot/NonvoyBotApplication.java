package uz.nonvoy.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class NonvoyBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(NonvoyBotApplication.class, args);
    }

}
