package com.group_2.repository.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.model.finance.Transaction;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByWg(WG wg);

    List<Transaction> findByCreditor(User creditor);
}
