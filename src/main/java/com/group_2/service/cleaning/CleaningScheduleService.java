package com.group_2.service.cleaning;

import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.model.cleaning.CleaningTask;
import com.group_2.model.cleaning.CleaningTaskTemplate;
import com.group_2.model.cleaning.RecurrenceInterval;
import com.group_2.model.cleaning.Room;
import com.group_2.model.cleaning.RoomAssignmentQueue;
import com.group_2.dto.cleaning.CleaningMapper;
import com.group_2.dto.cleaning.CleaningTaskDTO;
import com.group_2.dto.cleaning.CleaningTaskTemplateDTO;
import com.group_2.repository.UserRepository;
import com.group_2.repository.cleaning.CleaningTaskRepository;
import com.group_2.repository.cleaning.CleaningTaskTemplateRepository;
import com.group_2.repository.cleaning.RoomAssignmentQueueRepository;
import com.group_2.repository.cleaning.RoomRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing cleaning schedules and tasks. Uses round-robin queue
 * system for fair task distribution.
 */
@Service
public class CleaningScheduleService {

    private final CleaningTaskRepository cleaningTaskRepository;
    private final CleaningTaskTemplateRepository templateRepository;
    private final RoomAssignmentQueueRepository queueRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final CleaningMapper cleaningMapper;

    @Autowired
    public CleaningScheduleService(CleaningTaskRepository cleaningTaskRepository,
            CleaningTaskTemplateRepository templateRepository, RoomAssignmentQueueRepository queueRepository,
            UserRepository userRepository, RoomRepository roomRepository, CleaningMapper cleaningMapper) {
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.templateRepository = templateRepository;
        this.queueRepository = queueRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.cleaningMapper = cleaningMapper;
    }

    /**
     * Get the start of the current week (Monday).
     */
    public LocalDate getCurrentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Get all cleaning tasks for the current week for a WG.
     */
    public List<CleaningTask> getCurrentWeekTasks(WG wg) {
        LocalDate weekStart = getCurrentWeekStart();
        return cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
    }

    /**
     * Get all cleaning tasks for a specific week for a WG. Ensures all applicable
     * templates have corresponding tasks for the week, generating missing ones
     * automatically using round-robin assignment.
     */
    @Transactional
    public List<CleaningTask> getTasksForWeek(WG wg, LocalDate weekStart) {
        List<CleaningTask> tasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);

        // Always check if there are missing tasks from templates
        if (hasTemplate(wg)) {
            List<CleaningTask> generatedTasks = generateMissingTasksFromTemplate(wg, weekStart, tasks);
            tasks.addAll(generatedTasks);
        }

