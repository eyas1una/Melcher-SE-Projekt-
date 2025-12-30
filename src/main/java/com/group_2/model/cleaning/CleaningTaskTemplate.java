package com.group_2.model.cleaning;

import com.group_2.model.WG;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Entity representing a template for cleaning tasks.
 * Templates define a default weekly schedule that can be applied to new weeks.
 * Each template specifies a room and day of week. Assignees are determined
 * by the RoomAssignmentQueue for round-robin distribution.
 */
@Entity
@Table(name = "task_template")
public class CleaningTaskTemplate {

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
     * The day of the week this task should be scheduled on (1=Monday, 7=Sunday).
     */
    @Column(nullable = false)
    private int dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private RecurrenceInterval recurrenceInterval = RecurrenceInterval.WEEKLY;

    /**
     * The start of the week this template was anchored to.
     * Used to calculate cycles for bi-weekly and monthly tasks.
     */
    @Column(nullable = true)
    private LocalDate baseWeekStart;

    public CleaningTaskTemplate() {
        this.baseWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public CleaningTaskTemplate(Room room, WG wg, int dayOfWeek) {
        this.room = room;
        this.wg = wg;
        this.dayOfWeek = dayOfWeek;
        this.recurrenceInterval = RecurrenceInterval.WEEKLY;
        this.baseWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public CleaningTaskTemplate(Room room, WG wg, DayOfWeek dayOfWeek) {
        this(room, wg, dayOfWeek.getValue());
    }

    public CleaningTaskTemplate(Room room, WG wg, DayOfWeek dayOfWeek, RecurrenceInterval interval,
            LocalDate weekStart) {
        this.room = room;
        this.wg = wg;
        this.dayOfWeek = dayOfWeek.getValue();
        this.recurrenceInterval = interval;
        this.baseWeekStart = weekStart;
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

    public RecurrenceInterval getRecurrenceInterval() {
        return recurrenceInterval;
    }

    public void setRecurrenceInterval(RecurrenceInterval recurrenceInterval) {
        this.recurrenceInterval = recurrenceInterval;
    }

    public LocalDate getBaseWeekStart() {
        return baseWeekStart;
    }

    public void setBaseWeekStart(LocalDate baseWeekStart) {
        this.baseWeekStart = baseWeekStart;
    }

    /**
     * Ensures that new fields have default values when loading existing records
     * from the database that were created before these fields were added.
     */
    @PostLoad
    private void fillDefaults() {
        if (recurrenceInterval == null) {
            recurrenceInterval = RecurrenceInterval.WEEKLY;
        }
        if (baseWeekStart == null) {
            baseWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
    }
}
