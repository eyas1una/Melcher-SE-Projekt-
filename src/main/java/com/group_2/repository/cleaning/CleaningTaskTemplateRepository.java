package com.group_2.repository.cleaning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.group_2.model.WG;
import com.group_2.model.cleaning.CleaningTaskTemplate;

import java.util.List;

/**
 * Repository for CleaningTaskTemplate entities.
 */
@Repository
public interface CleaningTaskTemplateRepository extends JpaRepository<CleaningTaskTemplate, Long> {

    /**
     * Find all templates for a WG.
     */
    List<CleaningTaskTemplate> findByWg(WG wg);

    /**
     * Find all templates for a WG ordered by day of week.
     */
    List<CleaningTaskTemplate> findByWgOrderByDayOfWeekAsc(WG wg);

    /**
     * Delete all templates for a WG.
     */
    void deleteByWg(WG wg);
}
