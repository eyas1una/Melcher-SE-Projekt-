package com.group_2;

import com.group_2.service.DatabaseCleanupService;
import com.group_2.service.RoomService;
import com.group_2.service.UserService;
import com.group_2.service.WGService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        // SpringApplication.run(Main.class, args);
        javafx.application.Application.launch(JavaFxApplication.class, args);
    }

    @Bean
    public CommandLineRunner demo(WGService wgService, UserService userService, RoomService roomService,
            DatabaseCleanupService cleanupService) {
        return (args) -> {

            System.out.println("All operations completed successfully.");
            System.out.println("Access H2 Console at: http://localhost:8080/h2-console");
        };
    }
}