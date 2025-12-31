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
import com.group_2.dto.core.CoreMapper;
import com.group_2.dto.core.UserSummaryDTO;
import com.group_2.repository.UserRepository;
import com.group_2.repository.WGRepository;
import com.group_2.repository.cleaning.CleaningTaskRepository;
import com.group_2.repository.cleaning.CleaningTaskTemplateRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Coordinating service for cleaning schedules and tasks.
 * Delegates specialized operations to focused services:
 * - QueueManagementService: Queue operations
 * - CleaningTemplateService: Template CRUD and sync
 * - CleaningTaskAssignmentService: Task assignment
 * - CleaningTaskLifecycleService: Task status and CRUD
 */
@Service
public class CleaningScheduleService {

    private final CleaningTaskRepository cleaningTaskRepository;
    private final CleaningTaskTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final WGRepository wgRepository;
    private final CleaningMapper cleaningMapper;
    private final CoreMapper coreMapper;

    // Delegated services
    private final QueueManagementService queueManagementService;
    private final CleaningTemplateService templateService;
    private final CleaningTaskAssignmentService assignmentService;
    private final CleaningTaskLifecycleService lifecycleService;

    @Autowired
    public CleaningScheduleService(CleaningTaskRepository cleaningTaskRepository,
            CleaningTaskTemplateRepository templateRepository, UserRepository userRepository,
            WGRepository wgRepository, CleaningMapper cleaningMapper, CoreMapper coreMapper,
            QueueManagementService queueManagementService, CleaningTemplateService templateService,
            CleaningTaskAssignmentService assignmentService, CleaningTaskLifecycleService lifecycleService) {
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.wgRepository = wgRepository;
        this.cleaningMapper = cleaningMapper;
        this.coreMapper = coreMapper;
        this.queueManagementService = queueManagementService;
        this.templateService = templateService;
        this.assignmentService = assignmentService;
        this.lifecycleService = lifecycleService;
    }

    // ========== Core Query Methods ==========

    public List<UserSummaryDTO> getMemberSummaries(Long wgId) {
        if (wgId == null) {
            return List.of();
        }
        return coreMapper.toUserSummaries(userRepository.findByWgId(wgId));
    }

    public LocalDate getCurrentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public List<CleaningTask> getCurrentWeekTasks(WG wg) {
        return cleaningTaskRepository.findByWgAndWeekStartDate(wg, getCurrentWeekStart());
    }

    @Transactional
    public List<CleaningTask> getTasksForWeek(WG wg, LocalDate weekStart) {
        List<CleaningTask> tasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
        if (templateService.hasTemplate(wg)) {
            List<CleaningTask> generatedTasks = generateMissingTasksFromTemplate(wg, weekStart, tasks);
            tasks.addAll(generatedTasks);
        }
        return tasks;
    }

    @Transactional
    public List<CleaningTaskDTO> getTasksForWeekDTO(WG wg, LocalDate weekStart) {
        return cleaningMapper.toDTOList(getTasksForWeek(wg, weekStart));
    }

    @Transactional
    public List<CleaningTaskDTO> getTasksForWeekDTO(Long wgId, LocalDate weekStart) {
        return getTasksForWeekDTO(requireWg(wgId), weekStart);
    }

    public List<CleaningTask> getUserTasksForCurrentWeek(User user) {
        return cleaningTaskRepository.findByAssigneeAndWeekStartDate(user, getCurrentWeekStart());
    }

    // ========== Task Generation Methods ==========

