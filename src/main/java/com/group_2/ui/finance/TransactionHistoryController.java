package com.group_2.ui.finance;

import com.group_2.dto.finance.TransactionViewDTO;
import com.group_2.dto.finance.TransactionSplitViewDTO;
import com.group_2.model.User;
import com.group_2.service.finance.TransactionService;
import com.group_2.ui.core.Controller;
import com.group_2.util.SessionManager;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
public class TransactionHistoryController extends Controller {

    private final TransactionService transactionService;
    private final SessionManager sessionManager;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @FXML
    private Text transactionCountText;
    @FXML
    private TableView<TransactionViewDTO> historyTable;
    @FXML
    private TableColumn<TransactionViewDTO, String> dateColumn;
    @FXML
    private TableColumn<TransactionViewDTO, String> timeColumn;
    @FXML
    private TableColumn<TransactionViewDTO, String> descriptionColumn;
    @FXML
    private TableColumn<TransactionViewDTO, String> amountColumn;
    @FXML
    private TableColumn<TransactionViewDTO, String> creditorColumn;
    @FXML
    private TableColumn<TransactionViewDTO, String> debtorColumn;
    @FXML
    private TableColumn<TransactionViewDTO, Void> actionsColumn;

    // Filter controls
    @FXML
    private ComboBox<String> yearFilter;
    @FXML
    private ComboBox<String> monthFilter;
    @FXML
    private ComboBox<UserDisplay> payerFilter;
    @FXML
    private ComboBox<UserDisplay> debtorFilter;
    @FXML
    private TextField searchField;

    private DecimalFormat currencyFormat = new DecimalFormat("EUR #,##0.00");
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    // Master list of all transactions for the current user
    private List<TransactionViewDTO> allTransactions = new ArrayList<>();

    // Month names for the filter dropdown
    private static final String[] MONTH_NAMES = { "All Months", "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December" };

    @Autowired
    public TransactionHistoryController(TransactionService transactionService, SessionManager sessionManager) {
        this.transactionService = transactionService;
        this.sessionManager = sessionManager;
    }

