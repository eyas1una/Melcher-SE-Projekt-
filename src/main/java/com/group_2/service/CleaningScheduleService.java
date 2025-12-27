package com.group_2.service;

import com.group_2.repository.CleaningTaskRepository;
import com.group_2.repository.CleaningTaskTemplateRepository;
import com.group_2.repository.RoomAssignmentQueueRepository;
import com.group_2.repository.UserRepository;
import com.model.CleaningTask;
import com.model.CleaningTaskTemplate;
import com.model.RecurrenceInterval;
import com.model.Room;
import com.model.RoomAssignmentQueue;
import com.model.User;
import com.model.WG;

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
 * Service for managing cleaning schedules and tasks.
 * Uses round-robin queue system for fair task distribution.
 */
@Service
public class CleaningScheduleService {

    private final CleaningTaskRepository cleaningTaskRepository;
    private final CleaningTaskTemplateRepository templateRepository;
    private final RoomAssignmentQueueRepository queueRepository;
    private final UserRepository userRepository;

    @Autowired
    public CleaningScheduleService(CleaningTaskRepository cleaningTaskRepository,
            CleaningTaskTemplateRepository templateRepository,
            RoomAssignmentQueueRepository queueRepository,
            UserRepository userRepository) {
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.templateRepository = templateRepository;
        this.queueRepository = queueRepository;
        this.userRepository = userRepository;
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
     * Get all cleaning tasks for a specific week for a WG.
     * If no tasks exist for the week and a template exists, automatically generates
     * tasks from the template using round-robin assignment.
     */
    @Transactional
    public List<CleaningTask> getTasksForWeek(WG wg, LocalDate weekStart) {
        List<CleaningTask> tasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);

        // If no tasks exist for this week, auto-generate from template
        if (tasks.isEmpty() && hasTemplate(wg)) {
            tasks = generateFromTemplateForWeek(wg, weekStart);
        }

        return tasks;
    }

    /**
     * Generate tasks from template for a specific week using round-robin
     * assignment.
     * Each room's queue determines the assignee, then the queue rotates.
     */
    @Transactional
    public List<CleaningTask> generateFromTemplateForWeek(WG wg, LocalDate weekStart) {
        List<CleaningTaskTemplate> templates = templateRepository.findByWg(wg);
        if (templates.isEmpty()) {
            return new ArrayList<>();
        }

        List<User> members = wg.getMitbewohner();
        if (members.isEmpty()) {
            return new ArrayList<>();
        }

        List<CleaningTask> newTasks = new ArrayList<>();
        for (CleaningTaskTemplate template : templates) {
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
            CleaningTask task = new CleaningTask(
                    template.getRoom(),
                    assignee,
                    wg,
                    weekStart,
                    dueDate);
            newTasks.add(cleaningTaskRepository.save(task));

            // Rotate the queue for next time
            queue.rotate();
            queueRepository.save(queue);
        }

        return newTasks;
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
     */
    private void syncQueueWithMembers(RoomAssignmentQueue queue, List<User> currentMembers) {
        List<Long> queueIds = queue.getMemberIds();
        List<Long> currentIds = new ArrayList<>();
        for (User m : currentMembers) {
            currentIds.add(m.getId());
        }

        // Remove departed members
        queueIds.removeIf(id -> !currentIds.contains(id));

        // Add new members at the end
        for (Long id : currentIds) {
            if (!queueIds.contains(id)) {
                queueIds.add(id);
            }
        }

        // Update the queue
        queue.setMemberQueueOrder(
                queueIds.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(",")));
    }

    /**
     * Sync all queues for a WG with current members.
     * Call this when members join or leave the WG.
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
     * Save current week's schedule as the default template.
     * Also initializes assignment queues for each room.
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
            int dayOfWeek = task.getDueDate() != null
                    ? task.getDueDate().getDayOfWeek().getValue()
                    : 1;
            CleaningTaskTemplate template = new CleaningTaskTemplate(
                    task.getRoom(),
                    wg,
                    dayOfWeek);
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

    /**
     * Reassign a task to a different user.
     * Also swaps positions in the room's queue to maintain fairness.
     */
    @Transactional
    public CleaningTask reassignTask(CleaningTask task, User newAssignee) {
        User originalAssignee = task.getAssignee();

        // Swap positions in the room's queue for fairness
        if (!originalAssignee.getId().equals(newAssignee.getId())) {
            List<RoomAssignmentQueue> queues = queueRepository.findByWgAndRoom(task.getWg(), task.getRoom());
            if (!queues.isEmpty()) {
                RoomAssignmentQueue queue = queues.get(0);
                queue.swapPositions(originalAssignee.getId(), newAssignee.getId());
                queueRepository.save(queue);
            }
        }

        // Update the task assignment
        task.setAssignee(newAssignee);
        task.setManualOverride(true);
        return cleaningTaskRepository.save(task);
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

    /**
     * Mark a cleaning task as complete.
     */
    @Transactional
    public CleaningTask markTaskComplete(CleaningTask task) {
        task.markComplete();
        return cleaningTaskRepository.save(task);
    }

    /**
     * Mark a cleaning task as incomplete.
     */
    @Transactional
    public CleaningTask markTaskIncomplete(CleaningTask task) {
        task.markIncomplete();
        return cleaningTaskRepository.save(task);
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
     * Add a new template task with round-robin queue.
     * No assignee needed - queue handles assignment automatically.
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
     * Update an existing template (day and recurrence can be changed, assignee is
     * auto-managed). Also updates due dates for all existing tasks.
     */
    @Transactional
    public CleaningTaskTemplate updateTemplate(CleaningTaskTemplate template, DayOfWeek newDay,
            RecurrenceInterval newInterval) {
        // Update due dates for all existing tasks for this room
        List<CleaningTask> tasks = cleaningTaskRepository.findByWgAndRoom(template.getWg(), template.getRoom());
        for (CleaningTask task : tasks) {
            LocalDate newDueDate = task.getWeekStartDate().plusDays(newDay.getValue() - 1);
            task.setDueDate(newDueDate);
            cleaningTaskRepository.save(task);
        }

        template.setDayOfWeek(newDay);
        template.setRecurrenceInterval(newInterval);
        // Reset base week when interval changes to current week for predictable
        // behavior
        template.setBaseWeekStart(getCurrentWeekStart());
        return templateRepository.save(template);
    }

    /**
     * Delete a single template and its associated queue.
     * Also deletes all existing tasks for this room across all weeks.
     */
    @Transactional
    public void deleteTemplate(CleaningTaskTemplate template) {
        // Delete all existing tasks for this room across all weeks
        cleaningTaskRepository.deleteByWgAndRoom(template.getWg(), template.getRoom());
        queueRepository.deleteByRoom(template.getRoom());
        templateRepository.delete(template);
    }

    /**
     * Clear all templates and queues for a WG.
     * Also deletes all tasks across all weeks.
     */
    @Transactional
    public void clearTemplates(WG wg) {
        // Delete all tasks for this WG across all weeks
        cleaningTaskRepository.deleteByWg(wg);
        queueRepository.deleteByWg(wg);
        templateRepository.deleteByWg(wg);
    }

    /**
     * Syncs the current week's schedule with the default templates.
     * Overwrites tasks that are NOT manually overridden.
     * Preserves tasks that have manual adjustments.
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
                        .findFirst()
                        .ifPresent(task -> {
                            cleaningTaskRepository.delete(task);
                            existingTasks.remove(task);
                        });
                continue;
            }

            Optional<CleaningTask> existing = existingTasks.stream()
                    .filter(t -> t.getRoom().getId().equals(template.getRoom().getId()))
                    .findFirst();

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
