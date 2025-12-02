package com.group_2.service;

import com.group_2.Room;
import com.group_2.User;
import com.group_2.WG;
import com.group_2.repository.WGRepository;
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
        return wgRepository.save(wg);
    }

    @Transactional
    public WG addMitbewohner(Long wgId, User user) {
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

    @Transactional
    public WG updateWG(Long id, String name, User admin) {
        WG wg = wgRepository.findById(id).orElseThrow(() -> new RuntimeException("WG not found"));
        wg.name = name; // Accessing public field directly as per WG.java
        wg.setAdmin(admin);
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
