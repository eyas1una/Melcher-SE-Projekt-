package com.group_2.model.cleaning;

import com.group_2.model.User;
import com.group_2.model.WG;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entity representing the assignment queue for a room.
 * This enables round-robin task distribution where each member takes turns
 * cleaning each room. The queue rotates after each week.
 */
@Entity
@Table(name = "room_assignment_queue")
public class RoomAssignmentQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "wg_id", nullable = false)
    private WG wg;

    /**
     * Ordered list of user IDs representing the assignment queue.
     * The first user in the list is assigned to clean next.
     * Stored as comma-separated IDs for simplicity.
     */
    @Column(nullable = false, length = 1000)
    private String memberQueueOrder;

    public RoomAssignmentQueue() {
    }

    public RoomAssignmentQueue(Room room, WG wg, List<User> members, int offset) {
        this.room = room;
        this.wg = wg;
        initializeQueue(members, offset);
    }

    /**
     * Initialize the queue with members, starting at the given offset.
     * For example, with members [A, B, C, D] and offset 1, the queue becomes [B, C,
     * D, A].
     */
    public void initializeQueue(List<User> members, int offset) {
        if (members == null || members.isEmpty()) {
            this.memberQueueOrder = "";
            return;
        }

        List<Long> ids = new ArrayList<>();
        int size = members.size();
        for (int i = 0; i < size; i++) {
            int index = (i + offset) % size;
            ids.add(members.get(index).getId());
        }
        this.memberQueueOrder = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    /**
     * Get the list of user IDs in queue order.
     */
    public List<Long> getMemberIds() {
        if (memberQueueOrder == null || memberQueueOrder.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(memberQueueOrder.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    /**
     * Get the ID of the first user in the queue (next assignee).
     */
    public Long getNextAssigneeId() {
        List<Long> ids = getMemberIds();
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * Rotate the queue: move the first member to the end.
     */
    public void rotate() {
        List<Long> ids = getMemberIds();
        if (ids.size() <= 1) {
            return;
        }
        Long first = ids.remove(0);
        ids.add(first);
        this.memberQueueOrder = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    /**
     * Add a new member to the end of the queue.
     */
    public void addMember(User user) {
        List<Long> ids = getMemberIds();
        if (!ids.contains(user.getId())) {
            ids.add(user.getId());
            this.memberQueueOrder = ids.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }
    }

    /**
     * Remove a member from the queue.
     */
    public void removeMember(User user) {
        List<Long> ids = getMemberIds();
        ids.remove(user.getId());
        this.memberQueueOrder = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    /**
     * Swap positions of two users in the queue.
     * Used when reassigning tasks to maintain fairness.
     */
    public void swapPositions(Long userId1, Long userId2) {
        List<Long> ids = getMemberIds();
        int pos1 = ids.indexOf(userId1);
        int pos2 = ids.indexOf(userId2);
        if (pos1 >= 0 && pos2 >= 0 && pos1 != pos2) {
            ids.set(pos1, userId2);
            ids.set(pos2, userId1);
            this.memberQueueOrder = ids.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }
    }

    /**
     * Swap a position at the given index with the next occurrence of the target
     * user.
     * This is used for reassignment: the current task position is swapped with the
     * next upcoming occurrence of the target user, preserving total task counts.
     * 
     * Example: Queue [a, b, c, a, b, c, a, b, c], swapWithNextOccurrence(0, c)
     * → Finds 'a' at index 0, finds next 'c' at index 2
     * → Result: [c, b, a, a, b, c, a, b, c]
     * 
     * @param currentPosition The index of the position to swap FROM
     * @param targetUserId    The user ID to swap WITH (next occurrence after
     *                        currentPosition)
     * @return true if swap was successful, false if no valid target found
     */
    public boolean swapWithNextOccurrence(int currentPosition, Long targetUserId) {
        List<Long> ids = getMemberIds();

        if (currentPosition < 0 || currentPosition >= ids.size()) {
            return false;
        }

        // Find the next occurrence of targetUserId AFTER currentPosition
        int nextTargetPosition = -1;
        for (int i = currentPosition + 1; i < ids.size(); i++) {
            if (ids.get(i).equals(targetUserId)) {
                nextTargetPosition = i;
                break;
            }
        }

        if (nextTargetPosition == -1) {
            return false; // No upcoming occurrence of target user found
        }

        // Swap the positions
        Long temp = ids.get(currentPosition);
        ids.set(currentPosition, ids.get(nextTargetPosition));
        ids.set(nextTargetPosition, temp);

        this.memberQueueOrder = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        return true;
    }

    /**
     * Find the position of a user at or after the given starting index.
     * 
     * @param userId     The user ID to find
     * @param startIndex The index to start searching from (inclusive)
     * @return The index of the user, or -1 if not found
     */
    public int findPositionFrom(Long userId, int startIndex) {
        List<Long> ids = getMemberIds();
        for (int i = startIndex; i < ids.size(); i++) {
            if (ids.get(i).equals(userId)) {
                return i;
            }
        }
        return -1;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public WG getWg() {
        return wg;
    }

    public void setWg(WG wg) {
        this.wg = wg;
    }

    public String getMemberQueueOrder() {
        return memberQueueOrder;
    }

    public void setMemberQueueOrder(String memberQueueOrder) {
        this.memberQueueOrder = memberQueueOrder;
    }
}
