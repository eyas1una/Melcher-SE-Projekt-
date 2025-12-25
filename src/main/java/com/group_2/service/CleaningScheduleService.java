package com.group_2.service;

import com.group_2.repository.CleaningTaskRepository;
import com.group_2.repository.CleaningTaskTemplateRepository;
import com.model.CleaningTask;
import com.model.CleaningTaskTemplate;
import com.model.Room;
import com.model.User;
import com.model.WG;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing cleaning schedules and tasks.
 */
@Service
public class CleaningScheduleService {

    private final CleaningTaskRepository cleaningTaskRepository;
    private final CleaningTaskTemplateRepository templateRepository;

    @Autowired
    public CleaningScheduleService(CleaningTaskRepository cleaningTaskRepository,
            CleaningTaskTemplateRepository templateRepository) {
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.templateRepository = templateRepository;
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
     * tasks from the template.
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
     * Generate tasks from template for a specific week.
     */
    @Transactional
    public List<CleaningTask> generateFromTemplateForWeek(WG wg, LocalDate weekStart) {
        List<CleaningTaskTemplate> templates = templateRepository.findByWg(wg);
        if (templates.isEmpty()) {
            return new ArrayList<>();
        }

        List<CleaningTask> newTasks = new ArrayList<>();
        for (CleaningTaskTemplate template : templates) {
            LocalDate dueDate = weekStart.plusDays(template.getDayOfWeek() - 1);
            CleaningTask task = new CleaningTask(
                    template.getRoom(),
                    template.getDefaultAssignee(),
                    wg,
                    weekStart,
                    dueDate);
            newTasks.add(cleaningTaskRepository.save(task));
        }

        return newTasks;
    }

    /**
     * Get all tasks assigned to a user for the current week.
     */
    public List<CleaningTask> getUserTasksForCurrentWeek(User user) {
        LocalDate weekStart = getCurrentWeekStart();
        return cleaningTaskRepository.findByAssigneeAndWeekStartDate(user, weekStart);
    }

    /**
     * Generate a new weekly schedule, randomly distributing rooms among WG members
     * and assigning each task to a random day of the week.
     */
    @Transactional
    public List<CleaningTask> generateWeeklySchedule(WG wg) {
        if (wg == null || wg.rooms == null || wg.rooms.isEmpty()) {
            return new ArrayList<>();
        }

        List<User> members = wg.getMitbewohner();
        if (members.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDate weekStart = getCurrentWeekStart();
        java.util.Random random = new java.util.Random();

        // Delete existing tasks for this week
        List<CleaningTask> existingTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
        cleaningTaskRepository.deleteAll(existingTasks);

        // Randomly distribute rooms among members with random days
        List<CleaningTask> newTasks = new ArrayList<>();
        List<Room> rooms = new ArrayList<>(wg.rooms);
        java.util.Collections.shuffle(rooms, random);

        for (Room room : rooms) {
            User assignee = members.get(random.nextInt(members.size()));
            LocalDate dueDate = weekStart.plusDays(random.nextInt(7));
            CleaningTask task = new CleaningTask(room, assignee, wg, weekStart, dueDate);
            newTasks.add(cleaningTaskRepository.save(task));
        }

        return newTasks;
    }

    /**
     * Generate a new weekly schedule from saved templates.
     */
    @Transactional
    public List<CleaningTask> generateFromTemplate(WG wg) {
        List<CleaningTaskTemplate> templates = templateRepository.findByWg(wg);
        if (templates.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDate weekStart = getCurrentWeekStart();

        // Delete existing tasks for this week
        List<CleaningTask> existingTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
        cleaningTaskRepository.deleteAll(existingTasks);

        // Create tasks from templates
        List<CleaningTask> newTasks = new ArrayList<>();
        for (CleaningTaskTemplate template : templates) {
            LocalDate dueDate = weekStart.plusDays(template.getDayOfWeek() - 1);
            CleaningTask task = new CleaningTask(
                    template.getRoom(),
                    template.getDefaultAssignee(),
                    wg,
                    weekStart,
                    dueDate);
            newTasks.add(cleaningTaskRepository.save(task));
        }

        return newTasks;
    }

    /**
     * Save current week's schedule as the default template.
     */
    @Transactional
    public List<CleaningTaskTemplate> saveAsTemplate(WG wg) {
        LocalDate weekStart = getCurrentWeekStart();
        List<CleaningTask> currentTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);

        // Delete existing templates
        templateRepository.deleteByWg(wg);

        // Create templates from current tasks
        List<CleaningTaskTemplate> templates = new ArrayList<>();
        for (CleaningTask task : currentTasks) {
            int dayOfWeek = task.getDueDate() != null
                    ? task.getDueDate().getDayOfWeek().getValue()
                    : 1;
            CleaningTaskTemplate template = new CleaningTaskTemplate(
                    task.getRoom(),
                    task.getAssignee(),
                    wg,
                    dayOfWeek);
            templates.add(templateRepository.save(template));
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
     */
    @Transactional
    public CleaningTask reassignTask(CleaningTask task, User newAssignee) {
        task.setAssignee(newAssignee);
        return cleaningTaskRepository.save(task);
    }

    /**
     * Reschedule a task to a different day.
     */
    @Transactional
    public CleaningTask rescheduleTask(CleaningTask task, LocalDate newDueDate) {
        task.setDueDate(newDueDate);
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
     * Add a new template task.
     */
    @Transactional
    public CleaningTaskTemplate addTemplate(WG wg, Room room, User assignee, DayOfWeek dayOfWeek) {
        CleaningTaskTemplate template = new CleaningTaskTemplate(room, assignee, wg, dayOfWeek);
        return templateRepository.save(template);
    }

    /**
     * Update an existing template.
     */
    @Transactional
    public CleaningTaskTemplate updateTemplate(CleaningTaskTemplate template, User newAssignee, DayOfWeek newDay) {
        template.setDefaultAssignee(newAssignee);
        template.setDayOfWeek(newDay);
        return templateRepository.save(template);
    }

    /**
     * Delete a single template.
     */
    @Transactional
    public void deleteTemplate(CleaningTaskTemplate template) {
        templateRepository.delete(template);
    }

    /**
     * Clear all templates for a WG.
     */
    @Transactional
    public void clearTemplates(WG wg) {
        templateRepository.deleteByWg(wg);
    }
}
