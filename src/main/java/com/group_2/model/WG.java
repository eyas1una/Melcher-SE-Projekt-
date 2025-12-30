package com.group_2.model;

import com.group_2.model.cleaning.Room;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class WG {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 8)
    private String inviteCode;

    public Long getId() {
        return id;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public String name;

    @OneToMany(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    public List<Room> rooms;

    @OneToMany(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    public List<User> mitbewohner;

    @ManyToOne(cascade = CascadeType.MERGE)
    public User admin;

    public WG() {
    } // Required by JPA

    public WG(String name, User admin, List<Room> rooms) {
        this.name = name;
        this.admin = admin;
        this.rooms = rooms;
        this.mitbewohner = new ArrayList<>();
        this.inviteCode = generateInviteCode();
        addMitbewohner(admin);
    }

    private static String generateInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Removed confusing chars: I, O, 0, 1
        StringBuilder code = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    /**
     * Regenerate the invite code. Call this after removing a member
     * to prevent removed users from rejoining with the old code.
     */
    public void regenerateInviteCode() {
        this.inviteCode = generateInviteCode();
    }

    public boolean addRoom(Room room) {
        if (rooms.contains(room)) {
            return false;
        }
        room.setWg(this);
        return rooms.add(room);
    }

    public boolean removeRoom(Room room) {
        if (!rooms.contains(room)) {
            return false;
        }
        room.setWg(null);
        return rooms.remove(room);
    }

    public boolean setAdmin(User user) {
        // Check if user is a member by comparing IDs (more reliable with JPA)
        boolean isMember = mitbewohner.stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
        if (!isMember) {
            return false;
        }
        this.admin = user;
        return true;
    }

    public boolean addMitbewohner(User user) {
        if (mitbewohner.contains(user)) {
            return false;
        }
        user.setWg(this);
        return mitbewohner.add(user);
    }

    public boolean removeMitbewohner(User user) {
        if (!mitbewohner.contains(user)) {
            return false;
        }
        user.setWg(null);
        return mitbewohner.remove(user);
    }

    public List<User> getMitbewohner() {
        return new ArrayList<>(mitbewohner);
    }
}