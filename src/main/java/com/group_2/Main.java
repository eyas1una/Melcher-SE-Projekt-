package com.group_2;

import com.group_2.service.cleaning.RoomService;
import com.group_2.service.core.DatabaseCleanupService;
import com.group_2.service.core.UserService;
import com.group_2.service.core.WGService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = { "com.group_2", "com.model" })
@EnableScheduling
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // SpringApplication.run(Main.class, args);
        javafx.application.Application.launch(JavaFxApplication.class, args);
    }

    @Bean
    public CommandLineRunner demo(WGService wgService, UserService userService, RoomService roomService,
            DatabaseCleanupService cleanupService) {
        return (args) -> {

            log.info("All operations completed successfully.");
            log.info("Access H2 Console at: http://localhost:8080/h2-console");
        };
    }
}