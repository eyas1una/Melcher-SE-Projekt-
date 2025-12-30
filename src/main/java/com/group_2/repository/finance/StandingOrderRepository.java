package com.group_2.repository.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.group_2.model.WG;
import com.group_2.model.finance.StandingOrder;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StandingOrderRepository extends JpaRepository<StandingOrder, Long> {

    /**
     * Find all standing orders for a WG
     */
    List<StandingOrder> findByWg(WG wg);

    /**
     * Find all active standing orders that are due (next_execution <= today)
     */
    List<StandingOrder> findByNextExecutionLessThanEqualAndIsActiveTrue(LocalDate date);

    /**
     * Find all active standing orders for a WG
     */
    List<StandingOrder> findByWgAndIsActiveTrue(WG wg);
}
