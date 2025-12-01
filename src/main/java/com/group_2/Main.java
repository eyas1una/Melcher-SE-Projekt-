package com.group_2;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("wg-pu");
        EntityManager em = emf.createEntityManager();

        try {
            em.getTransaction().begin();

            User admin = new User("Alice");
            Room room1 = new Room("Living Room");
            Room room2 = new Room("Kitchen");
            List<Room> rooms = new ArrayList<>();
            rooms.add(room1);
            rooms.add(room2);

            WG wg = new WG("My WG", admin, rooms);

            // Add another user
            User bob = new User("Bob");
            wg.addMitbewohner(bob);

            em.persist(wg);

            em.getTransaction().commit();

            System.out.println("WG persisted successfully!");

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            e.printStackTrace();
        } finally {
            em.close();
            emf.close();
        }
    }
}