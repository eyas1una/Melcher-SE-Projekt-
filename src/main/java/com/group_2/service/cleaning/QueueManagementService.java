package com.group_2.service.cleaning;

import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.model.cleaning.Room;
import com.group_2.model.cleaning.RoomAssignmentQueue;
import com.group_2.repository.UserRepository;
import com.group_2.repository.cleaning.RoomAssignmentQueueRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing round-robin room assignment queues.
 * Handles queue creation, synchronization with WG members, and next assignee
 * selection.
 */
@Service
public class QueueManagementService {

    private final RoomAssignmentQueueRepository queueRepository;
    private final UserRepository userRepository;

    @Autowired
    public QueueManagementService(RoomAssignmentQueueRepository queueRepository, UserRepository userRepository) {
        this.queueRepository = queueRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get the next assignee from a queue, validating the user still exists.
     *
     * @param queue          the room assignment queue
     * @param currentMembers current WG members
     * @return the next user to be assigned, or null if no valid assignee
     */
    public User getNextAssigneeFromQueue(RoomAssignmentQueue queue, List<User> currentMembers) {
        Long nextId = queue.getNextAssigneeId();
        if (nextId == null) {
            return currentMembers.isEmpty() ? null : currentMembers.get(0);
        }

        // Find the user in current members
        for (User member : currentMembers) {
            if (member.getId().equals(nextId)) {
                return member;
            }
        }

        // If user not found (left WG), sync queue and try again
        syncQueueWithMembers(queue, currentMembers);
        queueRepository.save(queue);

        nextId = queue.getNextAssigneeId();
        if (nextId == null) {
            return null;
        }

        return userRepository.findById(nextId).orElse(null);
    }

    /**
     * Get existing queue or create a new one with the correct offset.
     *
     * @param wg      the WG
     * @param room    the room
     * @param members current WG members
     * @return existing or newly created queue
     */
    @Transactional
    public RoomAssignmentQueue getOrCreateQueueForRoom(WG wg, Room room, List<User> members) {
        // Use pessimistic lock to prevent concurrent queue rotation issues
        List<RoomAssignmentQueue> queues = queueRepository.findByWgAndRoomForUpdate(wg, room);
        if (!queues.isEmpty()) {
            return queues.get(0);
        }

        // Create new queue with offset based on existing queue count
        int offset = (int) queueRepository.countByWg(wg);
        RoomAssignmentQueue newQueue = new RoomAssignmentQueue(room, wg, members, offset);
        return queueRepository.save(newQueue);
    }

    /**
     * Sync a queue with current WG members (add new members, remove departed ones).
     * New members are added at the end of the queue, meaning they will be assigned
     * after all current members have had their turn (end of current cycle).
     *
     * @param queue          the queue to sync
     * @param currentMembers the current WG members
     */
    public void syncQueueWithMembers(RoomAssignmentQueue queue, List<User> currentMembers) {
        List<Long> queueIds = queue.getMemberIds();
        List<Long> currentIds = new ArrayList<>();
        for (User m : currentMembers) {
            currentIds.add(m.getId());
        }

        // Remove departed members
        queueIds.removeIf(id -> !currentIds.contains(id));

        // Add new members at the end of the queue (end of current cycle)
        for (Long id : currentIds) {
            if (!queueIds.contains(id)) {
                queueIds.add(id);
            }
        }

        // Update the queue
        queue.setMemberQueueOrder(queueIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    /**
     * Sync all queues for a WG with current members. Call this when members join or
     * leave the WG.
     *
     * @param wg the WG whose queues should be synced
     */
    @Transactional
    public void syncAllQueuesWithMembers(WG wg) {
        List<User> currentMembers = wg.getMitbewohner();
        List<RoomAssignmentQueue> queues = queueRepository.findByWg(wg);

        for (RoomAssignmentQueue queue : queues) {
            syncQueueWithMembers(queue, currentMembers);
            queueRepository.save(queue);
        }
    }

    /**
     * Get the next assignee for a specific room in a WG.
     *
     * @param wg   the WG
     * @param room the room
     * @return the next user to be assigned, or null if unavailable
     */
    @Transactional
    public User getNextAssigneeForRoom(WG wg, Room room) {
        List<User> members = wg.getMitbewohner();
        if (members.isEmpty()) {
            return null;
        }

        RoomAssignmentQueue queue = getOrCreateQueueForRoom(wg, room, members);
        return getNextAssigneeFromQueue(queue, members);
    }

    /**
     * Advance the queue to the next person in rotation.
     *
     * @param queue the queue to advance
     */
    @Transactional
    public void advanceQueue(RoomAssignmentQueue queue) {
        queue.rotate();
        queueRepository.save(queue);
    }

    /**
     * Delete all queues for a room.
     *
     * @param room the room whose queues should be deleted
     */
    @Transactional
    public void deleteQueuesForRoom(Room room) {
        queueRepository.deleteByRoom(room);
    }

    /**
     * Delete all queues for a WG.
     *
     * @param wg the WG whose queues should be deleted
     */
    @Transactional
    public void deleteQueuesForWg(WG wg) {
        queueRepository.deleteByWg(wg);
    }
}
