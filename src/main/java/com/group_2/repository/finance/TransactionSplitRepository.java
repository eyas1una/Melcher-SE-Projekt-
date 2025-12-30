package com.group_2.repository.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.group_2.model.User;
import com.group_2.model.finance.Transaction;
import com.group_2.model.finance.TransactionSplit;

import java.util.List;

@Repository
public interface TransactionSplitRepository extends JpaRepository<TransactionSplit, Long> {
    List<TransactionSplit> findByDebtor(User debtor);

    List<TransactionSplit> findByTransaction(Transaction transaction);
}
