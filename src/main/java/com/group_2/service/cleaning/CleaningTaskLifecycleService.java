package com.group_2.service.cleaning;

import com.group_2.dto.cleaning.CleaningMapper;
import com.group_2.dto.cleaning.CleaningTaskDTO;
import com.group_2.model.cleaning.CleaningTask;
import com.group_2.model.cleaning.CleaningTaskTemplate;
import com.group_2.model.cleaning.Room;
import com.group_2.repository.cleaning.CleaningTaskRepository;
import com.group_2.repository.cleaning.CleaningTaskTemplateRepository;
import com.group_2.repository.cleaning.RoomAssignmentQueueRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for cleaning task lifecycle operations (status changes and CRUD).
 * Handles task completion, rescheduling, and deletion.
 */
@Service
public class CleaningTaskLifecycleService {

    private final CleaningTaskRepository cleaningTaskRepository;
    private final CleaningTaskTemplateRepository templateRepository;
    private final RoomAssignmentQueueRepository queueRepository;
    private final CleaningMapper cleaningMapper;

    @Autowired
    public CleaningTaskLifecycleService(CleaningTaskRepository cleaningTaskRepository,
            CleaningTaskTemplateRepository templateRepository, RoomAssignmentQueueRepository queueRepository,
            CleaningMapper cleaningMapper) {
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.templateRepository = templateRepository;
        this.queueRepository = queueRepository;
        this.cleaningMapper = cleaningMapper;
    }

    /**
     * Mark a cleaning task as complete.
     *
     * @param task the task to mark complete
     * @return the updated task
     */
    @Transactional
    public CleaningTask markTaskComplete(CleaningTask task) {
        task.markComplete();
        return cleaningTaskRepository.save(task);
    }

    /**
     * Mark a cleaning task as complete by ID.
     *
     * @param taskId the task ID
     * @return DTO of the updated task
     */
    @Transactional
    public CleaningTaskDTO markTaskComplete(Long taskId) {
        CleaningTask task = cleaningTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        CleaningTask updated = markTaskComplete(task);
        return cleaningMapper.toDTO(updated);
    }

    /**
     * Mark a cleaning task as incomplete.
     *
     * @param task the task to mark incomplete
     * @return the updated task
     */
    @Transactional
    public CleaningTask markTaskIncomplete(CleaningTask task) {
        task.markIncomplete();
        return cleaningTaskRepository.save(task);
    }

    /**
     * Mark a cleaning task as incomplete by ID.
     *
     * @param taskId the task ID
     * @return DTO of the updated task
     */
    @Transactional
    public CleaningTaskDTO markTaskIncomplete(Long taskId) {
        CleaningTask task = cleaningTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        CleaningTask updated = markTaskIncomplete(task);
        return cleaningMapper.toDTO(updated);
    }

    /**
     * Reschedule a task to a different day.
     *
     * @param task       the task to reschedule
     * @param newDueDate the new due date
     * @return the updated task
     */
    @Transactional
    public CleaningTask rescheduleTask(CleaningTask task, LocalDate newDueDate) {
        if (newDueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot reschedule a task to a date in the past.");
        }
        task.setDueDate(newDueDate);
        task.setManualOverride(true);
        return cleaningTaskRepository.save(task);
    }

    /**
     * Reschedule a task by ID.
     *
     * @param taskId     the task ID
     * @param newDueDate the new due date
     * @return DTO of the updated task
     */
    @Transactional
    public CleaningTaskDTO rescheduleTask(Long taskId, LocalDate newDueDate) {
        CleaningTask task = cleaningTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        CleaningTask updated = rescheduleTask(task, newDueDate);
        return cleaningMapper.toDTO(updated);
    }

    /**
     * Get a task by ID.
     *
     * @param id the task ID
     * @return optional containing the task if found
     */
    public Optional<CleaningTask> getTask(Long id) {
        return cleaningTaskRepository.findById(id);
    }

    /**
     * Delete a cleaning task.
     *
     * @param task the task to delete
     */
    @Transactional
    public void deleteTask(CleaningTask task) {
        cleaningTaskRepository.delete(task);
    }

    /**
     * Delete a cleaning task by ID.
     *
     * @param taskId the task ID
     */
    @Transactional
    public void deleteTask(Long taskId) {
        CleaningTask task = cleaningTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        cleaningTaskRepository.delete(task);
    }

    /**
     * Delete all cleaning-related data for a specific room. This must be called
     * before deleting the room itself.
     *
     * @param room the room whose data should be deleted
     */
    @Transactional
    public void deleteRoomData(Room room) {
        // Delete all tasks for this room
        List<CleaningTask> tasks = cleaningTaskRepository.findAll().stream()
                .filter(t -> t.getRoom().getId().equals(room.getId())).collect(Collectors.toList());
        cleaningTaskRepository.deleteAll(tasks);

        // Delete all templates for this room
        List<CleaningTaskTemplate> templates = templateRepository.findAll().stream()
                .filter(t -> t.getRoom().getId().equals(room.getId())).collect(Collectors.toList());
        templateRepository.deleteAll(templates);

        // Delete all queues for this room
        queueRepository.deleteByRoom(room);
    }
}
