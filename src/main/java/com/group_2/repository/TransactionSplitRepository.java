package com.group_2.repository;

import com.model.TransactionSplit;
import com.model.Transaction;
import com.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionSplitRepository extends JpaRepository<TransactionSplit, Long> {
    List<TransactionSplit> findByDebtor(User debtor);

    List<TransactionSplit> findByTransaction(Transaction transaction);
}
