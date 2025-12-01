package com.group_2;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class WG {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Changed to Long for consistency with other entities

    public String name;

    @OneToMany(cascade = CascadeType.ALL)
    public List<Room> rooms;

    @OneToMany(cascade = CascadeType.ALL)
    public List<User> mitbewohner;

    @ManyToOne(cascade = CascadeType.ALL)
    public User admin;

    public WG() {
    } // Required by JPA

    public WG(String name, User admin, List<Room> rooms) {
        this.name = name;
        this.admin = admin;
        this.rooms = rooms;
        this.mitbewohner = new ArrayList<>();
        addMitbewohner(admin); // Add admin as first user
    }

    public boolean addRoom(Room room) {
        if (rooms.contains(room)) {

            // throw error
            return false;
        }
        return rooms.add(room);
    }

    public boolean removeRoom(Room room) {
        if (!rooms.contains(room)) {

            // throw error
            return false;
        }
        return rooms.remove(room);
    }

    public boolean setAdmin(User user) {
        if (!mitbewohner.contains(user)) {

            // throw error
            return false;
        }
        this.admin = user;
        return true;
    }

    public boolean addMitbewohner(User user) {
        if (mitbewohner.contains(user)) {

            // throw error
            return false;
        }
        return mitbewohner.add(user);
    }

    public boolean removeMitbewohner(User user) {
        if (!mitbewohner.contains(user)) {

            // throw error
            return false;
        }
        return mitbewohner.remove(user);
    }

    public List<User> getMitbewohner() {
        return new ArrayList<>(mitbewohner);
    }
}