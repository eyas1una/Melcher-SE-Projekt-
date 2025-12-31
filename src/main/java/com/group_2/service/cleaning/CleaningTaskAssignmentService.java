package com.group_2.service.cleaning;

import com.group_2.dto.cleaning.CleaningMapper;
import com.group_2.dto.cleaning.CleaningTaskDTO;
import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.model.cleaning.CleaningTask;
import com.group_2.model.cleaning.Room;
import com.group_2.model.cleaning.RoomAssignmentQueue;
import com.group_2.repository.UserRepository;
import com.group_2.repository.cleaning.CleaningTaskRepository;
import com.group_2.repository.cleaning.RoomAssignmentQueueRepository;
import com.group_2.repository.cleaning.RoomRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Service for task assignment and reassignment operations.
 * Handles assigning tasks to users and managing membership changes.
 */
@Service
public class CleaningTaskAssignmentService {

    private final CleaningTaskRepository cleaningTaskRepository;
    private final RoomAssignmentQueueRepository queueRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final CleaningMapper cleaningMapper;
    private final QueueManagementService queueManagementService;

    @Autowired
    public CleaningTaskAssignmentService(CleaningTaskRepository cleaningTaskRepository,
            RoomAssignmentQueueRepository queueRepository, RoomRepository roomRepository, UserRepository userRepository,
            CleaningMapper cleaningMapper, QueueManagementService queueManagementService) {
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.queueRepository = queueRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.cleaningMapper = cleaningMapper;
        this.queueManagementService = queueManagementService;
    }

    /**
     * Get the current week's Monday.
     */
    private LocalDate getCurrentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Assign a cleaning task for a specific room to a user.
     *
     * @param room     the room
     * @param assignee the user to assign
     * @param wg       the WG
     * @return the assigned task
     */
    @Transactional
    public CleaningTask assignTask(Room room, User assignee, WG wg) {
        LocalDate weekStart = getCurrentWeekStart();

        List<CleaningTask> existingTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
        for (CleaningTask task : existingTasks) {
            if (task.getRoom().getId().equals(room.getId())) {
                task.setAssignee(assignee);
                return cleaningTaskRepository.save(task);
            }
        }

        CleaningTask task = new CleaningTask(room, assignee, wg, weekStart);
        return cleaningTaskRepository.save(task);
    }

    /**
     * Assign a cleaning task and return as DTO.
     */
    @Transactional
    public CleaningTaskDTO assignTaskDTO(Room room, User assignee, WG wg) {
        return cleaningMapper.toDTO(assignTask(room, assignee, wg));
    }

