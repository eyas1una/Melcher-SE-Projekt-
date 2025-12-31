package com.group_2.service.core;

import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.model.cleaning.Room;
import com.group_2.dto.core.CoreMapper;
import com.group_2.dto.core.UserSummaryDTO;
import com.group_2.repository.UserRepository;
import com.group_2.repository.WGRepository;
import com.group_2.repository.cleaning.RoomRepository;
import com.group_2.service.cleaning.CleaningScheduleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WGService {

    private final WGRepository wgRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final CleaningScheduleService cleaningScheduleService;
    private final CoreMapper coreMapper;

    @Autowired
    public WGService(WGRepository wgRepository, UserRepository userRepository, RoomRepository roomRepository,
            @Lazy CleaningScheduleService cleaningScheduleService, CoreMapper coreMapper) {
        this.wgRepository = wgRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.cleaningScheduleService = cleaningScheduleService;
        this.coreMapper = coreMapper;
    }

    @Transactional
    public WG createWG(String name, User admin, List<Room> rooms) {
        WG wg = new WG(name, admin, rooms);

        // Ensure unique invite code with retry logic
        int maxRetries = 10;
        while (wgRepository.existsByInviteCode(wg.getInviteCode())) {
            wg.regenerateInviteCode();
            maxRetries--;
            if (maxRetries <= 0) {
                throw new RuntimeException("Failed to generate unique invite code after multiple attempts");
            }
        }

        // Save WG first
        wg = wgRepository.save(wg);
        // Ensure admin has the WG set and save the user
        admin.setWg(wg);
        userRepository.save(admin);
        if (rooms != null && !rooms.isEmpty()) {
            for (Room room : rooms) {
                room.setWg(wg);
            }
            roomRepository.saveAll(rooms);
        }
        return wg;
    }

    /**
     * Create a WG using IDs to keep controllers off entities.
     */
    @Transactional
    public WG createWGWithRoomIds(String name, Long adminUserId, List<Long> roomIds) {
        if (adminUserId == null) {
            throw new RuntimeException("Admin user ID is required");
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        List<Room> rooms = roomIds == null ? List.of() : roomRepository.findAllById(roomIds);
        return createWG(name, admin, rooms);
    }

    @Transactional
    public WG addMitbewohner(Long wgId, User user) {
        // Check if user is already in a WG
        if (user.getWg() != null) {
            throw new RuntimeException("User is already a member of a WG.");
        }
        WG wg = wgRepository.findById(wgId).orElseThrow(() -> new RuntimeException("WG not found"));
        wg.addMitbewohner(user);
        WG savedWg = wgRepository.save(wg);
        // Reset cleaning schedule to include new member and clear all reassignments
        cleaningScheduleService.resetScheduleForMembershipChange(savedWg);
        return savedWg;
    }

    /**
     * Add a member using IDs to keep controllers off entities.
     */
    @Transactional
    public WG addMitbewohner(Long wgId, Long userId) {
        if (userId == null) {
            throw new RuntimeException("User ID is required");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return addMitbewohner(wgId, user);
    }

    public List<WG> getAllWGs() {
        return wgRepository.findAll();
    }

    public Optional<WG> getWG(Long id) {
        return wgRepository.findById(id);
    }

    /**
     * Get WG member summaries for UI consumption by WG ID.
     */
    public List<UserSummaryDTO> getMemberSummaries(Long wgId) {
        if (wgId == null) {
            return List.of();
        }
        return coreMapper.toUserSummaries(userRepository.findByWgId(wgId));
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

        if (userRepository.existsByIdAndWgId(user.getId(), wg.getId())) {
            throw new RuntimeException("User is already a member of this WG.");
        }

        wg.addMitbewohner(user);
        WG savedWg = wgRepository.save(wg);
        // Reset cleaning schedule to include new member and clear all reassignments
        cleaningScheduleService.resetScheduleForMembershipChange(savedWg);
        return savedWg;
    }

    /**
     * Add a member by invite code using IDs to keep controllers off entities.
     */
    @Transactional
    public WG addMitbewohnerByInviteCode(String inviteCode, Long userId) {
        if (userId == null) {
            throw new RuntimeException("User ID is required");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return addMitbewohnerByInviteCode(inviteCode, user);
    }

    @Transactional
    public WG updateWG(Long id, String name, Long adminUserId) {
        WG wg = wgRepository.findById(id).orElseThrow(() -> new RuntimeException("WG not found"));
        wg.setName(name);

        if (adminUserId != null) {
            User managedAdmin = userRepository.findById(adminUserId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            if (managedAdmin.getWg() == null || !wg.getId().equals(managedAdmin.getWg().getId())) {
                throw new RuntimeException("User is not a member of this WG");
            }
            if (!wg.setAdmin(managedAdmin)) {
                throw new RuntimeException("Failed to set admin for this WG");
            }
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
        User userToRemove = wg.getMitbewohner().stream().filter(u -> u.getId().equals(userId)).findFirst()
                .orElseThrow(() -> new RuntimeException("User not found in WG"));
        wg.removeMitbewohner(userToRemove);

        // Regenerate invite code to prevent removed user from rejoining with old code
        // Ensure unique invite code with retry logic
        int maxRetries = 10;
        do {
            wg.regenerateInviteCode();
            maxRetries--;
            if (maxRetries <= 0) {
                throw new RuntimeException("Failed to generate unique invite code after multiple attempts");
            }
        } while (wgRepository.existsByInviteCode(wg.getInviteCode()));

        WG savedWg = wgRepository.save(wg);

        // Reset cleaning schedule to remove departed member and clear all reassignments
        cleaningScheduleService.resetScheduleForMembershipChange(savedWg);
    }

    @Transactional
    public void addRoom(Long wgId, Room room) {
        WG wg = wgRepository.findById(wgId).orElseThrow(() -> new RuntimeException("WG not found"));
        wg.addRoom(room);
        wgRepository.save(wg);
    }

    /**
     * Add room by ID (for DTO-based controller usage).
     */
    @Transactional
    public void addRoomById(Long wgId, Long roomId) {
        WG wg = wgRepository.findById(wgId).orElseThrow(() -> new RuntimeException("WG not found"));
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Room not found"));
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