        return tasks;
    }

    /**
     * DTO variant of getTasksForWeek to keep UI decoupled from entities.
     */
    public List<CleaningTaskDTO> getTasksForWeekDTO(WG wg, LocalDate weekStart) {
        return cleaningMapper.toDTOList(getTasksForWeek(wg, weekStart));
    }

    /**
     * Generate only the missing tasks from templates for a specific week. Checks
     * which templates don't have a corresponding task yet and creates them. Only
     * generates tasks for current week or future weeks, not past weeks.
     */
    @Transactional
    public List<CleaningTask> generateMissingTasksFromTemplate(WG wg, LocalDate weekStart,
            List<CleaningTask> existingTasks) {
        // Don't generate tasks for past weeks
        LocalDate currentWeekStart = getCurrentWeekStart();
        if (weekStart.isBefore(currentWeekStart)) {
            return new ArrayList<>();
        }

        List<CleaningTaskTemplate> templates = templateRepository.findByWg(wg);
        if (templates.isEmpty()) {
            return new ArrayList<>();
        }

        List<User> members = wg.getMitbewohner();
        if (members.isEmpty()) {
            return new ArrayList<>();
        }

        // Build a set of room IDs that already have tasks for this week
        java.util.Set<Long> existingRoomIds = new java.util.HashSet<>();
        for (CleaningTask task : existingTasks) {
            existingRoomIds.add(task.getRoom().getId());
        }

        List<CleaningTask> newTasks = new ArrayList<>();
        for (CleaningTaskTemplate template : templates) {
            // Skip if this room already has a task for this week
            if (existingRoomIds.contains(template.getRoom().getId())) {
                continue;
            }

            // Check if this task should be generated this week based on recurrence
            if (!shouldGenerateTaskThisWeek(template, weekStart)) {
                continue;
            }

            // Get or create queue for this room
            RoomAssignmentQueue queue = getOrCreateQueueForRoom(wg, template.getRoom(), members);

            // Get the next assignee from the queue
            User assignee = getNextAssigneeFromQueue(queue, members);
            if (assignee == null) {
                continue; // Skip if no valid assignee
            }

            LocalDate dueDate = weekStart.plusDays(template.getDayOfWeek() - 1);
            CleaningTask task = new CleaningTask(template.getRoom(), assignee, wg, weekStart, dueDate);
            newTasks.add(cleaningTaskRepository.save(task));

            // Rotate the queue for next time
            queue.rotate();
            queueRepository.save(queue);
        }

        return newTasks;
    }

    /**
     * Generate tasks from template for a specific week using round-robin
     * assignment. This replaces any existing tasks for the week. Each room's queue
     * determines the assignee, then the queue rotates.
     */
    @Transactional
    public List<CleaningTask> generateFromTemplateForWeek(WG wg, LocalDate weekStart) {
        List<CleaningTask> existingTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
        return generateMissingTasksFromTemplate(wg, weekStart, existingTasks);
    }

    /**
     * Get the next assignee from a queue, validating the user still exists.
     */
    private User getNextAssigneeFromQueue(RoomAssignmentQueue queue, List<User> currentMembers) {
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
     */
    private RoomAssignmentQueue getOrCreateQueueForRoom(WG wg, Room room, List<User> members) {
        List<RoomAssignmentQueue> queues = queueRepository.findByWgAndRoom(wg, room);
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
     */
    private void syncQueueWithMembers(RoomAssignmentQueue queue, List<User> currentMembers) {
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
        queue.setMemberQueueOrder(
                queueIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
    }

    /**
     * Sync all queues for a WG with current members. Call this when members join or
     * leave the WG.
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
     * Reset the cleaning schedule when membership changes (join or leave). This
     * deletes all current and future tasks and regenerates them fresh, clearing all
     * manual overrides and reassignments.
     * 
     * Call this when a member joins or leaves the WG.
     */
    @Transactional
    public void resetScheduleForMembershipChange(WG wg) {
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
        if (hasTemplate(wg)) {
            generateFromTemplateForWeek(wg, currentWeekStart);
        }
    }

    /**
     * Reassign all incomplete tasks from a departing user to other members. Uses
     * the room's queue to determine the next assignee. Call this when a member
     * leaves the WG.
     */
    @Transactional
    public void reassignTasksFromDepartedMember(WG wg, Long departedUserId) {
        LocalDate currentWeekStart = getCurrentWeekStart();
        List<User> currentMembers = wg.getMitbewohner();

        // Find all incomplete tasks assigned to the departed user (current week and
        // future)
        List<CleaningTask> tasks = cleaningTaskRepository.findByWg(wg);
        for (CleaningTask task : tasks) {
            // Only reassign incomplete tasks from current week onwards
            if (!task.isCompleted() && task.getAssignee().getId().equals(departedUserId)
                    && !task.getWeekStartDate().isBefore(currentWeekStart)) {

                // Get the queue for this room and find the next available assignee
                List<RoomAssignmentQueue> queues = queueRepository.findByWgAndRoom(wg, task.getRoom());
                if (!queues.isEmpty() && !currentMembers.isEmpty()) {
                    RoomAssignmentQueue queue = queues.get(0);
                    User newAssignee = getNextAssigneeFromQueue(queue, currentMembers);
                    if (newAssignee != null) {
                        task.setAssignee(newAssignee);
                        cleaningTaskRepository.save(task);
                    }
                }
            }
        }
    }

    /**
     * Get all tasks assigned to a user for the current week.
     */
    public List<CleaningTask> getUserTasksForCurrentWeek(User user) {
        LocalDate weekStart = getCurrentWeekStart();
        return cleaningTaskRepository.findByAssigneeAndWeekStartDate(user, weekStart);
    }

    /**
     * Generate a new weekly schedule from saved templates using round-robin.
     */
    @Transactional
    public List<CleaningTask> generateFromTemplate(WG wg) {
        LocalDate weekStart = getCurrentWeekStart();

        // Delete existing tasks for this week
        List<CleaningTask> existingTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
        cleaningTaskRepository.deleteAll(existingTasks);

        // Generate new tasks using round-robin
        return generateFromTemplateForWeek(wg, weekStart);
    }

    /**
     * Save current week's schedule as the default template. Also initializes
     * assignment queues for each room.
     */
    @Transactional
    public List<CleaningTaskTemplate> saveAsTemplate(WG wg) {
        LocalDate weekStart = getCurrentWeekStart();
        List<CleaningTask> currentTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);

        // Delete existing templates and queues
        templateRepository.deleteByWg(wg);
        queueRepository.deleteByWg(wg);

        List<User> members = wg.getMitbewohner();

        // Create templates and queues from current tasks
        List<CleaningTaskTemplate> templates = new ArrayList<>();
        int offset = 0;
        for (CleaningTask task : currentTasks) {
            int dayOfWeek = task.getDueDate() != null ? task.getDueDate().getDayOfWeek().getValue() : 1;
            CleaningTaskTemplate template = new CleaningTaskTemplate(task.getRoom(), wg, dayOfWeek);
            templates.add(templateRepository.save(template));

            // Create queue for this room with offset
            RoomAssignmentQueue queue = new RoomAssignmentQueue(task.getRoom(), wg, members, offset);
            queueRepository.save(queue);
            offset++;
        }

        return templates;
    }

    /**
     * Get all templates for a WG.
     */
    public List<CleaningTaskTemplate> getTemplates(WG wg) {
        return templateRepository.findByWgOrderByDayOfWeekAsc(wg);
    }

    /**
     * Get all templates for a WG as DTOs.
     */
    public List<CleaningTaskTemplateDTO> getTemplatesDTO(WG wg) {
        return cleaningMapper.toTemplateDTOList(getTemplates(wg));
    }

    /**
     * Check if templates exist for a WG.
     */
    public boolean hasTemplate(WG wg) {
        return !templateRepository.findByWg(wg).isEmpty();
    }

    /**
     * Get the next assignee for a room (who would be assigned if tasks were
     * generated now).
     */
    public User getNextAssigneeForRoom(WG wg, Room room) {
        List<RoomAssignmentQueue> queues = queueRepository.findByWgAndRoom(wg, room);
        if (queues.isEmpty()) {
            return null;
        }

        Long nextId = queues.get(0).getNextAssigneeId();
        if (nextId == null) {
            return null;
        }

        return userRepository.findById(nextId).orElse(null);
    }

    /**
     * Assign a cleaning task for a specific room to a user.
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

    @Transactional
    public CleaningTaskDTO assignTaskDTO(Room room, User assignee, WG wg) {
        return cleaningMapper.toDTO(assignTask(room, assignee, wg));
    }

    @Transactional
    public CleaningTaskDTO assignTaskByIds(Long roomId, Long assigneeId, WG wg) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("Room not found"));
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new IllegalArgumentException("Assignee not found"));
        return assignTaskDTO(room, assignee, wg);
    }

    /**
     * Reassign a task to a different user by swapping with their next scheduled
     * task. This preserves fairness: no tasks are created or removed, each person
     * keeps the same total number of tasks.
     * 
     * Example: If A has task at week 1 and wants to give it to C who has task at
     * week 3, after swap: C has task at week 1, A has task at week 3.
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
     * Reschedule a task to a different day.
     */
    @Transactional
    public CleaningTask rescheduleTask(CleaningTask task, LocalDate newDueDate) {
        task.setDueDate(newDueDate);
        task.setManualOverride(true);
        return cleaningTaskRepository.save(task);
    }

    @Transactional
    public CleaningTaskDTO rescheduleTask(Long taskId, LocalDate newDueDate) {
        CleaningTask task = cleaningTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        CleaningTask updated = rescheduleTask(task, newDueDate);
        return cleaningMapper.toDTO(updated);
    }

    /**
     * Mark a cleaning task as complete.
     */
    @Transactional
    public CleaningTask markTaskComplete(CleaningTask task) {
        task.markComplete();
        return cleaningTaskRepository.save(task);
    }

    @Transactional
    public CleaningTaskDTO markTaskComplete(Long taskId) {
        CleaningTask task = cleaningTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        CleaningTask updated = markTaskComplete(task);
        return cleaningMapper.toDTO(updated);
    }

    /**
     * Mark a cleaning task as incomplete.
     */
    @Transactional
    public CleaningTask markTaskIncomplete(CleaningTask task) {
        task.markIncomplete();
        return cleaningTaskRepository.save(task);
    }

    @Transactional
    public CleaningTaskDTO markTaskIncomplete(Long taskId) {
        CleaningTask task = cleaningTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        CleaningTask updated = markTaskIncomplete(task);
        return cleaningMapper.toDTO(updated);
    }

    /**
     * Get a task by ID.
     */
    public Optional<CleaningTask> getTask(Long id) {
        return cleaningTaskRepository.findById(id);
    }

    /**
     * Delete a cleaning task.
     */
    @Transactional
    public void deleteTask(CleaningTask task) {
        cleaningTaskRepository.delete(task);
    }

    // ========== Template CRUD Methods ==========

    /**
     * Add a new template task with round-robin queue. No assignee needed - queue
     * handles assignment automatically.
     */
    @Transactional
    public CleaningTaskTemplate addTemplate(WG wg, Room room, DayOfWeek dayOfWeek, RecurrenceInterval interval) {
        LocalDate weekStart = getCurrentWeekStart();
        CleaningTaskTemplate template = new CleaningTaskTemplate(room, wg, dayOfWeek, interval, weekStart);
        template = templateRepository.save(template);

        // Create queue for this room with offset based on existing queue count
        List<User> members = wg.getMitbewohner();
        int offset = (int) queueRepository.countByWg(wg);
        RoomAssignmentQueue queue = new RoomAssignmentQueue(room, wg, members, offset);
        queueRepository.save(queue);

        return template;
    }

    /**
     * Add a new template task by room ID - for DTO-based controller usage.
     */
    @Transactional
    public CleaningTaskTemplateDTO addTemplateByRoomId(WG wg, Long roomId, DayOfWeek dayOfWeek,
            RecurrenceInterval interval) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("Room not found"));
        CleaningTaskTemplate template = addTemplate(wg, room, dayOfWeek, interval);
        return cleaningMapper.toTemplateDTO(template);
    }

    /**
     * Update an existing template (day and recurrence can be changed, assignee is
     * auto-managed). Only updates due dates for current and future tasks, not past
     * ones.
     */
    @Transactional
    public CleaningTaskTemplate updateTemplate(CleaningTaskTemplate template, DayOfWeek newDay,
            RecurrenceInterval newInterval) {
        LocalDate currentWeekStart = getCurrentWeekStart();

        // Update due dates only for current and future tasks, not past ones
        List<CleaningTask> tasks = cleaningTaskRepository.findByWgAndRoom(template.getWg(), template.getRoom());
        for (CleaningTask task : tasks) {
            // Only update tasks from current week onwards
            if (!task.getWeekStartDate().isBefore(currentWeekStart)) {
                LocalDate newDueDate = task.getWeekStartDate().plusDays(newDay.getValue() - 1);
                task.setDueDate(newDueDate);
                cleaningTaskRepository.save(task);
            }
        }

        template.setDayOfWeek(newDay);
        template.setRecurrenceInterval(newInterval);
        // Reset base week when interval changes to current week for predictable
        // behavior
        template.setBaseWeekStart(getCurrentWeekStart());
        return templateRepository.save(template);
    }

    /**
     * Delete a single template and its associated queue. Only deletes current and
     * future tasks for this room, preserves past tasks.
     */
    @Transactional
    public void deleteTemplate(CleaningTaskTemplate template) {
        LocalDate currentWeekStart = getCurrentWeekStart();

        // Delete only current and future tasks for this room, preserve past tasks
        List<CleaningTask> allTasks = cleaningTaskRepository.findByWgAndRoom(template.getWg(), template.getRoom());
        for (CleaningTask task : allTasks) {
            if (!task.getWeekStartDate().isBefore(currentWeekStart)) {
                cleaningTaskRepository.delete(task);
            }
        }

        queueRepository.deleteByRoom(template.getRoom());
        templateRepository.delete(template);
    }

    /**
     * Clear all templates and queues for a WG. Only deletes current and future
     * tasks, preserves past tasks.
     */
    @Transactional
    public void clearTemplates(WG wg) {
        LocalDate currentWeekStart = getCurrentWeekStart();

        // Delete only current and future tasks for this WG, preserve past tasks
        List<CleaningTask> allTasks = cleaningTaskRepository.findByWg(wg);
        for (CleaningTask task : allTasks) {
            if (!task.getWeekStartDate().isBefore(currentWeekStart)) {
                cleaningTaskRepository.delete(task);
            }
        }

        queueRepository.deleteByWg(wg);
        templateRepository.deleteByWg(wg);
    }

    /**
     * Delete all cleaning-related data for a specific room. This must be called
     * before deleting the room itself.
     */
    @Transactional
    public void deleteRoomData(Room room) {
        // Delete all tasks for this room
        List<CleaningTask> tasks = cleaningTaskRepository.findAll().stream()
                .filter(t -> t.getRoom().getId().equals(room.getId())).collect(java.util.stream.Collectors.toList());
        cleaningTaskRepository.deleteAll(tasks);

        // Delete all templates for this room
        List<CleaningTaskTemplate> templates = templateRepository.findAll().stream()
                .filter(t -> t.getRoom().getId().equals(room.getId())).collect(java.util.stream.Collectors.toList());
        templateRepository.deleteAll(templates);

        // Delete all queues for this room
        queueRepository.deleteByRoom(room);
    }

    /**
     * Syncs the current week's schedule with the default templates. Overwrites
     * tasks that are NOT manually overridden. Preserves tasks that have manual
     * adjustments.
     */
    @Transactional
    public void syncCurrentWeekWithTemplate(WG wg) {
        LocalDate weekStart = getCurrentWeekStart();
        List<CleaningTask> existingTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
        List<CleaningTaskTemplate> templates = templateRepository.findByWg(wg);

        // Track which rooms in the current week are accounted for
        List<Long> roomIdsInTemplates = new ArrayList<>();
        for (CleaningTaskTemplate t : templates) {
            roomIdsInTemplates.add(t.getRoom().getId());
        }

        // 1. Delete tasks for rooms that are no longer in the template (if not
        // overridden)
        for (CleaningTask task : new ArrayList<>(existingTasks)) {
            if (!roomIdsInTemplates.contains(task.getRoom().getId()) && !task.isManualOverride()) {
                cleaningTaskRepository.delete(task);
                existingTasks.remove(task);
            }
        }

        // 2. Update or create tasks from templates
        List<User> members = wg.getMitbewohner();
        for (CleaningTaskTemplate template : templates) {
            if (!shouldGenerateTaskThisWeek(template, weekStart)) {
                // If it shouldn't be here this week, remove existing one if not overridden
                existingTasks.stream()
                        .filter(t -> t.getRoom().getId().equals(template.getRoom().getId()) && !t.isManualOverride())
                        .findFirst().ifPresent(task -> {
                            cleaningTaskRepository.delete(task);
                            existingTasks.remove(task);
                        });
                continue;
            }

            Optional<CleaningTask> existing = existingTasks.stream()
                    .filter(t -> t.getRoom().getId().equals(template.getRoom().getId())).findFirst();

            if (existing.isPresent()) {
                CleaningTask task = existing.get();
                if (!task.isManualOverride()) {
                    // Update due date if changed in template
                    LocalDate correctDueDate = weekStart.plusDays(template.getDayOfWeek() - 1);
                    task.setDueDate(correctDueDate);
                    cleaningTaskRepository.save(task);
                }
            } else {
                // Create missing task
                RoomAssignmentQueue queue = getOrCreateQueueForRoom(wg, template.getRoom(), members);
                User assignee = getNextAssigneeFromQueue(queue, members);
                if (assignee != null) {
                    LocalDate dueDate = weekStart.plusDays(template.getDayOfWeek() - 1);
                    CleaningTask task = new CleaningTask(template.getRoom(), assignee, wg, weekStart, dueDate);
                    cleaningTaskRepository.save(task);
                }
            }
        }
    }

    /**
     * Helper to determine if a task should be generated for a specific week based
     * on its recurrence interval and base week.
     */
    private boolean shouldGenerateTaskThisWeek(CleaningTaskTemplate template, LocalDate weekStart) {
        if (template.getRecurrenceInterval() == RecurrenceInterval.WEEKLY) {
            return true;
        }

        long weeksBetween = ChronoUnit.WEEKS.between(template.getBaseWeekStart(), weekStart);
        int intervalWeeks = template.getRecurrenceInterval().getWeeks();

        return weeksBetween % intervalWeeks == 0;
    }
}
