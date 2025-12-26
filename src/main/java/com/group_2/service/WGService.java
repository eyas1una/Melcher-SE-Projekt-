package com.group_2.service;

import com.group_2.repository.WGRepository;
import com.model.Room;
import com.model.User;
import com.model.WG;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WGService {

    private final WGRepository wgRepository;

    @Autowired
    public WGService(WGRepository wgRepository) {
        this.wgRepository = wgRepository;
    }

    @Transactional
    public WG createWG(String name, User admin, List<Room> rooms) {
        WG wg = new WG(name, admin, rooms);
        // Ensure admin has the WG set
        admin.setWg(wg);
        return wgRepository.save(wg);
    }

    @Transactional
    public WG addMitbewohner(Long wgId, User user) {
        // Check if user is already in a WG
        if (user.getWg() != null) {
            throw new RuntimeException("User is already a member of a WG.");
        }
        WG wg = wgRepository.findById(wgId).orElseThrow(() -> new RuntimeException("WG not found"));
        wg.addMitbewohner(user);
        return wgRepository.save(wg);
    }

    public List<WG> getAllWGs() {
        return wgRepository.findAll();
    }

    public Optional<WG> getWG(Long id) {
        return wgRepository.findById(id);
    }

    public Optional<WG> getWGByInviteCode(String inviteCode) {
        return wgRepository.findByInviteCode(inviteCode.toUpperCase());
    }

    @Transactional
    public WG addMitbewohnerByInviteCode(String inviteCode, User user) {
        // Check if user is already in a WG
        if (user.getWg() != null) {
            throw new RuntimeException("User is already a member of a WG. Leave current WG first.");
        }

        WG wg = wgRepository.findByInviteCode(inviteCode.toUpperCase())
                .orElseThrow(() -> new RuntimeException("WG not found with invite code: " + inviteCode));

        // Check if user is already in this WG (by ID comparison)
        boolean alreadyMember = wg.getMitbewohner().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
        if (alreadyMember) {
            throw new RuntimeException("User is already a member of this WG.");
        }

        wg.addMitbewohner(user);
        return wgRepository.save(wg);
    }

    @Transactional
    public WG updateWG(Long id, String name, User admin) {
        WG wg = wgRepository.findById(id).orElseThrow(() -> new RuntimeException("WG not found"));
        wg.name = name; // Accessing public field directly as per WG.java

        if (admin != null) {
            // Find the managed user entity from the WG's member list by ID
            // This avoids JPA merge issues with detached entities
            User managedAdmin = wg.getMitbewohner().stream()
                    .filter(m -> m.getId().equals(admin.getId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("User is not a member of this WG"));
            wg.admin = managedAdmin;
        }
        return wgRepository.save(wg);
    }

    @Transactional
    public void deleteWG(Long id) {
        wgRepository.deleteById(id);
    }

    @Transactional
    public void removeMitbewohner(Long wgId, Long userId) {
        WG wg = wgRepository.findById(wgId).orElseThrow(() -> new RuntimeException("WG not found"));
        // We need to find the user in the list. Since we only have ID, we iterate or
        // fetch user.
        // Assuming we pass the user object or find it.
        // Let's find the user in the list.
        User userToRemove = wg.getMitbewohner().stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found in WG"));
        wg.removeMitbewohner(userToRemove);

        // Regenerate invite code to prevent removed user from rejoining with old code
        wg.regenerateInviteCode();

        wgRepository.save(wg);
    }

    @Transactional
    public void addRoom(Long wgId, Room room) {
        WG wg = wgRepository.findById(wgId).orElseThrow(() -> new RuntimeException("WG not found"));
        wg.addRoom(room);
        wgRepository.save(wg);
    }

    @Transactional
    public void removeRoom(Long wgId, Room room) {
        WG wg = wgRepository.findById(wgId).orElseThrow(() -> new RuntimeException("WG not found"));
        wg.removeRoom(room);
        wgRepository.save(wg);
    }
}
