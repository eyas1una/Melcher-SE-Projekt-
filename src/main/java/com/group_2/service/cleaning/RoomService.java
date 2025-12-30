package com.group_2.service.cleaning;

import com.group_2.model.WG;
import com.group_2.model.cleaning.Room;
import com.group_2.repository.WGRepository;
import com.group_2.repository.cleaning.RoomRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final WGRepository wgRepository;
    private final CleaningScheduleService cleaningScheduleService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public RoomService(RoomRepository roomRepository, WGRepository wgRepository,
            CleaningScheduleService cleaningScheduleService) {
        this.roomRepository = roomRepository;
        this.wgRepository = wgRepository;
        this.cleaningScheduleService = cleaningScheduleService;
    }

    @Transactional
    public Room createRoom(String name) {
        Room room = new Room(name);
        return roomRepository.save(room);
    }

    public Optional<Room> getRoom(Long id) {
        return roomRepository.findById(id);
    }

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    @Transactional
    public Room updateRoom(Long id, String name) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found with id: " + id));
        room.setName(name);
        return roomRepository.save(room);
    }

    @Transactional
    public void deleteRoom(Long id) {
        roomRepository.deleteById(id);
    }

    /**
     * Delete a room and all its associated data in a single transaction.
     * This method handles:
     * 1. Deleting all cleaning-related data (tasks, templates, queues)
     * 2. Removing the room from the WG's collection
     * 3. Flushing changes to ensure the join table is updated
     * 4. Deleting the room itself
     * 
     * @param room The room to delete
     * @param wg   The WG that owns the room
     */
    @Transactional
    public void deleteRoom(Room room, WG wg) {
        // 1. Delete all cleaning-related data for this room
        cleaningScheduleService.deleteRoomData(room);

        // 2. Remove the room from WG's collection and save
        wg.removeRoom(room);
        wgRepository.save(wg);

        // 3. Flush to ensure the join table (WG_ROOMS) is updated before room delete
        entityManager.flush();

        // 4. Now safe to delete the room
        roomRepository.delete(room);
    }
}
