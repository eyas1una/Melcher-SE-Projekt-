package com.group_2;

import java.util.ArrayList;
import java.util.List;

public class WG {

    private String id;
    public String name;
    public List<Room> rooms;
    public List<User> mitbewohner;
    public User admin;

    public WG(String name, User admin, List<Room> rooms) {
        this.name = name;
        this.admin = admin;
        this.rooms = rooms;
        this.mitbewohner = new ArrayList<>();
        addMitbewohner(admin); // Add admin as first user

        //generate id
        //save entity in database
    }

    public boolean addRoom(Room room) {
        if (rooms.contains(room)) {

            //throw error
            return false;
        }
        return rooms.add(room);
    }

    public boolean removeRoom(Room room) {
        if (!rooms.contains(room)) {

            //throw error
            return false;
        }
        return rooms.remove(room);
    }

    public boolean setAdmin(User user) {
        if (!mitbewohner.contains(user)) {

            //throw error
            return false;
        }
        this.admin = user;
        return true;
    }

    public boolean addMitbewohner(User user) {
        if (mitbewohner.contains(user)) {

            //throw error
            return false;
        }
        return mitbewohner.add(user);
    }

    public boolean removeMitbewohner(User user) {
        if (!mitbewohner.contains(user)) {

            //throw error
            return false;
        }
        return mitbewohner.remove(user);
    }

    public List<User> getMitbewohner() {
        return new ArrayList<>(mitbewohner);
    }
}