    @Transactional
    public List<CleaningTask> generateMissingTasksFromTemplate(WG wg, LocalDate weekStart,
            List<CleaningTask> existingTasks) {
        if (weekStart.isBefore(getCurrentWeekStart())) {
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

        Set<Long> existingRoomIds = new HashSet<>();
        for (CleaningTask task : existingTasks) {
            existingRoomIds.add(task.getRoom().getId());
        }

        List<CleaningTask> newTasks = new ArrayList<>();
        for (CleaningTaskTemplate template : templates) {
            if (existingRoomIds.contains(template.getRoom().getId())) {
                continue;
            }
            if (!templateService.shouldGenerateTaskThisWeek(template, weekStart)) {
                continue;
            }

            RoomAssignmentQueue queue = queueManagementService.getOrCreateQueueForRoom(wg, template.getRoom(), members);
            User assignee = queueManagementService.getNextAssigneeFromQueue(queue, members);
            if (assignee == null) {
                continue;
            }

            LocalDate dueDate = templateService.resolveDueDateForWeek(template, weekStart);
            if (dueDate == null) {
                continue;
            }

            CleaningTask task = new CleaningTask(template.getRoom(), assignee, wg, weekStart, dueDate);
            newTasks.add(cleaningTaskRepository.save(task));
            queueManagementService.advanceQueue(queue);
        }

        return newTasks;
    }

    @Transactional
    public List<CleaningTask> generateFromTemplateForWeek(WG wg, LocalDate weekStart) {
        List<CleaningTask> existingTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
        return generateMissingTasksFromTemplate(wg, weekStart, existingTasks);
    }

    @Transactional
    public List<CleaningTask> generateFromTemplate(WG wg) {
        LocalDate weekStart = getCurrentWeekStart();
        List<CleaningTask> existingTasks = cleaningTaskRepository.findByWgAndWeekStartDate(wg, weekStart);
        cleaningTaskRepository.deleteAll(existingTasks);
        return generateFromTemplateForWeek(wg, weekStart);
    }

    @Transactional
    public List<CleaningTask> generateFromTemplate(Long wgId) {
        return generateFromTemplate(requireWg(wgId));
    }

    // ========== Delegated Template Methods ==========

    public List<CleaningTaskTemplate> getTemplates(WG wg) {
        return templateService.getTemplates(wg);
    }

    public List<CleaningTaskTemplateDTO> getTemplatesDTO(WG wg) {
        return templateService.getTemplatesDTO(wg);
    }

    public List<CleaningTaskTemplateDTO> getTemplatesDTO(Long wgId) {
        return templateService.getTemplatesDTO(requireWg(wgId));
    }

    public boolean hasTemplate(WG wg) {
        return templateService.hasTemplate(wg);
    }

    public boolean hasTemplate(Long wgId) {
        return templateService.hasTemplate(requireWg(wgId));
    }

    @Transactional
    public List<CleaningTaskTemplate> saveAsTemplate(WG wg) {
        return templateService.saveAsTemplate(wg);
    }

    @Transactional
    public List<CleaningTaskTemplate> saveAsTemplate(Long wgId) {
        return templateService.saveAsTemplate(requireWg(wgId));
    }

    @Transactional
    public CleaningTaskTemplate addTemplate(WG wg, Room room, DayOfWeek dayOfWeek, RecurrenceInterval interval) {
        return templateService.addTemplate(wg, room, dayOfWeek, interval);
    }

    @Transactional
    public CleaningTaskTemplateDTO addTemplateByRoomId(WG wg, Long roomId, DayOfWeek dayOfWeek,
            RecurrenceInterval interval) {
        return templateService.addTemplateByRoomId(wg, roomId, dayOfWeek, interval);
    }

    @Transactional
    public CleaningTaskTemplateDTO addTemplateByRoomId(Long wgId, Long roomId, DayOfWeek dayOfWeek,
            RecurrenceInterval interval) {
        return templateService.addTemplateByRoomId(requireWg(wgId), roomId, dayOfWeek, interval);
    }

    @Transactional
    public CleaningTaskTemplateDTO addTemplateByRoomId(WG wg, Long roomId, DayOfWeek dayOfWeek,
            RecurrenceInterval interval, LocalDate baseWeekStart) {
        return templateService.addTemplateByRoomId(wg, roomId, dayOfWeek, interval, baseWeekStart);
    }

    @Transactional
    public CleaningTaskTemplateDTO addTemplateByRoomId(Long wgId, Long roomId, DayOfWeek dayOfWeek,
            RecurrenceInterval interval, LocalDate baseWeekStart) {
        return templateService.addTemplateByRoomId(requireWg(wgId), roomId, dayOfWeek, interval, baseWeekStart);
    }

    @Transactional
    public CleaningTaskTemplate updateTemplate(CleaningTaskTemplate template, DayOfWeek newDay,
            RecurrenceInterval newInterval) {
        return templateService.updateTemplate(template, newDay, newInterval);
    }

    @Transactional
    public void deleteTemplate(CleaningTaskTemplate template) {
        templateService.deleteTemplate(template);
    }

    @Transactional
    public void clearTemplates(WG wg) {
        templateService.clearTemplates(wg);
    }

    @Transactional
    public void clearTemplates(Long wgId) {
        templateService.clearTemplates(requireWg(wgId));
    }

    @Transactional
    public void syncCurrentWeekWithTemplate(WG wg) {
        templateService.syncCurrentWeekWithTemplate(wg, queueManagementService);
    }

    // ========== Delegated Assignment Methods ==========

    @Transactional
    public CleaningTask assignTask(Room room, User assignee, WG wg) {
        return assignmentService.assignTask(room, assignee, wg);
    }

    @Transactional
    public CleaningTaskDTO assignTaskDTO(Room room, User assignee, WG wg) {
        return assignmentService.assignTaskDTO(room, assignee, wg);
    }

    @Transactional
    public CleaningTaskDTO assignTaskByIds(Long roomId, Long assigneeId, WG wg) {
        return assignmentService.assignTaskByIds(roomId, assigneeId, wg);
    }

    @Transactional
    public CleaningTaskDTO assignTaskByIds(Long roomId, Long assigneeId, Long wgId) {
        return assignmentService.assignTaskByIds(roomId, assigneeId, requireWg(wgId));
    }

    @Transactional
    public CleaningTaskDTO assignTaskByIdsWithDate(Long roomId, Long assigneeId, WG wg, LocalDate dueDate) {
        return assignmentService.assignTaskByIdsWithDate(roomId, assigneeId, wg, dueDate);
    }

    @Transactional
    public CleaningTaskDTO assignTaskByIdsWithDate(Long roomId, Long assigneeId, Long wgId, LocalDate dueDate) {
        return assignmentService.assignTaskByIdsWithDate(roomId, assigneeId, requireWg(wgId), dueDate);
    }

    @Transactional
    public CleaningTask reassignTask(CleaningTask task, User newAssignee) {
        return assignmentService.reassignTask(task, newAssignee);
    }

    @Transactional
    public CleaningTaskDTO reassignTask(Long taskId, Long newAssigneeId) {
        return assignmentService.reassignTask(taskId, newAssigneeId);
    }

    @Transactional
    public void resetScheduleForMembershipChange(WG wg) {
        assignmentService.resetScheduleForMembershipChange(wg, templateService.hasTemplate(wg),
                (w, weekStart) -> generateFromTemplateForWeek(w, weekStart));
    }

    @Transactional
    public void reassignTasksFromDepartedMember(WG wg, Long departedUserId) {
        assignmentService.reassignTasksFromDepartedMember(wg, departedUserId);
    }

    public User getNextAssigneeForRoom(WG wg, Room room) {
        return assignmentService.getNextAssigneeForRoom(wg, room);
    }

    // ========== Delegated Lifecycle Methods ==========

    @Transactional
    public CleaningTask markTaskComplete(CleaningTask task) {
        return lifecycleService.markTaskComplete(task);
    }

    @Transactional
    public CleaningTaskDTO markTaskComplete(Long taskId) {
        return lifecycleService.markTaskComplete(taskId);
    }

    @Transactional
    public CleaningTask markTaskIncomplete(CleaningTask task) {
        return lifecycleService.markTaskIncomplete(task);
    }

    @Transactional
    public CleaningTaskDTO markTaskIncomplete(Long taskId) {
        return lifecycleService.markTaskIncomplete(taskId);
    }

    @Transactional
    public CleaningTask rescheduleTask(CleaningTask task, LocalDate newDueDate) {
        return lifecycleService.rescheduleTask(task, newDueDate);
    }

    @Transactional
    public CleaningTaskDTO rescheduleTask(Long taskId, LocalDate newDueDate) {
        return lifecycleService.rescheduleTask(taskId, newDueDate);
    }

    public Optional<CleaningTask> getTask(Long id) {
        return lifecycleService.getTask(id);
    }

    @Transactional
    public void deleteTask(CleaningTask task) {
        lifecycleService.deleteTask(task);
    }

    @Transactional
    public void deleteTask(Long taskId) {
        lifecycleService.deleteTask(taskId);
    }

    @Transactional
    public void deleteRoomData(Room room) {
        lifecycleService.deleteRoomData(room);
    }

    // ========== Delegated Queue Methods ==========

    @Transactional
    public void syncAllQueuesWithMembers(WG wg) {
        queueManagementService.syncAllQueuesWithMembers(wg);
    }

    // ========== Helper Methods ==========

    private WG requireWg(Long wgId) {
        if (wgId == null) {
            throw new IllegalArgumentException("WG ID is required");
        }
        return wgRepository.findById(wgId).orElseThrow(() -> new IllegalArgumentException("WG not found"));
    }
}
