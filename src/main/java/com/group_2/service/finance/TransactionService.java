package com.group_2.service.finance;

import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.model.finance.Transaction;
import com.group_2.model.finance.TransactionSplit;
import com.group_2.repository.UserRepository;
import com.group_2.repository.WGRepository;
import com.group_2.repository.finance.TransactionRepository;
import com.group_2.repository.finance.TransactionSplitRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.group_2.dto.finance.BalanceDTO;
import com.group_2.dto.finance.FinanceMapper;
import com.group_2.dto.finance.TransactionDTO;
import com.group_2.dto.finance.TransactionViewDTO;
import com.group_2.dto.finance.BalanceViewDTO;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionSplitRepository transactionSplitRepository;
    private final UserRepository userRepository;
    private final WGRepository wgRepository;
    private final FinanceMapper financeMapper;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository,
            TransactionSplitRepository transactionSplitRepository, UserRepository userRepository,
            WGRepository wgRepository, FinanceMapper financeMapper) {
        this.transactionRepository = transactionRepository;
        this.transactionSplitRepository = transactionSplitRepository;
        this.userRepository = userRepository;
        this.wgRepository = wgRepository;
        this.financeMapper = financeMapper;
    }

    /**
     * Create a new transaction with multiple debtors
     * 
     * @param creatorId   ID of the user who is creating this transaction (gets edit
     *                    rights)
     * @param creditorId  ID of the user who paid
     * @param debtorIds   List of user IDs who owe
     * @param percentages List of percentages (0-100) for each debtor, null for
     *                    equal split
     * @param totalAmount Total amount of the transaction
     * @param description Description of what the transaction was for
     * @return The created transaction
     */
    @Transactional
    public Transaction createTransaction(Long creatorId, Long creditorId, List<Long> debtorIds,
            List<Double> percentages, Double totalAmount, String description) {
        // Validate inputs
        if (debtorIds == null || debtorIds.isEmpty()) {
            throw new IllegalArgumentException("At least one debtor is required");
        }
        if (totalAmount == null || totalAmount <= 0) {
            throw new IllegalArgumentException("Total amount must be positive");
        }

        // Fetch entities
        User creator = userRepository.findById(creatorId).orElseThrow(() -> new RuntimeException("Creator not found"));
        User creditor = userRepository.findById(creditorId)
                .orElseThrow(() -> new RuntimeException("Creditor not found"));
        WG wg = creator.getWg();
        if (wg == null) {
            throw new RuntimeException("Creator must be part of a WG");
        }

        // Handle percentages - default to equal split if not provided
        List<Double> finalPercentages = percentages;
        if (percentages == null || percentages.isEmpty()) {
            double equalPercentage = 100.0 / debtorIds.size();
            finalPercentages = debtorIds.stream().map(id -> equalPercentage).toList();
        } else {
            // Validate percentages sum to 100
            double sum = percentages.stream().mapToDouble(Double::doubleValue).sum();
            if (Math.abs(sum - 100.0) > 0.01) {
                throw new IllegalArgumentException("Percentages must sum to 100");
            }
            if (percentages.size() != debtorIds.size()) {
                throw new IllegalArgumentException("Number of percentages must match number of debtors");
            }
        }

        // Create transaction (createdBy is the user who created it, not necessarily the
        // creditor)
        Transaction transaction = new Transaction(creditor, creator, totalAmount, description, wg);
        transaction = transactionRepository.save(transaction);

        // Create splits
        for (int i = 0; i < debtorIds.size(); i++) {
            Long debtorId = debtorIds.get(i);
            Double percentage = finalPercentages.get(i);

            User debtor = userRepository.findById(debtorId)
                    .orElseThrow(() -> new RuntimeException("Debtor not found: " + debtorId));

            double amount = (percentage / 100.0) * totalAmount;
            TransactionSplit split = new TransactionSplit(transaction, debtor, percentage, amount);
            transaction.addSplit(split);
            transactionSplitRepository.save(split);
        }

        return transaction;
    }

    /**
     * Get all transactions for a WG
     */
    public List<Transaction> getTransactionsByWG(Long wgId) {
        WG wg = wgRepository.findById(wgId).orElseThrow(() -> new RuntimeException("WG not found"));
        return transactionRepository.findByWg(wg);
    }

    /**
     * Get all transactions involving a specific user (as creditor or debtor) Sorted
     * by creation date descending (newest first)
     */
    public List<Transaction> getTransactionsForUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        WG wg = user.getWg();
        if (wg == null) {
            return List.of();
        }

        List<Transaction> allTransactions = transactionRepository.findByWg(wg);

        // Filter transactions where user is creditor or appears in splits
        return allTransactions.stream().filter(t -> {
            // User is creditor
            if (t.getCreditor().getId().equals(userId)) {
                return true;
            }
            // User is debtor in any split
            return t.getSplits().stream().anyMatch(split -> split.getDebtor().getId().equals(userId));
        }).sorted((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp())) // Newest first
                .toList();
    }

    /**
     * Calculate cumulative balance between two users Positive = otherUser owes
     * currentUser Negative = currentUser owes otherUser
     */
    public double calculateBalanceWithUser(Long currentUserId, Long otherUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        // Validate other user exists
        userRepository.findById(otherUserId).orElseThrow(() -> new RuntimeException("Other user not found"));

        WG wg = currentUser.getWg();
        if (wg == null) {
            return 0.0;
        }

        List<Transaction> allTransactions = transactionRepository.findByWg(wg);
        double balance = 0.0;

        for (Transaction transaction : allTransactions) {
            // Case 1: Current user is creditor, other user is debtor
            if (transaction.getCreditor().getId().equals(currentUserId)) {
                for (TransactionSplit split : transaction.getSplits()) {
                    if (split.getDebtor().getId().equals(otherUserId)) {
                        balance += split.getAmount(); // Other user owes current user
                    }
                }
            }

            // Case 2: Other user is creditor, current user is debtor
            if (transaction.getCreditor().getId().equals(otherUserId)) {
                for (TransactionSplit split : transaction.getSplits()) {
                    if (split.getDebtor().getId().equals(currentUserId)) {
                        balance -= split.getAmount(); // Current user owes other user
                    }
                }
            }
        }

        return balance;
    }

    /**
     * Calculate balances between current user and all WG members
     * 
     * @return Map of User ID to balance amount
     */
    public Map<Long, Double> calculateAllBalances(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        WG wg = currentUser.getWg();
        if (wg == null || wg.mitbewohner == null) {
            return new HashMap<>();
        }

        Map<Long, Double> balances = new HashMap<>();
        for (User member : wg.mitbewohner) {
            if (!member.getId().equals(currentUserId)) {
                double balance = calculateBalanceWithUser(currentUserId, member.getId());
                balances.put(member.getId(), balance);
            }
        }

        return balances;
    }

    /**
     * Get total net balance for a user (sum of all balances with all members)
     */
    public double getTotalBalance(Long userId) {
        Map<Long, Double> allBalances = calculateAllBalances(userId);
        return allBalances.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Update an existing transaction Only the creditor (creator) can update a
     * transaction
     * 
     * @param transactionId ID of the transaction to update
     * @param currentUserId ID of the user attempting the update (must be the
     *                      creditor)
     * @param newCreditorId New creditor ID (can be different from current)
     * @param debtorIds     Updated list of debtor IDs
     * @param percentages   Updated percentages (null for equal split)
     * @param totalAmount   Updated total amount
     * @param description   Updated description
     * @return The updated transaction
     */
    @Transactional
    public Transaction updateTransaction(Long transactionId, Long currentUserId, Long newCreditorId,
            List<Long> debtorIds, List<Double> percentages, Double totalAmount, String description) {

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // Only the original creator can edit the transaction
        if (!transaction.getCreatedBy().getId().equals(currentUserId)) {
            throw new RuntimeException("Only the creator of the transaction can edit it");
        }

        // Validate inputs
        if (debtorIds == null || debtorIds.isEmpty()) {
            throw new IllegalArgumentException("At least one debtor is required");
        }
        if (totalAmount == null || totalAmount <= 0) {
            throw new IllegalArgumentException("Total amount must be positive");
        }

        // Fetch the new creditor
        User newCreditor = userRepository.findById(newCreditorId)
                .orElseThrow(() -> new RuntimeException("Creditor not found"));

        // Handle percentages - default to equal split if not provided
        List<Double> finalPercentages = percentages;
        if (percentages == null || percentages.isEmpty()) {
            double equalPercentage = 100.0 / debtorIds.size();
            finalPercentages = debtorIds.stream().map(id -> equalPercentage).toList();
        } else {
            // Validate percentages sum to 100
            double sum = percentages.stream().mapToDouble(Double::doubleValue).sum();
            if (Math.abs(sum - 100.0) > 0.01) {
                throw new IllegalArgumentException("Percentages must sum to 100");
            }
            if (percentages.size() != debtorIds.size()) {
                throw new IllegalArgumentException("Number of percentages must match number of debtors");
            }
        }

        // Update transaction fields
        transaction.setCreditor(newCreditor);
        transaction.setTotalAmount(totalAmount);
        transaction.setDescription(description);

        // Clear old splits
        transactionSplitRepository.deleteAll(transaction.getSplits());
        transaction.getSplits().clear();

        // Create new splits
        for (int i = 0; i < debtorIds.size(); i++) {
            Long debtorId = debtorIds.get(i);
            Double percentage = finalPercentages.get(i);

            User debtor = userRepository.findById(debtorId)
                    .orElseThrow(() -> new RuntimeException("Debtor not found: " + debtorId));

            double amount = (percentage / 100.0) * totalAmount;
            TransactionSplit split = new TransactionSplit(transaction, debtor, percentage, amount);
            transaction.addSplit(split);
            transactionSplitRepository.save(split);
        }

        return transactionRepository.save(transaction);
    }

    /**
     * Delete a transaction Only the creditor (creator) can delete a transaction
     * 
     * @param transactionId ID of the transaction to delete
     * @param currentUserId ID of the user attempting the deletion
     */
    @Transactional
    public void deleteTransaction(Long transactionId, Long currentUserId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // Only the original creator can delete the transaction
        if (!transaction.getCreatedBy().getId().equals(currentUserId)) {
            throw new RuntimeException("Only the creator of the transaction can delete it");
        }

        // Delete all splits first (cascade should handle this, but being explicit)
        transactionSplitRepository.deleteAll(transaction.getSplits());

        // Delete the transaction
        transactionRepository.delete(transaction);
    }

    /**
     * Get a transaction by ID
     */
    public Transaction getTransactionById(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
    }

    // ==================== DTO METHODS ====================
    // These methods return DTOs instead of entities for UI consumption

    /**
     * Get all transactions for a user as DTOs
     */
    public List<TransactionDTO> getTransactionsForUserDTO(Long userId) {
        List<Transaction> transactions = getTransactionsForUser(userId);
        return financeMapper.toDTOList(transactions);
    }

    /**
     * Get all transactions for a WG as DTOs
     */
    public List<TransactionDTO> getTransactionsByWGDTO(Long wgId) {
        List<Transaction> transactions = getTransactionsByWG(wgId);
        return financeMapper.toDTOList(transactions);
    }

    /**
     * Get a single transaction by ID as DTO
     */
    public TransactionDTO getTransactionByIdDTO(Long transactionId) {
        Transaction transaction = getTransactionById(transactionId);
        return financeMapper.toDTO(transaction);
    }

    /**
     * Get all transactions for a user as view DTOs with nested user summaries.
     */
    public List<TransactionViewDTO> getTransactionsForUserView(Long userId) {
        List<Transaction> transactions = getTransactionsForUser(userId);
        return financeMapper.toViewList(transactions);
    }

    /**
     * Calculate balances between current user and all WG members as DTOs
     */
    public List<BalanceDTO> calculateAllBalancesDTO(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        WG wg = currentUser.getWg();
        if (wg == null || wg.mitbewohner == null) {
            return List.of();
        }

        return wg.mitbewohner.stream().filter(member -> !member.getId().equals(currentUserId)).map(member -> {
            double balance = calculateBalanceWithUser(currentUserId, member.getId());
            return financeMapper.toBalanceDTO(member, balance);
        }).filter(dto -> dto != null).toList();
    }

    /**
     * Calculate balances between current user and all WG members as view DTOs with
     * nested user summaries.
     */
    public List<BalanceViewDTO> calculateAllBalancesView(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        WG wg = currentUser.getWg();
        if (wg == null || wg.mitbewohner == null) {
            return List.of();
        }

        return wg.mitbewohner.stream().filter(member -> !member.getId().equals(currentUserId)).map(member -> {
            double balance = calculateBalanceWithUser(currentUserId, member.getId());
            return financeMapper.toBalanceView(member, balance);
        }).filter(dto -> dto != null).toList();
    }

    /**
     * Create a transaction and return as DTO
     */
    @Transactional
    public TransactionDTO createTransactionDTO(Long creatorId, Long creditorId, List<Long> debtorIds,
            List<Double> percentages, Double totalAmount, String description) {
        Transaction transaction = createTransaction(creatorId, creditorId, debtorIds, percentages, totalAmount,
                description);
        return financeMapper.toDTO(transaction);
    }

    /**
     * Update a transaction and return as DTO
     */
    @Transactional
    public TransactionDTO updateTransactionDTO(Long transactionId, Long currentUserId, Long newCreditorId,
            List<Long> debtorIds, List<Double> percentages, Double totalAmount, String description) {
        Transaction transaction = updateTransaction(transactionId, currentUserId, newCreditorId, debtorIds, percentages,
                totalAmount, description);
        return financeMapper.toDTO(transaction);
    }

    /**
     * Settle a balance between two users with a single transaction. Validates WG
     * membership and positive amounts.
     */
    @Transactional
    public void settleBalance(Long currentUserId, Long otherUserId, double amount, boolean currentUserPays,
            String paymentMethod) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("Other user not found"));

        WG wg = currentUser.getWg();
        if (wg == null || otherUser.getWg() == null || !wg.getId().equals(otherUser.getWg().getId())) {
            throw new RuntimeException("Users must belong to the same WG to settle balances");
        }

        Long payerId = currentUserPays ? currentUserId : otherUserId;
        Long debtorId = currentUserPays ? otherUserId : currentUserId;

        String description = "Settlement" + (paymentMethod != null ? " via " + paymentMethod : "");

        createTransactionDTO(currentUserId, payerId, List.of(debtorId), null, amount, description);
    }

    /**
     * Execute a credit transfer: a third roommate's positive balance with the
     * current user is used to settle a debt to another roommate.
     */
    @Transactional
    public void transferCredit(Long currentUserId, Long creditSourceUserId, Long debtorToUserId, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User creditSource = userRepository.findById(creditSourceUserId)
                .orElseThrow(() -> new RuntimeException("Credit source user not found"));
        User debtorTo = userRepository.findById(debtorToUserId)
                .orElseThrow(() -> new RuntimeException("Debtor user not found"));

        WG wg = currentUser.getWg();
        if (wg == null || creditSource.getWg() == null || debtorTo.getWg() == null
                || !wg.getId().equals(creditSource.getWg().getId()) || !wg.getId().equals(debtorTo.getWg().getId())) {
            throw new RuntimeException("All users must belong to the same WG for credit transfer");
        }

        // Transaction 1: current user settles debt with debtorTo
        createTransactionDTO(currentUserId, currentUserId, List.of(debtorToUserId), null, amount,
                "Settlement via Credit Transfer (settled debt)");

        // Transaction 2: credit source settles their debt with current user
        createTransactionDTO(currentUserId, creditSourceUserId, List.of(currentUserId), null, amount,
                "Settlement via Credit Transfer (used credit)");
    }
}
