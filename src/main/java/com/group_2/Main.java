package com.group_2;

import com.group_2.service.DatabaseCleanupService;
import com.group_2.service.RoomService;
import com.group_2.service.UserService;
import com.group_2.service.WGService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    public CommandLineRunner demo(WGService wgService, UserService userService, RoomService roomService,
            DatabaseCleanupService cleanupService) {
        return (args) -> {
            // Cleanup database
            System.out.println("Cleaning up database...");
            cleanupService.cleanupDatabase();
            System.out.println("Database cleaned.");

            // Create Users
            System.out.println("Creating users...");
            User admin = userService.createUser("Alice");
            User bob = userService.createUser("Bob");
            System.out.println("Users created: " + admin.getName() + ", " + bob.getName());

            // Create Rooms
            System.out.println("Creating rooms...");
            Room room1 = roomService.createRoom("Living Room");
            Room room2 = roomService.createRoom("Kitchen");
            System.out.println("Rooms created: " + room1.getName() + ", " + room2.getName());

            // Create WG
            System.out.println("Creating WG...");
            List<Room> rooms = new ArrayList<>();
            rooms.add(room1);
            rooms.add(room2);
            WG wg = wgService.createWG("My WG", admin, rooms);
            System.out.println("WG created: " + wg.name + " with ID: " + wg.getId());

            // Add Mitbewohner
            System.out.println("Adding Mitbewohner...");
            wgService.addMitbewohner(wg.getId(), bob);
            System.out.println("Mitbewohner added.");

            // Verify WG content
            WG fetchedWG = wgService.getWG(wg.getId()).orElseThrow();
            System.out.println("WG Mitbewohner count: " + fetchedWG.getMitbewohner().size());

            // Update User
            System.out.println("Updating User...");
            userService.updateUser(bob.getId(), "Bob Updated");
            System.out.println("User updated: " + userService.getUser(bob.getId()).get().getName());

            // Update Room
            System.out.println("Updating Room...");
            roomService.updateRoom(room1.getId(), "Living Room Updated");
            System.out.println("Room updated: " + roomService.getRoom(room1.getId()).get().getName());

            // Remove Mitbewohner
            System.out.println("Removing Mitbewohner...");
            wgService.removeMitbewohner(wg.getId(), bob.getId());
            fetchedWG = wgService.getWG(wg.getId()).orElseThrow();
            System.out.println("WG Mitbewohner count after removal: " + fetchedWG.getMitbewohner().size());

            // Delete User
            System.out.println("Deleting User...");
            // Note: Deleting a user who is part of a WG might cause issues if relationships
            // are not handled correctly (Cascade).
            // But we removed Bob from WG, so it should be fine.
            userService.deleteUser(bob.getId());
            System.out.println("User deleted. Exists? " + userService.getUser(bob.getId()).isPresent());

            System.out.println("All operations completed successfully.");
            System.out.println("Access H2 Console at: http://localhost:8080/h2-console");
        };
    }
}