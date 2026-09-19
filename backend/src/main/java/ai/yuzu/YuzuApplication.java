package ai.yuzu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** v0.0.1 🍊 Spring Boot entry point of the Yuzu multi-agent coworker platform. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class YuzuApplication {

    /** v0.0.1 🍊 Boots the application. */
    public static void main(String[] args) {
        SpringApplication.run(YuzuApplication.class, args);
    }
}
