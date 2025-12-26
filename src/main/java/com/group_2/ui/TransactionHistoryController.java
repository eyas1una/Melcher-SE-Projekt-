package com.group_2.ui;

import com.group_2.service.TransactionService;
import com.group_2.util.SessionManager;
import com.model.Transaction;
import com.model.TransactionSplit;
import com.model.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

        // Setup row factory for future edit functionality
        historyTable.setRowFactory(tv -> {
            TableRow<Transaction> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Transaction transaction = row.getItem();
                    // TODO: Open edit dialog for transaction
                    System.out.println("Double-clicked transaction: " + transaction.getId());
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
}
