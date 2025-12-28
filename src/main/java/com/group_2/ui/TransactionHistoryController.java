package com.group_2.ui;

import com.group_2.service.TransactionService;
import com.group_2.util.SessionManager;
import com.model.Transaction;
import com.model.TransactionSplit;
import com.model.User;
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
    private TableView<Transaction> historyTable;
    @FXML
    private TableColumn<Transaction, String> dateColumn;
    @FXML
    private TableColumn<Transaction, String> timeColumn;
    @FXML
    private TableColumn<Transaction, String> descriptionColumn;
    @FXML
    private TableColumn<Transaction, String> amountColumn;
    @FXML
    private TableColumn<Transaction, String> creditorColumn;
    @FXML
    private TableColumn<Transaction, String> debtorColumn;
    @FXML
    private TableColumn<Transaction, Void> actionsColumn;

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

    private DecimalFormat currencyFormat = new DecimalFormat("€#,##0.00");
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    // Master list of all transactions for the current user
    private List<Transaction> allTransactions = new ArrayList<>();

    // Month names for the filter dropdown
    private static final String[] MONTH_NAMES = {
            "All Months", "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    @Autowired
    public TransactionHistoryController(TransactionService transactionService,
            SessionManager sessionManager) {
        this.transactionService = transactionService;
        this.sessionManager = sessionManager;
    }

    @FXML
    public void initialize() {
        // Setup table columns
        dateColumn.setCellValueFactory(cellData -> {
            LocalDateTime timestamp = cellData.getValue().getTimestamp();
            return new SimpleStringProperty(timestamp.format(dateFormatter));
        });

        timeColumn.setCellValueFactory(cellData -> {
            LocalDateTime timestamp = cellData.getValue().getTimestamp();
            return new SimpleStringProperty(timestamp.format(timeFormatter));
        });

        descriptionColumn.setCellValueFactory(cellData -> {
            String desc = cellData.getValue().getDescription();
            return new SimpleStringProperty(desc != null ? desc : "No description");
        });

        amountColumn.setCellValueFactory(cellData -> {
            Double amount = cellData.getValue().getTotalAmount();
            return new SimpleStringProperty(currencyFormat.format(amount));
        });

        // Add styling to amount column based on value
        amountColumn.setCellFactory(column -> new TableCell<Transaction, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    // Color positive amounts in green, keep others default
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    if (transaction.getTotalAmount() > 0) {
                        setStyle("-fx-text-fill: #10b981; -fx-font-weight: 600;");
                    } else {
                        setStyle("-fx-font-weight: 600;");
                    }
                }
            }
        });

        creditorColumn.setCellValueFactory(cellData -> {
            User creditor = cellData.getValue().getCreditor();
            String name = creditor.getName();
            if (creditor.getSurname() != null) {
                name += " " + creditor.getSurname();
            }
            return new SimpleStringProperty(name);
        });

        debtorColumn.setCellValueFactory(cellData -> {
            List<TransactionSplit> splits = cellData.getValue().getSplits();
            if (splits.isEmpty()) {
                return new SimpleStringProperty("None");
            } else if (splits.size() == 1) {
                User debtor = splits.get(0).getDebtor();
                String name = debtor.getName();
                if (debtor.getSurname() != null) {
                    name += " " + debtor.getSurname();
                }
                return new SimpleStringProperty(name);
            } else {
                // Multiple debtors - show name with amount for each
                String debtors = splits.stream()
                        .map(split -> split.getDebtor().getName() + " (" + currencyFormat.format(split.getAmount())
                                + ")")
                        .collect(Collectors.joining(", "));
                if (debtors.length() > 50) {
                    debtors = debtors.substring(0, 47) + "...";
                }
                return new SimpleStringProperty(debtors);
            }
        });

        // Setup actions column with edit and delete buttons
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✏️ Edit");
            private final Button deleteBtn = new Button("🗑️");

            {
                editBtn.setStyle(
                        "-fx-background-color: #dbeafe; -fx-text-fill: #2563eb; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 5 8;");
                editBtn.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    showEditTransactionDialog(transaction);
                });

                deleteBtn.setStyle(
                        "-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 5 8;");
                deleteBtn.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    confirmAndDeleteTransaction(transaction);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    User currentUser = sessionManager.getCurrentUser();

                    // Only show edit/delete for transactions created by current user
                    if (currentUser != null && transaction.getCreatedBy().getId().equals(currentUser.getId())) {
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
            TableRow<Transaction> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Transaction transaction = row.getItem();
                    User currentUser = sessionManager.getCurrentUser();
                    // Only allow edit if current user is the creator
                    if (currentUser != null && transaction.getCreatedBy().getId().equals(currentUser.getId())) {
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
        historyTable.getItems().addListener((javafx.collections.ListChangeListener.Change<? extends Transaction> c) -> {
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
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        // Fetch all transactions for current user
        allTransactions = transactionService.getTransactionsForUser(currentUser.getId());

        // Populate filter dropdowns
        populateFilters();

        // Apply initial filters (current month)
        applyFilters();
    }

    private void populateFilters() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        // Populate year filter from transaction dates
        List<String> yearOptions = new ArrayList<>();
        yearOptions.add("All Years");

        Set<Integer> years = new TreeSet<>();
        for (Transaction t : allTransactions) {
            years.add(t.getTimestamp().getYear());
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

        List<Transaction> filtered = allTransactions.stream()
                .filter(t -> {
                    // Year filter (optional - "All Years" shows all)
                    if (selectedYear != null && !selectedYear.equals("All Years")) {
                        int year = Integer.parseInt(selectedYear);
                        if (t.getTimestamp().getYear() != year) {
                            return false;
                        }
                    }

                    // Month filter (optional - "All Months" shows all)
                    if (selectedMonth != null && !selectedMonth.equals("All Months")) {
                        int monthIndex = java.util.Arrays.asList(MONTH_NAMES).indexOf(selectedMonth);
                        if (monthIndex > 0 && t.getTimestamp().getMonthValue() != monthIndex) {
                            return false;
                        }
                    }

                    // Payer filter
                    if (selectedPayer != null && selectedPayer.getUser() != null) {
                        if (!t.getCreditor().getId().equals(selectedPayer.getUser().getId())) {
                            return false;
                        }
                    }

                    // Debtor filter
                    if (selectedDebtor != null && selectedDebtor.getUser() != null) {
                        boolean hasDebtor = t.getSplits().stream()
                                .anyMatch(s -> s.getDebtor().getId().equals(selectedDebtor.getUser().getId()));
                        if (!hasDebtor) {
                            return false;
                        }
                    }

                    // Description search (case-insensitive partial match)
                    String searchText = searchField.getText();
                    if (searchText != null && !searchText.trim().isEmpty()) {
                        String desc = t.getDescription();
                        if (desc == null || !desc.toLowerCase().contains(searchText.toLowerCase().trim())) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

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
        loadScene(historyTable.getScene(), "/transactions.fxml");
        javafx.application.Platform.runLater(() -> {
            TransactionsController controller = applicationContext.getBean(TransactionsController.class);
            controller.initView();
        });
    }

    private void showEditTransactionDialog(Transaction transaction) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        // Create edit dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Transaction");
        dialog.initOwner(historyTable.getScene().getWindow());

        // Create dialog content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: white;");
        content.setPrefWidth(500);

        // Description field
        Text descLabel = new Text("Description");
        descLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        TextField descField = new TextField(transaction.getDescription());
        descField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        // Total Amount field
        Text amountLabel = new Text("Total Amount (€)");
        amountLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        TextField amountField = new TextField(String.format("%.2f", transaction.getTotalAmount()));
        amountField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        // Creditor info
        Text creditorInfo = new Text("Creditor: " + transaction.getCreditor().getName() +
                (transaction.getCreditor().getSurname() != null ? " " + transaction.getCreditor().getSurname() : ""));
        creditorInfo.setStyle("-fx-font-size: 12px; -fx-fill: #6b7280;");

        // Date info
        Text dateInfo = new Text("Created: " + transaction.getTimestamp().format(dateFormatter) +
                " at " + transaction.getTimestamp().format(timeFormatter));
        dateInfo.setStyle("-fx-font-size: 11px; -fx-fill: #9ca3af;");

        content.getChildren().addAll(descLabel, descField, amountLabel, amountField, creditorInfo, dateInfo);

        // For multiple debtors, show split mode options
        List<TransactionSplit> splits = transaction.getSplits();
        java.util.Map<Long, TextField> splitFields = new java.util.HashMap<>();
        final String[] currentMode = { "AMOUNT" }; // Default mode

        VBox splitsContainer = new VBox(8);
        Text validationLabel = new Text("");

        if (splits.size() > 1) {
            Text splitsLabel = new Text("Split Options");
            splitsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
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

            splitsContainer.setStyle("-fx-background-color: #f9fafb; -fx-padding: 10; -fx-background-radius: 8;");
            content.getChildren().add(splitsContainer);

            validationLabel.setStyle("-fx-font-size: 11px; -fx-fill: #dc2626;");
            content.getChildren().add(validationLabel);

            // Function to rebuild split fields based on mode
            Runnable rebuildSplitFields = () -> {
                splitsContainer.getChildren().clear();
                splitFields.clear();

                double total;
                try {
                    total = Double.parseDouble(amountField.getText().replace(",", "."));
                } catch (NumberFormatException e) {
                    total = transaction.getTotalAmount();
                }

                if (currentMode[0].equals("EQUAL")) {
                    double equalAmount = total / splits.size();
                    for (TransactionSplit split : splits) {
                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        String name = split.getDebtor().getName();
                        if (split.getDebtor().getSurname() != null)
                            name += " " + split.getDebtor().getSurname();
                        Text nameText = new Text(name + ": " + String.format("%.2f", equalAmount) + "€");
                        nameText.setStyle("-fx-font-size: 12px;");
                        row.getChildren().add(nameText);
                        splitsContainer.getChildren().add(row);
                    }
                    validationLabel.setText("✓ Equal split");
                    validationLabel.setStyle("-fx-font-size: 11px; -fx-fill: #16a34a;");
                } else if (currentMode[0].equals("PERCENT")) {
                    for (TransactionSplit split : splits) {
                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        String name = split.getDebtor().getName();
                        if (split.getDebtor().getSurname() != null)
                            name += " " + split.getDebtor().getSurname();
                        Text nameText = new Text(name + ":");
                        nameText.setStyle("-fx-font-size: 12px;");
                        nameText.setWrappingWidth(120);

                        TextField field = new TextField(String.format("%.1f", split.getPercentage()));
                        field.setStyle(
                                "-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 4; -fx-padding: 6;");
                        field.setPrefWidth(70);
                        splitFields.put(split.getDebtor().getId(), field);

                        // Add listener for live validation
                        field.textProperty().addListener((obs, oldVal, newVal) -> {
                            try {
                                double sum = 0;
                                for (TextField f : splitFields.values()) {
                                    sum += Double.parseDouble(f.getText().replace(",", "."));
                                }
                                double remaining = 100.0 - sum;
                                String style;
                                if (Math.abs(remaining) < 0.01) {
                                    style = "-fx-font-size: 11px; -fx-fill: #10b981; -fx-font-weight: 600;";
                                } else if (remaining < 0) {
                                    style = "-fx-font-size: 11px; -fx-fill: #ef4444; -fx-font-weight: 600;";
                                } else {
                                    style = "-fx-font-size: 11px; -fx-fill: #6b7280;";
                                }
                                validationLabel
                                        .setText(String.format("Total: %.1f%% of 100%%\n%.1f%% left", sum, remaining));
                                validationLabel.setStyle(style);
                            } catch (NumberFormatException ex) {
                                validationLabel.setText("⚠ Invalid number");
                                validationLabel.setStyle("-fx-font-size: 11px; -fx-fill: #ef4444;");
                            }
                        });

                        Text percentSign = new Text("%");
                        percentSign.setStyle("-fx-font-size: 12px;");

                        row.getChildren().addAll(nameText, field, percentSign);
                        splitsContainer.getChildren().add(row);
                    }
                    // Initial calculation
                    double sum = splits.stream().mapToDouble(TransactionSplit::getPercentage).sum();
                    double remaining = 100.0 - sum;
                    String style = Math.abs(remaining) < 0.01
                            ? "-fx-font-size: 11px; -fx-fill: #10b981; -fx-font-weight: 600;"
                            : "-fx-font-size: 11px; -fx-fill: #6b7280;";
                    validationLabel.setText(String.format("Total: %.1f%% of 100%%\n%.1f%% left", sum, remaining));
                    validationLabel.setStyle(style);
                } else { // AMOUNT mode
                    for (TransactionSplit split : splits) {
                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        String name = split.getDebtor().getName();
                        if (split.getDebtor().getSurname() != null)
                            name += " " + split.getDebtor().getSurname();
                        Text nameText = new Text(name + ":");
                        nameText.setStyle("-fx-font-size: 12px;");
                        nameText.setWrappingWidth(120);

                        TextField field = new TextField(String.format("%.2f", split.getAmount()));
                        field.setStyle(
                                "-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 4; -fx-padding: 6;");
                        field.setPrefWidth(80);
                        splitFields.put(split.getDebtor().getId(), field);

                        // Add listener for live validation
                        field.textProperty().addListener((obs, oldVal, newVal) -> {
                            try {
                                double totalAmt = Double.parseDouble(amountField.getText().replace(",", "."));
                                double sum = 0;
                                for (TextField f : splitFields.values()) {
                                    sum += Double.parseDouble(f.getText().replace(",", "."));
                                }
                                double remaining = totalAmt - sum;
                                String style;
                                if (Math.abs(remaining) < 0.01) {
                                    style = "-fx-font-size: 11px; -fx-fill: #10b981; -fx-font-weight: 600;";
                                } else if (remaining < 0) {
                                    style = "-fx-font-size: 11px; -fx-fill: #ef4444; -fx-font-weight: 600;";
                                } else {
                                    style = "-fx-font-size: 11px; -fx-fill: #6b7280;";
                                }
                                validationLabel.setText(
                                        String.format("Total: %.2f€ of %.2f€\n%.2f€ left", sum, totalAmt, remaining));
                                validationLabel.setStyle(style);
                            } catch (NumberFormatException ex) {
                                validationLabel.setText("⚠ Invalid number");
                                validationLabel.setStyle("-fx-font-size: 11px; -fx-fill: #ef4444;");
                            }
                        });

                        Text euroSign = new Text("€");
                        euroSign.setStyle("-fx-font-size: 12px;");

                        row.getChildren().addAll(nameText, field, euroSign);
                        splitsContainer.getChildren().add(row);
                    }
                    // Initial calculation
                    double sum = splits.stream().mapToDouble(TransactionSplit::getAmount).sum();
                    double remaining = total - sum;
                    String style = Math.abs(remaining) < 0.01
                            ? "-fx-font-size: 11px; -fx-fill: #10b981; -fx-font-weight: 600;"
                            : "-fx-font-size: 11px; -fx-fill: #6b7280;";
                    validationLabel.setText(String.format("Total: %.2f€ of %.2f€\n%.2f€ left", sum, total, remaining));
                    validationLabel.setStyle(style);
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
        ButtonType saveButton = new ButtonType("💾 Save Changes", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, cancelButton);

        // Style the save button
        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveButton);
        saveBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-background-radius: 8; " +
                "-fx-padding: 10 24; -fx-font-weight: bold; -fx-cursor: hand;");

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
                        for (TransactionSplit split : splits) {
                            debtorIds.add(split.getDebtor().getId());
                            percentages.add(equalPercent);
                        }
                    } else if (currentMode[0].equals("PERCENT")) {
                        // Percentage mode - validate sum = 100%
                        double sum = 0;
                        for (TransactionSplit split : splits) {
                            TextField field = splitFields.get(split.getDebtor().getId());
                            double pct = Double.parseDouble(field.getText().replace(",", "."));
                            sum += pct;
                            debtorIds.add(split.getDebtor().getId());
                            percentages.add(pct);
                        }
                        if (Math.abs(sum - 100.0) > 0.1) {
                            throw new IllegalArgumentException(
                                    String.format("Percentages must sum to 100%% (current: %.1f%%)", sum));
                        }
                    } else {
                        // Amount mode - calculate percentages from amounts
                        double totalSplitAmount = 0;
                        for (TransactionSplit split : splits) {
                            TextField field = splitFields.get(split.getDebtor().getId());
                            totalSplitAmount += Double.parseDouble(field.getText().replace(",", "."));
                        }
                        if (Math.abs(totalSplitAmount - newAmount) > 0.01) {
                            throw new IllegalArgumentException(
                                    String.format("Split amounts (€%.2f) must equal total (€%.2f)", totalSplitAmount,
                                            newAmount));
                        }
                        for (TransactionSplit split : splits) {
                            TextField field = splitFields.get(split.getDebtor().getId());
                            double amount = Double.parseDouble(field.getText().replace(",", "."));
                            debtorIds.add(split.getDebtor().getId());
                            percentages.add((amount / newAmount) * 100.0);
                        }
                    }
                } else {
                    // Single debtor
                    for (TransactionSplit split : splits) {
                        debtorIds.add(split.getDebtor().getId());
                        percentages.add(split.getPercentage());
                    }
                }

                // Update the transaction
                transactionService.updateTransaction(
                        transaction.getId(),
                        currentUser.getId(),
                        transaction.getCreditor().getId(),
                        debtorIds,
                        percentages,
                        newAmount,
                        newDescription);

                // Refresh the view
                initView();

                // Show success
                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.initOwner(historyTable.getScene().getWindow());
                success.setTitle("Success");
                success.setHeaderText(null);
                success.setContentText("Transaction updated successfully.");
                success.showAndWait();

            } catch (NumberFormatException e) {
                Alert error = new Alert(Alert.AlertType.ERROR);
                error.initOwner(historyTable.getScene().getWindow());
                error.setTitle("Error");
                error.setHeaderText("Invalid input");
                error.setContentText("Please enter valid amounts.");
                error.showAndWait();
            } catch (Exception e) {
                Alert error = new Alert(Alert.AlertType.ERROR);
                error.initOwner(historyTable.getScene().getWindow());
                error.setTitle("Error");
                error.setHeaderText("Failed to update transaction");
                error.setContentText(e.getMessage());
                error.showAndWait();
            }
        }
    }

    private void confirmAndDeleteTransaction(Transaction transaction) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(historyTable.getScene().getWindow());
        confirm.setTitle("Delete Transaction");
        confirm.setHeaderText("Are you sure you want to delete this transaction?");
        confirm.setContentText("Transaction: " + transaction.getDescription() +
                "\nAmount: " + currencyFormat.format(transaction.getTotalAmount()) +
                "\n\nThis action cannot be undone and will affect all balances.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                transactionService.deleteTransaction(transaction.getId(), currentUser.getId());

                // Refresh the view
                initView();

                // Show success
                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.initOwner(historyTable.getScene().getWindow());
                success.setTitle("Success");
                success.setHeaderText(null);
                success.setContentText("Transaction deleted successfully.");
                success.showAndWait();

            } catch (Exception e) {
                Alert error = new Alert(Alert.AlertType.ERROR);
                error.initOwner(historyTable.getScene().getWindow());
                error.setTitle("Error");
                error.setHeaderText("Failed to delete transaction");
                error.setContentText(e.getMessage());
                error.showAndWait();
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
