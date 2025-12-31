package com.group_2.service.cleaning;

import com.group_2.dto.cleaning.CleaningMapper;
import com.group_2.dto.cleaning.CleaningTaskTemplateDTO;
import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.model.cleaning.CleaningTask;
import com.group_2.model.cleaning.CleaningTaskTemplate;
import com.group_2.model.cleaning.RecurrenceInterval;
import com.group_2.model.cleaning.Room;
import com.group_2.model.cleaning.RoomAssignmentQueue;
import com.group_2.repository.cleaning.CleaningTaskRepository;
import com.group_2.repository.cleaning.CleaningTaskTemplateRepository;
import com.group_2.repository.cleaning.RoomAssignmentQueueRepository;
import com.group_2.repository.cleaning.RoomRepository;
import com.group_2.util.MonthlyScheduleUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing cleaning task templates.
 * Handles template CRUD, sync operations, and recurrence calculations.
 */
@Service
public class CleaningTemplateService {

    private final CleaningTaskRepository cleaningTaskRepository;
    private final CleaningTaskTemplateRepository templateRepository;
    private final RoomAssignmentQueueRepository queueRepository;
    private final RoomRepository roomRepository;
    private final CleaningMapper cleaningMapper;

    @Autowired
    public CleaningTemplateService(CleaningTaskRepository cleaningTaskRepository,
            CleaningTaskTemplateRepository templateRepository, RoomAssignmentQueueRepository queueRepository,
            RoomRepository roomRepository, CleaningMapper cleaningMapper) {
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.templateRepository = templateRepository;
        this.queueRepository = queueRepository;
        this.roomRepository = roomRepository;
        this.cleaningMapper = cleaningMapper;
    }

    private LocalDate getCurrentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // ========== Template Query Methods ==========

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

    // ========== Template CRUD Methods ==========

    /**
     * Save current week's schedule as the default template.
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
     * Add a new template task.
     */
    @Transactional
    public CleaningTaskTemplate addTemplate(WG wg, Room room, DayOfWeek dayOfWeek, RecurrenceInterval interval) {
        return addTemplate(wg, room, dayOfWeek, interval, null);
    }

    @Transactional
    public CleaningTaskTemplate addTemplate(WG wg, Room room, DayOfWeek dayOfWeek, RecurrenceInterval interval,
            LocalDate baseWeekStart) {
        LocalDate weekStart = baseWeekStart != null ? baseWeekStart : getCurrentWeekStart();
        CleaningTaskTemplate template = new CleaningTaskTemplate(room, wg, dayOfWeek, interval, weekStart);
        template = templateRepository.save(template);

        // Create queue for this room with offset
        List<User> members = wg.getMitbewohner();
        int offset = (int) queueRepository.countByWg(wg);
        RoomAssignmentQueue queue = new RoomAssignmentQueue(room, wg, members, offset);
        queueRepository.save(queue);

        return template;
    }

    /**
     * Add a new template task by room ID.
     */
    @Transactional
    public CleaningTaskTemplateDTO addTemplateByRoomId(WG wg, Long roomId, DayOfWeek dayOfWeek,
            RecurrenceInterval interval) {
        return addTemplateByRoomId(wg, roomId, dayOfWeek, interval, null);
    }

