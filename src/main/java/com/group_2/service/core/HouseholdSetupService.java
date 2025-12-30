package com.group_2.service.core;

import com.group_2.dto.cleaning.CleaningMapper;
import com.group_2.dto.cleaning.RoomDTO;
import com.group_2.model.WG;
import com.group_2.model.cleaning.Room;
import com.group_2.service.cleaning.RoomService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Core boundary service for household setup operations. Provides a facade over
 * domain-specific services (e.g., cleaning) so that core UI controllers don't
 * depend on feature domain services directly.
 */
@Service
public class HouseholdSetupService {

    private final RoomService roomService;
    private final CleaningMapper cleaningMapper;

    @Autowired
    public HouseholdSetupService(RoomService roomService, CleaningMapper cleaningMapper) {
        this.roomService = roomService;
        this.cleaningMapper = cleaningMapper;
    }

    // ========== Room Management (delegating to cleaning domain) ==========

    /**
     * Create a new room.
     * 
     * @param name the room name
     * @return the created Room
     */
    @Transactional
    public Room createRoom(String name) {
        return roomService.createRoom(name);
    }

    /**
     * Create a new room and return as DTO.
     */
    @Transactional
    public RoomDTO createRoomDTO(String name) {
        return cleaningMapper.toRoomDTO(createRoom(name));
    }

    /**
     * Get a room by ID.
     * 
     * @param id the room ID
     * @return Optional containing the room if found
     */
    public Optional<Room> getRoom(Long id) {
        return roomService.getRoom(id);
    }

    /**
     * Get all rooms.
     * 
     * @return list of all rooms
     */
    public List<Room> getAllRooms() {
        return roomService.getAllRooms();
    }

    /**
     * Get all rooms as DTOs.
     */
    public List<RoomDTO> getAllRoomsDTO() {
        return cleaningMapper.toRoomDTOList(getAllRooms());
    }

    /**
     * Get rooms for a WG as DTOs.
     */
    public List<RoomDTO> getRoomsForWgDTO(WG wg) {
        if (wg == null || wg.rooms == null) {
            return List.of();
        }
        return cleaningMapper.toRoomDTOList(wg.rooms);
    }

    /**
     * Update a room's name.
     * 
     * @param id   the room ID
     * @param name the new name
     * @return the updated Room
     */
    @Transactional
    public Room updateRoom(Long id, String name) {
        return roomService.updateRoom(id, name);
    }

    /**
     * Update a room's name and return as DTO.
     */
    @Transactional
    public RoomDTO updateRoomDTO(Long id, String name) {
        return cleaningMapper.toRoomDTO(updateRoom(id, name));
    }

    /**
     * Delete a room by ID (simple deletion).
     * 
     * @param id the room ID
     */
    @Transactional
    public void deleteRoom(Long id) {
        roomService.deleteRoom(id);
    }

    /**
     * Delete a room with full cleanup of associated data.
     * 
     * @param room the room to delete
     * @param wg   the WG owning the room
     */
    @Transactional
    public void deleteRoom(Room room, WG wg) {
        roomService.deleteRoom(room, wg);
    }

    /**
     * Delete a room by ID with full cleanup.
     */
    @Transactional
    public void deleteRoomById(Long roomId, WG wg) {
        getRoom(roomId).ifPresent(room -> deleteRoom(room, wg));
    }
}
