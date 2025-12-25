package com.group_2.repository;

import com.model.CleaningTask;
import com.model.User;
import com.model.WG;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for CleaningTask entities.
 */
@Repository
public interface CleaningTaskRepository extends JpaRepository<CleaningTask, Long> {

    /**
     * Find all cleaning tasks for a WG for a specific week.
     */
    List<CleaningTask> findByWgAndWeekStartDate(WG wg, LocalDate weekStartDate);

    /**
     * Find all cleaning tasks assigned to a user for a specific week.
     */
    List<CleaningTask> findByAssigneeAndWeekStartDate(User assignee, LocalDate weekStartDate);

    /**
     * Find all cleaning tasks for a WG (all weeks).
     */
    List<CleaningTask> findByWg(WG wg);

    /**
     * Find all cleaning tasks for a WG ordered by week descending.
     */
    List<CleaningTask> findByWgOrderByWeekStartDateDesc(WG wg);
}
