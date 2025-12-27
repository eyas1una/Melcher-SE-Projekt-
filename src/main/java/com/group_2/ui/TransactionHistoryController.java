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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private DecimalFormat currencyFormat = new DecimalFormat("€#,##0.00");
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

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
                // Multiple debtors
                String debtors = splits.stream()
                        .map(split -> split.getDebtor().getName())
                        .collect(Collectors.joining(", "));
                if (debtors.length() > 30) {
                    debtors = debtors.substring(0, 27) + "...";
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

        // Fetch transactions for current user
        List<Transaction> transactions = transactionService.getTransactionsForUser(currentUser.getId());

        // Update count text
        transactionCountText.setText(String.format("Showing %d transaction%s involving you",
                transactions.size(), transactions.size() == 1 ? "" : "s"));

        // Populate table
        historyTable.setItems(FXCollections.observableArrayList(transactions));

        // Update table height to fit content
        updateHistoryTableHeight();
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
        content.setPrefWidth(450);

        // Description field
        Text descLabel = new Text("Description");
        descLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        TextField descField = new TextField(transaction.getDescription());
        descField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        // Amount field
        Text amountLabel = new Text("Amount (€)");
        amountLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        TextField amountField = new TextField(String.format("%.2f", transaction.getTotalAmount()));
        amountField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        // Current info display
        Text creditorInfo = new Text("Creditor: " + transaction.getCreditor().getName() +
                (transaction.getCreditor().getSurname() != null ? " " + transaction.getCreditor().getSurname() : ""));
        creditorInfo.setStyle("-fx-font-size: 12px; -fx-fill: #6b7280;");

        String debtorNames = transaction.getSplits().stream()
                .map(s -> s.getDebtor().getName())
                .collect(Collectors.joining(", "));
        Text debtorInfo = new Text("Debtors: " + debtorNames);
        debtorInfo.setStyle("-fx-font-size: 12px; -fx-fill: #6b7280;");

        Text dateInfo = new Text("Created: " + transaction.getTimestamp().format(dateFormatter) +
                " at " + transaction.getTimestamp().format(timeFormatter));
        dateInfo.setStyle("-fx-font-size: 11px; -fx-fill: #9ca3af;");

        content.getChildren().addAll(
                descLabel, descField,
                amountLabel, amountField,
                creditorInfo, debtorInfo, dateInfo);

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

                // Get existing debtor IDs and recalculate percentages
                List<Long> debtorIds = new ArrayList<>();
                List<Double> percentages = new ArrayList<>();
                for (TransactionSplit split : transaction.getSplits()) {
                    debtorIds.add(split.getDebtor().getId());
                    percentages.add(split.getPercentage());
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
                error.setContentText("Please enter a valid amount.");
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
}
