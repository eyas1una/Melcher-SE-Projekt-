package com.group_2.model.finance;

import com.group_2.model.User;
import jakarta.persistence.*;

@Entity
@Table(name = "transaction_splits")
public class TransactionSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "debtor_id", nullable = false)
    private User debtor;

    @Column(nullable = false)
    private Double percentage;

    @Column(nullable = false)
    private Double amount;

    public TransactionSplit() {
    }

    public TransactionSplit(Transaction transaction, User debtor, Double percentage, Double amount) {
        this.transaction = transaction;
        this.debtor = debtor;
        this.percentage = percentage;
        this.amount = amount;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public User getDebtor() {
        return debtor;
    }

    public void setDebtor(User debtor) {
        this.debtor = debtor;
    }

    public Double getPercentage() {
        return percentage;
    }

    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }
}