    @FXML
    public void initialize() {
        // Setup table columns
        dateColumn.setCellValueFactory(cellData -> {
            LocalDateTime timestamp = cellData.getValue().timestamp();
            return new SimpleStringProperty(timestamp.format(dateFormatter));
        });

        timeColumn.setCellValueFactory(cellData -> {
            LocalDateTime timestamp = cellData.getValue().timestamp();
            return new SimpleStringProperty(timestamp.format(timeFormatter));
        });

        descriptionColumn.setCellValueFactory(cellData -> {
            String desc = cellData.getValue().description();
            return new SimpleStringProperty(desc != null ? desc : "No description");
        });

        amountColumn.setCellValueFactory(cellData -> {
            Double amount = cellData.getValue().totalAmount();
            return new SimpleStringProperty(currencyFormat.format(amount));
        });

        // Add styling to amount column based on value
        amountColumn.setCellFactory(column -> new TableCell<TransactionViewDTO, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("amount-positive", "amount-default");
                } else {
                    setText(item);
                    // Color positive amounts in green, keep others default
                    TransactionViewDTO transaction = getTableView().getItems().get(getIndex());
                    getStyleClass().removeAll("amount-positive", "amount-default");
                    if (transaction.totalAmount() > 0) {
                        getStyleClass().add("amount-positive");
                    } else {
                        getStyleClass().add("amount-default");
                    }
                }
            }
        });

        creditorColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().creditor() != null ? cellData.getValue().creditor().displayName() : "Unknown"));

        debtorColumn.setCellValueFactory(cellData -> {
            List<TransactionSplitViewDTO> splits = cellData.getValue().splits();
            if (splits.isEmpty()) {
                return new SimpleStringProperty("None");
            } else if (splits.size() == 1) {
                return new SimpleStringProperty(
                        splits.get(0).debtor() != null ? splits.get(0).debtor().displayName() : "Unknown");
            } else {
                // Multiple debtors - show name with amount for each
                String debtors = splits.stream()
                        .map(split -> {
                            String name = split.debtor() != null ? split.debtor().displayName() : "Unknown";
                            return name + " (" + currencyFormat.format(split.amount()) + ")";
                        })
                        .collect(Collectors.joining(", "));
                if (debtors.length() > 50) {
                    debtors = debtors.substring(0, 47) + "...";
                }
                return new SimpleStringProperty(debtors);
            }
        });

        // Setup actions column with edit and delete buttons
        actionsColumn.setCellFactory(col -> new TableCell<TransactionViewDTO, Void>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");

            {
                editBtn.getStyleClass().addAll("table-action-button", "table-edit-button");
                editBtn.setOnAction(e -> {
                    TransactionViewDTO transaction = getTableView().getItems().get(getIndex());
                    showEditTransactionDialog(transaction);
                });

                deleteBtn.getStyleClass().addAll("table-action-button", "table-delete-button");
                deleteBtn.setOnAction(e -> {
                    TransactionViewDTO transaction = getTableView().getItems().get(getIndex());
                    confirmAndDeleteTransaction(transaction);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    TransactionViewDTO transaction = getTableView().getItems().get(getIndex());
                    Long currentUserId = sessionManager.getCurrentUserId();

                    // Only show edit/delete for transactions created by current user
                    if (currentUserId != null && transaction.createdBy() != null
                            && transaction.createdBy().id().equals(currentUserId)) {
                        HBox buttons = new HBox(5, editBtn, deleteBtn);
                        setGraphic(buttons);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        // Setup row factory for double-click edit
        historyTable.setRowFactory(tv -> {
            TableRow<TransactionViewDTO> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    TransactionViewDTO transaction = row.getItem();
                    Long currentUserId = sessionManager.getCurrentUserId();
                    // Only allow edit if current user is the creator
                    if (currentUserId != null && transaction.createdBy() != null
                            && transaction.createdBy().id().equals(currentUserId)) {
                        showEditTransactionDialog(transaction);
                    }
                }
            });
            return row;
        });

        // Configure table to remove placeholder rows and set proper sizing
        historyTable.setPlaceholder(new Text("No transactions found"));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        historyTable.setFixedCellSize(52); // Fixed row height for consistency

        // Add listener to dynamically size table based on number of items
        historyTable.getItems()
                .addListener((javafx.collections.ListChangeListener.Change<? extends TransactionViewDTO> c) -> {
                    updateHistoryTableHeight();
                });

        // Load data
        initView();
    }

    private void updateHistoryTableHeight() {
        int rowCount = historyTable.getItems().size();
        if (rowCount == 0) {
            // Show placeholder area
            historyTable.setPrefHeight(200);
            historyTable.setMaxHeight(200);
        } else {
            // Calculate exact height: header (45px) + rows (52px each) + small buffer
            double height = 45 + (rowCount * 52) + 2;
            historyTable.setPrefHeight(height);
            historyTable.setMaxHeight(height);
        }
    }

    public void initView() {
        Long currentUserId = sessionManager.getCurrentUserId();
        if (currentUserId == null) {
            return;
        }

        // Fetch all transactions for current user
        allTransactions = transactionService.getTransactionsForUserView(currentUserId);

        // Populate filter dropdowns
        populateFilters();

        // Apply initial filters (current month)
        applyFilters();
    }

    private void populateFilters() {
        Long currentUserId = sessionManager.getCurrentUserId();
        if (currentUserId == null)
            return;

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        // Populate year filter from transaction dates
        List<String> yearOptions = new ArrayList<>();
        yearOptions.add("All Years");

        Set<Integer> years = new TreeSet<>();
        for (TransactionViewDTO t : allTransactions) {
            years.add(t.timestamp().getYear());
        }
        // Always include current year
        int currentYear = Year.now().getValue();
        years.add(currentYear);

        // Add years in descending order (newest first)
        List<Integer> sortedYears = new ArrayList<>(years);
        sortedYears.sort((a, b) -> b - a); // Descending
        for (Integer year : sortedYears) {
            yearOptions.add(String.valueOf(year));
        }

        yearFilter.setItems(FXCollections.observableArrayList(yearOptions));
        yearFilter.setValue(String.valueOf(currentYear)); // Default to current year
        yearFilter.setOnAction(e -> applyFilters());

        // Populate month filter
        monthFilter.setItems(FXCollections.observableArrayList(MONTH_NAMES));
        int currentMonth = LocalDateTime.now().getMonthValue(); // 1-12
        monthFilter.setValue(MONTH_NAMES[currentMonth]); // Default to current month
        monthFilter.setOnAction(e -> applyFilters());

        // Populate payer/debtor filters from WG members
        List<UserDisplay> members = new ArrayList<>();
        members.add(new UserDisplay(null, "All")); // "All" option

        if (currentUser.getWg() != null && currentUser.getWg().getMitbewohner() != null) {
            for (User member : currentUser.getWg().getMitbewohner()) {
                String name = member.getName();
                if (member.getSurname() != null) {
                    name += " " + member.getSurname();
                }
                members.add(new UserDisplay(member, name));
            }
        }

        payerFilter.setItems(FXCollections.observableArrayList(members));
        payerFilter.setValue(members.get(0)); // "All"
        payerFilter.setOnAction(e -> applyFilters());

        debtorFilter.setItems(FXCollections.observableArrayList(members));
        debtorFilter.setValue(members.get(0)); // "All"
        debtorFilter.setOnAction(e -> applyFilters());

        // Setup search field listener (triggers on every keystroke)
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void applyFilters() {
        String selectedYear = yearFilter.getValue();
        String selectedMonth = monthFilter.getValue();
        UserDisplay selectedPayer = payerFilter.getValue();
        UserDisplay selectedDebtor = debtorFilter.getValue();

        List<TransactionViewDTO> filtered = allTransactions.stream().filter(t -> {
            // Year filter (optional - "All Years" shows all)
            if (selectedYear != null && !selectedYear.equals("All Years")) {
                int year = Integer.parseInt(selectedYear);
                if (t.timestamp().getYear() != year) {
                    return false;
                }
            }

            // Month filter (optional - "All Months" shows all)
            if (selectedMonth != null && !selectedMonth.equals("All Months")) {
                int monthIndex = java.util.Arrays.asList(MONTH_NAMES).indexOf(selectedMonth);
                if (monthIndex > 0 && t.timestamp().getMonthValue() != monthIndex) {
                    return false;
                }
            }

            // Payer filter
            if (selectedPayer != null && selectedPayer.getUser() != null) {
                if (t.creditor() == null || !t.creditor().id().equals(selectedPayer.getUser().getId())) {
                    return false;
                }
            }

            // Debtor filter
            if (selectedDebtor != null && selectedDebtor.getUser() != null) {
                boolean hasDebtor = t.splits().stream()
                        .anyMatch(s -> s.debtor() != null && s.debtor().id().equals(selectedDebtor.getUser().getId()));
                if (!hasDebtor) {
                    return false;
                }
            }

            // Description search (case-insensitive partial match)
            String searchText = searchField.getText();
            if (searchText != null && !searchText.trim().isEmpty()) {
                String desc = t.description();
                if (desc == null || !desc.toLowerCase().contains(searchText.toLowerCase().trim())) {
                    return false;
                }
            }

            return true;
        }).collect(Collectors.toList());

        // Update count text
        transactionCountText.setText(String.valueOf(filtered.size()));

        // Populate table
        historyTable.setItems(FXCollections.observableArrayList(filtered));

        // Update table height
        updateHistoryTableHeight();
    }

    @FXML
    public void clearFilters() {
        // Reset to show all transactions (All Years, All Months, All Payers, All
        // Debtors)
        yearFilter.setValue("All Years");
        monthFilter.setValue("All Months");
        searchField.clear();

        // Reset member filters to "All"
        if (!payerFilter.getItems().isEmpty()) {
            payerFilter.setValue(payerFilter.getItems().get(0));
        }
        if (!debtorFilter.getItems().isEmpty()) {
            debtorFilter.setValue(debtorFilter.getItems().get(0));
        }

        applyFilters();
    }

    @FXML
    public void returnToTransactions() {
        loadScene(historyTable.getScene(), "/finance/transactions.fxml");
        javafx.application.Platform.runLater(() -> {
            TransactionsController controller = applicationContext.getBean(TransactionsController.class);
            controller.initView();
        });
    }

    private void showEditTransactionDialog(TransactionViewDTO transaction) {
        Long currentUserId = sessionManager.getCurrentUserId();
        if (currentUserId == null)
            return;

        // Create edit dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Transaction");
        dialog.initOwner(historyTable.getScene().getWindow());
        String stylesheet = getClass().getResource("/css/styles.css").toExternalForm();
        if (!dialog.getDialogPane().getStylesheets().contains(stylesheet)) {
            dialog.getDialogPane().getStylesheets().add(stylesheet);
        }

        // Create dialog content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("dialog-content");
        content.setPrefWidth(500);

        // Description field
        Text descLabel = new Text("Description");
        descLabel.getStyleClass().add("form-label-bold");
        TextField descField = new TextField(transaction.description());
        descField.getStyleClass().addAll("dialog-field", "dialog-field-small");

        // Total Amount field
        Text amountLabel = new Text("Total Amount (EUR)");
        amountLabel.getStyleClass().add("form-label-bold");
        TextField amountField = new TextField(String.format("%.2f", transaction.totalAmount()));
        amountField.getStyleClass().addAll("dialog-field", "dialog-field-small");

        // Creditor info
        String creditorName = transaction.creditor() != null ? transaction.creditor().displayName() : "Unknown";
        Text creditorInfo = new Text("Creditor: " + creditorName);
        creditorInfo.getStyleClass().add("dialog-label-secondary");

        // Date info
        Text dateInfo = new Text("Created: " + transaction.timestamp().format(dateFormatter) + " at "
                + transaction.timestamp().format(timeFormatter));
        dateInfo.getStyleClass().add("info-text-muted");

        content.getChildren().addAll(descLabel, descField, amountLabel, amountField, creditorInfo, dateInfo);

        // For multiple debtors, show split mode options
        List<TransactionSplitViewDTO> splits = transaction.splits();
        java.util.Map<Long, TextField> splitFields = new java.util.HashMap<>();
        final String[] currentMode = { "AMOUNT" }; // Default mode

        VBox splitsContainer = new VBox(8);
        Text validationLabel = new Text("");

        if (splits.size() > 1) {
            Text splitsLabel = new Text("Split Options");
            splitsLabel.getStyleClass().add("form-label-bold");
            content.getChildren().add(splitsLabel);

            // Mode selector
            HBox modeSelector = new HBox(10);
            modeSelector.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            ToggleGroup modeGroup = new ToggleGroup();
            RadioButton equalBtn = new RadioButton("Equal");
            equalBtn.setToggleGroup(modeGroup);
            RadioButton percentBtn = new RadioButton("Percentage");
            percentBtn.setToggleGroup(modeGroup);
            RadioButton amountBtn = new RadioButton("Custom Amount");
            amountBtn.setToggleGroup(modeGroup);
            amountBtn.setSelected(true); // Default to amount mode

            modeSelector.getChildren().addAll(equalBtn, percentBtn, amountBtn);
            content.getChildren().add(modeSelector);

            splitsContainer.getStyleClass().add("splits-container");
            content.getChildren().add(splitsContainer);

            validationLabel.getStyleClass().add("validation-label");
            content.getChildren().add(validationLabel);

            // Function to rebuild split fields based on mode
            Runnable rebuildSplitFields = () -> {
                splitsContainer.getChildren().clear();
                splitFields.clear();

                double total;
                try {
                    total = Double.parseDouble(amountField.getText().replace(",", "."));
                } catch (NumberFormatException e) {
                    total = transaction.totalAmount();
                }

                if (currentMode[0].equals("EQUAL")) {
                    double equalAmount = total / splits.size();
                    for (TransactionSplitViewDTO split : splits) {
                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        String debtorName = split.debtor() != null ? split.debtor().displayName() : "Unknown";
                        Text nameText = new Text(debtorName + ": " + String.format("%.2f", equalAmount) + " EUR");
                        nameText.getStyleClass().add("text-small");
                        row.getChildren().add(nameText);
                        splitsContainer.getChildren().add(row);
                    }
                    validationLabel.setText("Equal split");
                    validationLabel.getStyleClass().removeAll("validation-label-error", "validation-label-muted",
                            "validation-label-success");
                    validationLabel.getStyleClass().addAll("validation-label", "validation-label-success");
                } else if (currentMode[0].equals("PERCENT")) {
                    for (TransactionSplitViewDTO split : splits) {
                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        String debtorName = split.debtor() != null ? split.debtor().displayName() : "Unknown";
                        Text nameText = new Text(debtorName + ":");
                        nameText.getStyleClass().add("text-small");
                        nameText.setWrappingWidth(120);

                        TextField field = new TextField(String.format("%.1f", split.percentage()));
                        field.getStyleClass().add("input-compact");
                        field.setPrefWidth(70);
                        if (split.debtor() != null) {
                            splitFields.put(split.debtor().id(), field);
                        }

                        // Add listener for live validation
                        field.textProperty().addListener((obs, oldVal, newVal) -> {
                            try {
                                double sum = 0;
                                for (TextField f : splitFields.values()) {
                                    sum += Double.parseDouble(f.getText().replace(",", "."));
                                }
                                double remaining = 100.0 - sum;
                                validationLabel.getStyleClass().removeAll("validation-label-success",
                                        "validation-label-error", "validation-label-muted");
                                if (Math.abs(remaining) < 0.01) {
                                    validationLabel.getStyleClass().add("validation-label-success");
                                } else if (remaining < 0) {
                                    validationLabel.getStyleClass().add("validation-label-error");
                                } else {
                                    validationLabel.getStyleClass().add("validation-label-muted");
                                }
                                validationLabel
                                        .setText(String.format("Total: %.1f%% of 100%%\n%.1f%% left", sum, remaining));
                                if (!validationLabel.getStyleClass().contains("validation-label")) {
                                    validationLabel.getStyleClass().add("validation-label");
                                }
                            } catch (NumberFormatException ex) {
                                validationLabel.setText("Invalid number");
                                validationLabel.getStyleClass().removeAll("validation-label-success",
                                        "validation-label-muted");
                                validationLabel.getStyleClass().addAll("validation-label", "validation-label-error");
                            }
                        });

                        Text percentSign = new Text("%");
                        percentSign.getStyleClass().add("unit-sign");

                        row.getChildren().addAll(nameText, field, percentSign);
                        splitsContainer.getChildren().add(row);
                    }
                    // Initial calculation
                    double sum = splits.stream().mapToDouble(TransactionSplitViewDTO::percentage).sum();
                    double remaining = 100.0 - sum;
                    validationLabel.setText(String.format("Total: %.2f EUR of %.2f EUR\n%.2f EUR left", sum, total, remaining));
                    validationLabel.getStyleClass().removeAll("validation-label-success", "validation-label-error", "validation-label-muted");
                    if (Math.abs(remaining) < 0.01) {
                        validationLabel.getStyleClass().addAll("validation-label", "validation-label-success");
                    } else {
                        validationLabel.getStyleClass().addAll("validation-label", "validation-label-muted");
                    }
                }
            };

            // Mode change listeners
            equalBtn.setOnAction(e -> {
                currentMode[0] = "EQUAL";
                rebuildSplitFields.run();
            });
            percentBtn.setOnAction(e -> {
                currentMode[0] = "PERCENT";
                rebuildSplitFields.run();
            });
            amountBtn.setOnAction(e -> {
                currentMode[0] = "AMOUNT";
                rebuildSplitFields.run();
            });

            // Initial build
            rebuildSplitFields.run();
        }

        dialog.getDialogPane().setContent(content);

        // Add buttons
        ButtonType saveButton = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, cancelButton);

        // Style the save button
        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveButton);
        saveBtn.getStyleClass().addAll("confirm-button", "confirm-button-success");

        // Handle result
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveButton) {
            try {
                String newDescription = descField.getText().trim();
                double newAmount = Double.parseDouble(amountField.getText().replace(",", "."));

                if (newAmount <= 0) {
                    throw new IllegalArgumentException("Amount must be positive");
                }

                List<Long> debtorIds = new ArrayList<>();
                List<Double> percentages = new ArrayList<>();

                if (splits.size() > 1) {
                    if (currentMode[0].equals("EQUAL")) {
                        // Equal split
                        double equalPercent = 100.0 / splits.size();
                        for (TransactionSplitViewDTO split : splits) {
                            if (split.debtor() != null) {
                                debtorIds.add(split.debtor().id());
                                percentages.add(equalPercent);
                            }
                        }
                    } else if (currentMode[0].equals("PERCENT")) {
                        // Percentage mode - validate sum = 100%
                        double sum = 0;
                        for (TransactionSplitViewDTO split : splits) {
                            if (split.debtor() != null) {
                                TextField field = splitFields.get(split.debtor().id());
                                double pct = Double.parseDouble(field.getText().replace(",", "."));
                                sum += pct;
                                debtorIds.add(split.debtor().id());
                                percentages.add(pct);
                            }
                        }
                        if (Math.abs(sum - 100.0) > 0.1) {
                            throw new IllegalArgumentException(
                                    String.format("Percentages must sum to 100%% (current: %.1f%%)", sum));
                        }
                    } else {
                        // Amount mode - calculate percentages from amounts
                        double totalSplitAmount = 0;
                        for (TransactionSplitViewDTO split : splits) {
                            if (split.debtor() != null) {
                                TextField field = splitFields.get(split.debtor().id());
                                totalSplitAmount += Double.parseDouble(field.getText().replace(",", "."));
                            }
                        }
                        if (Math.abs(totalSplitAmount - newAmount) > 0.01) {
                            throw new IllegalArgumentException(String.format(
                                    "Split amounts (EUR %.2f) must equal total (EUR %.2f)", totalSplitAmount,
                                    newAmount));
                        }
                        for (TransactionSplitViewDTO split : splits) {
                            if (split.debtor() != null) {
                                TextField field = splitFields.get(split.debtor().id());
                                double amount = Double.parseDouble(field.getText().replace(",", "."));
                                debtorIds.add(split.debtor().id());
                                percentages.add((amount / newAmount) * 100.0);
                            }
                        }
                    }
                } else {
                    // Single debtor
                    for (TransactionSplitViewDTO split : splits) {
                        if (split.debtor() != null) {
                            debtorIds.add(split.debtor().id());
                            percentages.add(split.percentage());
                        }
                    }
                }

                // Update the transaction
                transactionService.updateTransactionDTO(transaction.id(), currentUserId, transaction.creditor().id(),
                        debtorIds, percentages, newAmount, newDescription);

                // Refresh the view
                initView();

                // Show success
                showSuccessAlert("Success", "Transaction updated successfully.", historyTable.getScene().getWindow());

            } catch (NumberFormatException e) {
                showErrorAlert("Invalid input", "Please enter valid amounts.", historyTable.getScene().getWindow());
            } catch (Exception e) {
                showErrorAlert("Failed to update transaction", e.getMessage(), historyTable.getScene().getWindow());
            }
        }
    }

    private void confirmAndDeleteTransaction(TransactionViewDTO transaction) {
        Long currentUserId = sessionManager.getCurrentUserId();
        if (currentUserId == null)
            return;

        String message = "Transaction: " + transaction.description() + "\nAmount: "
                + currencyFormat.format(transaction.totalAmount())
                + "\n\nThis action cannot be undone and will affect all balances.";

        boolean confirmed = showConfirmDialog("Delete Transaction", "Are you sure you want to delete this transaction?",
                message, historyTable.getScene().getWindow());

        if (confirmed) {
            try {
                transactionService.deleteTransaction(transaction.id(), currentUserId);

                // Refresh the view
                initView();

                // Show success
                showSuccessAlert("Success", "Transaction deleted successfully.", historyTable.getScene().getWindow());

            } catch (Exception e) {
                showErrorAlert("Failed to delete transaction", e.getMessage(), historyTable.getScene().getWindow());
            }
        }
    }

    // Helper class for displaying users in ComboBox
    private static class UserDisplay {
        private final User user;
        private final String displayName;

        public UserDisplay(User user, String displayName) {
            this.user = user;
            this.displayName = displayName;
        }

        public User getUser() {
            return user;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}