    /**
     * Assign a cleaning task by IDs.
     */
    @Transactional
    public CleaningTaskDTO assignTaskByIds(Long roomId, Long assigneeId, WG wg) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("Room not found"));
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new IllegalArgumentException("Assignee not found"));
        return assignTaskDTO(room, assignee, wg);
    }

    /**
     * Assign a cleaning task for a specific room to a user with a custom due date.
     * Always creates a new task (allows multiple tasks per room per day).
     */
    @Transactional
    public CleaningTaskDTO assignTaskByIdsWithDate(Long roomId, Long assigneeId, WG wg, LocalDate dueDate) {
        if (dueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot assign a task to a date in the past.");
        }
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("Room not found"));
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new IllegalArgumentException("Assignee not found"));

        // Calculate the week start from the due date
        LocalDate weekStart = dueDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // Always create a new task (allows multiple tasks per room per day)
        CleaningTask task = new CleaningTask(room, assignee, wg, weekStart, dueDate);
        task.setManualOverride(true);
        return cleaningMapper.toDTO(cleaningTaskRepository.save(task));
    }

    /**
     * Reassign a task to a different user by swapping with their next scheduled
     * task. This preserves fairness: no tasks are created or removed, each person
     * keeps the same total number of tasks.
     *
     * @param task        The task to reassign (current assignment)
     * @param newAssignee The user who should take this task
     * @return The updated task (now assigned to newAssignee)
     */
    @Transactional
    public CleaningTask reassignTask(CleaningTask task, User newAssignee) {
        User originalAssignee = task.getAssignee();

        // If same user, nothing to do
        if (originalAssignee.getId().equals(newAssignee.getId())) {
            return task;
        }

        // Find all tasks for this room, ordered by week
        List<CleaningTask> roomTasks = cleaningTaskRepository.findByWgAndRoom(task.getWg(), task.getRoom());

        // Sort by week start date
        roomTasks.sort((t1, t2) -> t1.getWeekStartDate().compareTo(t2.getWeekStartDate()));

        // Find the next task assigned to newAssignee AFTER the current task's week
        CleaningTask swapTarget = null;
        for (CleaningTask candidate : roomTasks) {
            if (candidate.getAssignee().getId().equals(newAssignee.getId())
                    && candidate.getWeekStartDate().isAfter(task.getWeekStartDate())) {
                swapTarget = candidate;
                break;
            }
        }

        if (swapTarget != null) {
            // Swap the assignees between the two tasks
            swapTarget.setAssignee(originalAssignee);
            swapTarget.setManualOverride(true);
            cleaningTaskRepository.save(swapTarget);
        }

        // Update the current task assignment
        task.setAssignee(newAssignee);
        task.setManualOverride(true);
        return cleaningTaskRepository.save(task);
    }

    /**
     * Reassign a task by ID.
     */
    @Transactional
    public CleaningTaskDTO reassignTask(Long taskId, Long newAssigneeId) {
        CleaningTask task = cleaningTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        User newAssignee = userRepository.findById(newAssigneeId)
                .orElseThrow(() -> new IllegalArgumentException("Assignee not found"));
        CleaningTask updated = reassignTask(task, newAssignee);
        return cleaningMapper.toDTO(updated);
    }

    /**
     * Reset the cleaning schedule when membership changes (join or leave). This
     * deletes all current and future tasks and regenerates them fresh.
     *
     * @param wg                     the WG
     * @param generateFromTemplateFn callback to regenerate tasks from template
     */
    @Transactional
    public void resetScheduleForMembershipChange(WG wg, boolean hasTemplate,
            java.util.function.BiConsumer<WG, LocalDate> generateFromTemplateFn) {
        LocalDate currentWeekStart = getCurrentWeekStart();
        List<User> currentMembers = wg.getMitbewohner();

        // Delete all tasks from current week onwards (preserve history)
        List<CleaningTask> allTasks = cleaningTaskRepository.findByWg(wg);
        for (CleaningTask task : allTasks) {
            if (!task.getWeekStartDate().isBefore(currentWeekStart)) {
                cleaningTaskRepository.delete(task);
            }
        }

        // Reset all queues with current members and fresh starting positions
        List<RoomAssignmentQueue> queues = queueRepository.findByWg(wg);
        int offset = 0;
        for (RoomAssignmentQueue queue : queues) {
            queue.initializeQueue(currentMembers, offset);
            queueRepository.save(queue);
            offset++;
        }

        // Regenerate tasks for current week from templates
        if (hasTemplate && generateFromTemplateFn != null) {
            generateFromTemplateFn.accept(wg, currentWeekStart);
        }
    }

    /**
     * Reassign all incomplete tasks from a departing user to other members.
     *
     * @param wg             the WG
     * @param departedUserId ID of the user leaving
     */
    @Transactional
    public void reassignTasksFromDepartedMember(WG wg, Long departedUserId) {
        LocalDate currentWeekStart = getCurrentWeekStart();
        List<User> currentMembers = wg.getMitbewohner();

        // Find all incomplete tasks assigned to the departed user
        List<CleaningTask> tasks = cleaningTaskRepository.findByWg(wg);
        for (CleaningTask task : tasks) {
            // Only reassign incomplete tasks from current week onwards
            if (!task.isCompleted() && task.getAssignee().getId().equals(departedUserId)
                    && !task.getWeekStartDate().isBefore(currentWeekStart)) {

                // Get the queue for this room and find the next available assignee
                List<RoomAssignmentQueue> queues = queueRepository.findByWgAndRoom(wg, task.getRoom());
                if (!queues.isEmpty() && !currentMembers.isEmpty()) {
                    RoomAssignmentQueue queue = queues.get(0);
                    User newAssignee = queueManagementService.getNextAssigneeFromQueue(queue, currentMembers);
                    if (newAssignee != null) {
                        task.setAssignee(newAssignee);
                        cleaningTaskRepository.save(task);
                    }
                }
            }
        }
    }

    /**
     * Get the next assignee for a specific room in a WG.
     *
     * @param wg   the WG
     * @param room the room
     * @return the next user to be assigned
     */
    @Transactional
    public User getNextAssigneeForRoom(WG wg, Room room) {
        List<User> members = wg.getMitbewohner();
        if (members.isEmpty()) {
            return null;
        }

        List<RoomAssignmentQueue> queues = queueRepository.findByWgAndRoomForUpdate(wg, room);
        if (queues.isEmpty()) {
            return members.get(0);
        }

        Long nextId = queues.get(0).getNextAssigneeId();
        if (nextId == null) {
            return null;
        }

        return userRepository.findById(nextId).orElse(null);
    }
}
