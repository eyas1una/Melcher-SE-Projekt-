# Codebase Error Report

## Executive Summary

After a comprehensive scan of the **Melcher WG Management Application** codebase, I identified **25+ distinct issues** across multiple severity levels. The codebase compiles successfully but contains significant architectural, security, and code quality concerns that should be addressed.

---

## 🔴 Critical Issues

### 1. **Complete Absence of Unit Tests**
- **Location**: `/src/test/` directory does not exist
- **Impact**: Zero test coverage for 84 Java source files
- **Risk**: Regressions, uncaught bugs, refactoring fragility
- **Recommendation**: Implement test suite starting with service layer

### 2. **Generic RuntimeException Usage (60+ occurrences)**
- **Files**: All service classes (`TransactionService`, `StandingOrderService`, `WGService`, etc.)
- **Examples**:
  ```java
  throw new RuntimeException("User not found");
  throw new RuntimeException("WG not found");
  throw new RuntimeException("Only the creator can edit it");
  ```
- **Impact**: No distinction between business logic errors and system errors
- **Recommendation**: Create custom exception hierarchy:
  - `EntityNotFoundException`
  - `UnauthorizedOperationException`  
  - `ValidationException`
  - `BusinessRuleViolationException`

### 3. **Potential N+1 Query Problem in Balance Calculations**
- **File**: [TransactionService.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/service/finance/TransactionService.java#L213-L231)
- **Issue**: `calculateAllBalances()` iterates over all WG members and calls `calculateBalanceWithUser()` for each, which fetches all transactions per call
- **Impact**: For 5 members, this executes ~5 * N database operations instead of 1
- **Code**:
  ```java
  for (User member : members) {
      if (!member.getId().equals(currentUserId)) {
          double balance = calculateBalanceWithUser(currentUserId, member.getId());
          // Each call fetches ALL transactions again
      }
  }
  ```

---

## 🟠 High Severity Issues

### 4. **Missing WG Membership Validation in UserService**
- **File**: [UserService.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/service/core/UserService.java#L38-L48)
- **Issue**: `registerUser()` checks for duplicate email by loading ALL users into memory
- **Code**:
  ```java
  if (userRepository.findAll().stream().anyMatch(u -> u.getEmail() != null && u.getEmail().equals(email))) {
      throw new RuntimeException("Email already exists");
  }
  ```
- **Impact**: O(n) memory usage, inefficient for large user bases
- **Fix**: Add `existsByEmail(String email)` method to repository

### 5. **Authentication Loads All Users into Memory**
- **File**: [UserService.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/service/core/UserService.java#L58-L65)
- **Issue**: `authenticate()` filters all users in Java instead of database query
- **Impact**: Security and performance concern; scales poorly

### 6. **EAGER Fetch Strategy Overuse**
- **Files**: All entity models
- **Examples**:
  - `Transaction.java`: All relationships use `FetchType.EAGER`
  - `CleaningTask.java`: All relationships use `FetchType.EAGER`
- **Impact**: Loading a single transaction loads: creditor, createdBy, WG, all splits, all split debtors
- **Recommendation**: Use `LAZY` fetch by default, `@EntityGraph` for specific queries

### 7. **Null Safety Issues in FinanceMapper**
- **File**: [FinanceMapper.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/dto/finance/FinanceMapper.java#L47-L62)
- **Issue**: `toDTO(Transaction)` does not null-check nested objects before calling methods
- **Code**:
  ```java
  return new TransactionDTO(
      transaction.getId(),
      transaction.getCreditor().getId(),  // NPE if creditor is null
      getDisplayName(transaction.getCreditor()),
      transaction.getCreatedBy().getId(), // NPE if createdBy is null
      ...
  );
  ```

### 8. **StandingOrder Debtor Data Stored as JSON String**
- **File**: [StandingOrder.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/model/finance/StandingOrder.java) (referenced in service)
- **Issue**: Debtor IDs and percentages stored as JSON string instead of proper relationship
- **Impact**: 
  - No referential integrity
  - Deleted users remain as "Unknown" in standing orders
  - Cannot query standing orders by debtor

---

## 🟡 Medium Severity Issues

### 9. **Inconsistent `@Transactional` Application**
- **Issue**: Some query-only methods have `@Transactional`, others don't
- **Examples**:
  - `getTasksForWeek()` - has `@Transactional` (line 93)
  - `getActiveStandingOrders()` - no annotation (line 195)
- **Recommendation**: Use class-level `@Transactional(readOnly = true)` by default

### 10. **WG.equals() Based Only on ID**
- **File**: [WG.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/model/WG.java#L132-L147)
- **Issue**: `equals()` relies on ID which is null for unpersisted entities
- **Impact**: Collections may behave unexpectedly before entity is saved

### 11. **SessionManager is Not Thread-Safe**
- **File**: [SessionManager.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/util/SessionManager.java)
- **Issue**: Singleton Spring bean with mutable state and no synchronization
- **Impact**: For a JavaFX desktop app this is likely fine, but architecture suggests web/multi-threaded potential

### 12. **Hardcoded Locale for Currency Formatting**
- **Potential Issue**: No visible locale configuration for `FormatUtils`
- **Impact**: Currency symbols may not match user's region

### 13. **No Input Validation on Transaction Amounts**
- **File**: [TransactionService.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/service/finance/TransactionService.java#L74-L82)
- **Issue**: Validates `totalAmount > 0` but no upper bound or precision check
- **Impact**: Users could enter extremely large or precision-heavy amounts

### 14. **Bidirectional Relationship Not Properly Synced**
- **File**: [WG.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/model/WG.java#L28-L32)
- **Issue**: `@OneToMany` with `CascadeType.MERGE` on rooms/mitbewohner, but User owns the relationship
- **Impact**: Potential for inconsistent state

---

## 🟢 Low Severity / Code Quality Issues

### 15. **Magic Numbers in Code**
- **File**: `StandingOrderService.java` line 280
- **Example**: `100.0 / debtorIds.size()` - percentage calculation should be extracted

### 16. **German/English Mixed Naming**
- **Examples**: `Mitbewohner`, `wg` (Wohngemeinschaft)
- **Recommendation**: Standardize to English for consistency

### 17. **Missing `@NonNull` Annotations**
- **Impact**: IDE/tooling can't warn about null safety
- **Recommendation**: Add JSR-305 or JetBrains annotations

### 18. **Unused ObjectMapper Instances**
- **Files**: Both `StandingOrderService` and `FinanceMapper` create their own `ObjectMapper`
- **Recommendation**: Use Spring's auto-configured `ObjectMapper` bean

### 19. **@Column(nullable = true) on Boolean Fields**
- **File**: [CleaningTask.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/model/cleaning/CleaningTask.java#L57-L58)
- **Issue**: `manualOverride` is nullable `Boolean` then checked with `!= null && manualOverride`
- **Recommendation**: Use primitive `boolean` with default value

### 20. **No Logging in Critical Paths**
- **Issue**: Most service methods lack logging for debugging/audit
- **Recommendation**: Add structured logging for transactions and balance operations

---

## 🔒 Security Concerns

### 21. **No Authorization Checks Beyond Creator**
- **Issue**: Only `createdBy` can edit/delete, but no WG admin overrides
- **Impact**: Admins cannot moderate content

### 22. **Invite Code Predictability**
- **File**: [WG.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/model/WG.java#L51-L59)
- **Issue**: Uses `java.util.Random` instead of `SecureRandom`
- **Impact**: Invite codes could theoretically be predicted

### 23. **Password in User Entity**
- **File**: [User.java](file:///Users/felixraffel/Melcher_Current/Melcher-SE-Projekt-/src/main/java/com/group_2/model/User.java)
- **Issue**: Password stored directly on User entity (hashed, but still exposed via getters)
- **Recommendation**: Consider separate credentials table

---

## 📊 Architecture Observations

### 24. **Circular Dependency Risk**
- **Files**: `WGService` ↔ `CleaningScheduleService`
- **Current Mitigation**: `@Lazy` annotation on injection
- **Issue**: Indicates potential architectural coupling

### 25. **DTO Duplication**
- **Observation**: Two parallel DTO hierarchies (`*DTO` and `*ViewDTO`)
- **Impact**: Increased maintenance burden
- **Recommendation**: Consider consolidating or using projections

---

## Recommendations Priority Matrix

| Priority | Issue                                | Effort | Impact |
| -------- | ------------------------------------ | ------ | ------ |
| 🔴 P0     | Add unit tests                       | High   | High   |
| 🔴 P0     | Fix N+1 query in balances            | Medium | High   |
| 🟠 P1     | Custom exception hierarchy           | Medium | Medium |
| 🟠 P1     | Fix UserService email/auth queries   | Low    | Medium |
| 🟠 P1     | Add null safety to mappers           | Low    | Medium |
| 🟡 P2     | Change EAGER to LAZY fetching        | Medium | Medium |
| 🟡 P2     | Normalize standing order debtor data | High   | Medium |
| 🟢 P3     | Code quality improvements            | Low    | Low    |

---

*Report generated: 2025-12-31*
