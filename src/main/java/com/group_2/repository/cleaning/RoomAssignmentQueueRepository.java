package com.group_2.repository.cleaning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.group_2.model.WG;
import com.group_2.model.cleaning.Room;
import com.group_2.model.cleaning.RoomAssignmentQueue;

import java.util.List;

/**
 * Repository for managing room assignment queues.
 */
@Repository
public interface RoomAssignmentQueueRepository extends JpaRepository<RoomAssignmentQueue, Long> {

    /**
     * Find the assignment queue for a specific room in a WG.
     */
    List<RoomAssignmentQueue> findByWgAndRoom(WG wg, Room room);

    /**
     * Find all assignment queues for a WG.
     */
    List<RoomAssignmentQueue> findByWg(WG wg);

    /**
     * Delete all assignment queues for a WG.
     */
    void deleteByWg(WG wg);

    /**
     * Delete assignment queue for a specific room.
     */
    void deleteByRoom(Room room);

    /**
     * Count queues for a WG (used to determine offset for new rooms).
     */
    long countByWg(WG wg);
}
