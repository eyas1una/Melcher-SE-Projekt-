package com.group_2.repository;

import com.model.Transaction;
import com.model.WG;
import com.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByWg(WG wg);

    List<Transaction> findByCreditor(User creditor);
}
