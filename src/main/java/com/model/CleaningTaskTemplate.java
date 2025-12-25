package com.model;

import jakarta.persistence.*;
import java.time.DayOfWeek;

/**
 * Entity representing a template for cleaning tasks.
 * Templates define a default weekly schedule that can be applied to new weeks.
 * Each template specifies a room, default assignee, and day of week.
 */
@Entity
@Table(name = "cleaning_task_template")
public class CleaningTaskTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_assignee_id", nullable = false)
    private User defaultAssignee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "wg_id", nullable = false)
    private WG wg;

    /**
     * The day of the week this task should be scheduled on (1=Monday, 7=Sunday).
     */
    @Column(nullable = false)
    private int dayOfWeek;

    public CleaningTaskTemplate() {
    }

    public CleaningTaskTemplate(Room room, User defaultAssignee, WG wg, int dayOfWeek) {
        this.room = room;
        this.defaultAssignee = defaultAssignee;
        this.wg = wg;
        this.dayOfWeek = dayOfWeek;
    }

    public CleaningTaskTemplate(Room room, User defaultAssignee, WG wg, DayOfWeek dayOfWeek) {
        this.room = room;
        this.defaultAssignee = defaultAssignee;
        this.wg = wg;
        this.dayOfWeek = dayOfWeek.getValue();
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

    public User getDefaultAssignee() {
        return defaultAssignee;
    }

    public void setDefaultAssignee(User defaultAssignee) {
        this.defaultAssignee = defaultAssignee;
    }

    public WG getWg() {
        return wg;
    }

    public void setWg(WG wg) {
        this.wg = wg;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(int dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public DayOfWeek getDayOfWeekEnum() {
        return DayOfWeek.of(dayOfWeek);
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek.getValue();
    }
}
