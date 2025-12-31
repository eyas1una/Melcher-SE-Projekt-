package com.group_2.model.cleaning;

import com.group_2.model.User;
import com.group_2.model.WG;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a cleaning task for a specific room.
 * Each task is assigned to a user for a specific week.
 */
@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_tasks_wg_week", columnList = "wg_id, week_start_date"),
        @Index(name = "idx_tasks_room", columnList = "room_id"),
        @Index(name = "idx_tasks_assignee", columnList = "assignee_id")
})
public class CleaningTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assignee_id", nullable = false)
    private User assignee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "wg_id", nullable = false)
    private WG wg;

    @Column(nullable = false)
    private LocalDate weekStartDate;

    /**
     * The specific day the task should be completed by.
     */
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean completed = false;

    private LocalDateTime completedAt;

    /**
     * Flag to indicate if this task was manually modified (assignee or due date).
     * If true, auto-generation from template will not overwrite this task.
     */
    @Column(nullable = true)
    private Boolean manualOverride = false;

    public CleaningTask() {
    }

    public CleaningTask(Room room, User assignee, WG wg, LocalDate weekStartDate) {
        this.room = room;
        this.assignee = assignee;
        this.wg = wg;
        this.weekStartDate = weekStartDate;
        this.dueDate = weekStartDate; // Default to first day of week
        this.completed = false;
    }

    public CleaningTask(Room room, User assignee, WG wg, LocalDate weekStartDate, LocalDate dueDate) {
        this.room = room;
        this.assignee = assignee;
        this.wg = wg;
        this.weekStartDate = weekStartDate;
        this.dueDate = dueDate;
        this.completed = false;
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

    public User getAssignee() {
        return assignee;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }

    public WG getWg() {
        return wg;
    }

    public void setWg(WG wg) {
        this.wg = wg;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Mark this task as completed with the current timestamp.
     */
    public void markComplete() {
        this.completed = true;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Mark this task as incomplete.
     */
    public void markIncomplete() {
        this.completed = false;
        this.completedAt = null;
    }

    public boolean isManualOverride() {
        return manualOverride != null && manualOverride;
    }

    public void setManualOverride(boolean manualOverride) {
        this.manualOverride = manualOverride;
    }
}