    @Transactional
    public CleaningTaskTemplateDTO addTemplateByRoomId(WG wg, Long roomId, DayOfWeek dayOfWeek,
            RecurrenceInterval interval, LocalDate baseWeekStart) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("Room not found"));
        CleaningTaskTemplate template = addTemplate(wg, room, dayOfWeek, interval, baseWeekStart);
        return cleaningMapper.toTemplateDTO(template);
    }

    /**
     * Update an existing template.
     */
    @Transactional
    public CleaningTaskTemplate updateTemplate(CleaningTaskTemplate template, DayOfWeek newDay,
            RecurrenceInterval newInterval) {
        LocalDate currentWeekStart = getCurrentWeekStart();

        template.setDayOfWeek(newDay);
        template.setRecurrenceInterval(newInterval);
        template.setBaseWeekStart(currentWeekStart);

        // Update due dates for current and future tasks
        List<CleaningTask> tasks = cleaningTaskRepository.findByWgAndRoom(template.getWg(), template.getRoom());
        for (CleaningTask task : tasks) {
            if (!task.getWeekStartDate().isBefore(currentWeekStart)) {
                LocalDate newDueDate = resolveDueDateForWeek(template, task.getWeekStartDate());
                if (newDueDate != null) {
                    task.setDueDate(newDueDate);
                    cleaningTaskRepository.save(task);
                }
            }
        }
        return templateRepository.save(template);
    }

    /**
     * Delete a single template.
     */
    @Transactional
    public void deleteTemplate(CleaningTaskTemplate template) {
        LocalDate currentWeekStart = getCurrentWeekStart();

        // Delete only current and future tasks for this room
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
     * Clear all templates for a WG.
     */
    @Transactional
    public void clearTemplates(WG wg) {
        LocalDate currentWeekStart = getCurrentWeekStart();

        // Delete only current and future tasks
        List<CleaningTask> allTasks = cleaningTaskRepository.findByWg(wg);
        for (CleaningTask task : allTasks) {
            if (!task.getWeekStartDate().isBefore(currentWeekStart) && !task.isManualOverride()) {
                cleaningTaskRepository.delete(task);
            }
        }

        queueRepository.deleteByWg(wg);
        templateRepository.deleteByWg(wg);
    }

    // ========== Recurrence Calculation Methods ==========

    /**
     * Check if a task should be generated for a specific week.
     */
    public boolean shouldGenerateTaskThisWeek(CleaningTaskTemplate template, LocalDate weekStart) {
        if (template.getRecurrenceInterval() == RecurrenceInterval.WEEKLY) {
            return true;
        }
        if (template.getRecurrenceInterval() == RecurrenceInterval.MONTHLY) {
            return resolveMonthlyDueDateForWeek(template, weekStart) != null;
        }

        long weeksBetween = ChronoUnit.WEEKS.between(template.getBaseWeekStart(), weekStart);
        int intervalWeeks = template.getRecurrenceInterval().getWeeks();

        return weeksBetween % intervalWeeks == 0;
    }

    /**
     * Resolve the due date for a template in a specific week.
     */
    public LocalDate resolveDueDateForWeek(CleaningTaskTemplate template, LocalDate weekStart) {
        if (template.getRecurrenceInterval() == RecurrenceInterval.MONTHLY) {
            return resolveMonthlyDueDateForWeek(template, weekStart);
        }
        return weekStart.plusDays(template.getDayOfWeek() - 1);
    }

    private LocalDate resolveMonthlyDueDateForWeek(CleaningTaskTemplate template, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        int preferredDay = getTemplateBaseDueDate(template).getDayOfMonth();

        LocalDate dueDate = resolveMonthlyDueDateForMonth(weekStart, preferredDay);
        if (!dueDate.isBefore(weekStart) && !dueDate.isAfter(weekEnd)) {
            return dueDate;
        }

        LocalDate dueDateEndMonth = resolveMonthlyDueDateForMonth(weekEnd, preferredDay);
        if (!dueDateEndMonth.isBefore(weekStart) && !dueDateEndMonth.isAfter(weekEnd)) {
            return dueDateEndMonth;
        }

        return null;
    }

    private LocalDate resolveMonthlyDueDateForMonth(LocalDate monthAnchor, int preferredDay) {
        int effectiveDay = MonthlyScheduleUtil.getEffectiveDay(monthAnchor, preferredDay);
        return monthAnchor.withDayOfMonth(effectiveDay);
    }

    private LocalDate getTemplateBaseDueDate(CleaningTaskTemplate template) {
        LocalDate baseWeekStart = template.getBaseWeekStart() != null ? template.getBaseWeekStart()
                : getCurrentWeekStart();
        return baseWeekStart.plusDays(template.getDayOfWeek() - 1);
    }

    // ========== Template Sync Methods ==========

    /**
     * Sync current week's schedule with templates.
     */
    @Transactional
    public void syncCurrentWeekWithTemplate(WG wg, QueueManagementService queueManagementService) {
        LocalDate weekStart = getCurrentWeekStart();
        List<CleaningTask> existingTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
        List<CleaningTaskTemplate> templates = templateRepository.findByWg(wg);

        // Track which rooms are in templates
        List<Long> roomIdsInTemplates = new ArrayList<>();
        for (CleaningTaskTemplate t : templates) {
            roomIdsInTemplates.add(t.getRoom().getId());
        }

        // Delete tasks for rooms no longer in template
        for (CleaningTask task : new ArrayList<>(existingTasks)) {
            if (!roomIdsInTemplates.contains(task.getRoom().getId()) && !task.isManualOverride()) {
                cleaningTaskRepository.delete(task);
                existingTasks.remove(task);
            }
        }

        // Update or create tasks from templates
        List<User> members = wg.getMitbewohner();
        for (CleaningTaskTemplate template : templates) {
            if (!shouldGenerateTaskThisWeek(template, weekStart)) {
                existingTasks.stream()
                        .filter(t -> t.getRoom().getId().equals(template.getRoom().getId()) && !t.isManualOverride())
                        .findFirst().ifPresent(task -> {
                            cleaningTaskRepository.delete(task);
                            existingTasks.remove(task);
                        });
                continue;
            }

            var existing = existingTasks.stream()
                    .filter(t -> t.getRoom().getId().equals(template.getRoom().getId())).findFirst();

            if (existing.isPresent()) {
                CleaningTask task = existing.get();
                if (!task.isManualOverride()) {
                    LocalDate correctDueDate = resolveDueDateForWeek(template, weekStart);
                    if (correctDueDate != null) {
                        task.setDueDate(correctDueDate);
                        cleaningTaskRepository.save(task);
                    }
                }
            } else {
                RoomAssignmentQueue queue = queueManagementService.getOrCreateQueueForRoom(wg, template.getRoom(),
                        members);
                User assignee = queueManagementService.getNextAssigneeFromQueue(queue, members);
                if (assignee != null) {
                    LocalDate dueDate = resolveDueDateForWeek(template, weekStart);
                    if (dueDate != null) {
                        CleaningTask task = new CleaningTask(template.getRoom(), assignee, wg, weekStart, dueDate);
                        cleaningTaskRepository.save(task);
                    }
                }
            }
        }
    }
}
