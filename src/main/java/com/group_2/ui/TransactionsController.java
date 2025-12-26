package com.group_2.ui;

import com.group_2.service.TransactionService;
import com.group_2.util.SessionManager;
import com.model.User;
import com.model.WG;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.*;

@Component
public class TransactionsController extends Controller {

    private final TransactionService transactionService;
    private final SessionManager sessionManager;

    @Autowired
    private ApplicationContext applicationContext;

    // Balance display
    @FXML
    private Text totalBalanceText;

    // Balance table
    @FXML
    private TableView<BalanceEntry> balanceTable;
    @FXML
    private TableColumn<BalanceEntry, String> memberColumn;
    @FXML
    private TableColumn<BalanceEntry, String> balanceColumn;

    // Transaction dialog fields will be added here

    private DecimalFormat currencyFormat = new DecimalFormat("€#,##0.00");

    @Autowired
    public TransactionsController(TransactionService transactionService,
            SessionManager sessionManager) {
        this.transactionService = transactionService;
        this.sessionManager = sessionManager;
    }

    @FXML
    public void initialize() {
        setupBalanceTable();
        // Dialog setup will be added here
    }

    public void initView() {
        sessionManager.refreshCurrentUser();
        updateBalanceDisplay();
        updateBalanceSheet();
    }

    private void setupBalanceTable() {
        memberColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getMemberName()));

        balanceColumn
                .setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getBalanceFormatted()));

        balanceColumn.setCellFactory(column -> new TableCell<BalanceEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    BalanceEntry entry = getTableView().getItems().get(getIndex());
                    double balance = entry.getBalance();

                    if (balance > 0) {
                        setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                    } else if (balance < 0) {
                        setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #64748b; -fx-font-weight: normal;");
                    }
                }
            }
        });

        // Remove placeholder rows and configure table properly
        balanceTable.setPlaceholder(new Text("No balance data available"));
        balanceTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        balanceTable.setSelectionModel(null); // Disable selection
        balanceTable.setFocusTraversable(false);

        // Add a listener to dynamically resize the table based on the number of items
        balanceTable.getItems()
                .addListener((javafx.collections.ListChangeListener.Change<? extends BalanceEntry> c) -> {
                    updateBalanceTableHeight();
                });
    }

    private void updateBalanceTableHeight() {
        int rowCount = balanceTable.getItems().size();
        if (rowCount == 0) {
            balanceTable.setPrefHeight(100);
            balanceTable.setMaxHeight(100);
            balanceTable.setMinHeight(100);
        } else {
            // Calculate exact height: header (40px) + rows (50px each)
            double height = 40 + (rowCount * 50);
            balanceTable.setPrefHeight(height);
            balanceTable.setMaxHeight(height);
            balanceTable.setMinHeight(height);
        }
    }

    // Balance card
    @FXML
    private VBox balanceCard;

    private void updateBalanceDisplay() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        double totalBalance = transactionService.getTotalBalance(currentUser.getId());
        totalBalanceText.setText(currencyFormat.format(totalBalance));

        // Change card color based on balance
        if (totalBalance > 0) {
            // Green gradient - they owe you
            balanceCard.setStyle(
                    "-fx-background-color: linear-gradient(to bottom right, #10b981, #059669); -fx-padding: 25;");
        } else if (totalBalance < 0) {
            // Red gradient - you owe them
            balanceCard.setStyle(
                    "-fx-background-color: linear-gradient(to bottom right, #ef4444, #dc2626); -fx-padding: 25;");
        } else {
            // Blue gradient - all settled
            balanceCard.setStyle(
                    "-fx-background-color: linear-gradient(to bottom right, #3b82f6, #2563eb); -fx-padding: 25;");
        }
    }

    private void updateBalanceSheet() {
        User currentUser = sessionManager.getCurrentUser();

        balanceTable.getItems().clear();

        if (currentUser == null) {
            return;
        }

        WG wg = currentUser.getWg();

        if (wg == null || wg.mitbewohner == null) {
            return;
        }

        Map<Long, Double> balances = transactionService.calculateAllBalances(currentUser.getId());

        for (User member : wg.mitbewohner) {
            if (!member.getId().equals(currentUser.getId())) {
                double balance = balances.getOrDefault(member.getId(), 0.0);
                String memberName = member.getName() +
                        (member.getSurname() != null ? " " + member.getSurname() : "");
                balanceTable.getItems().add(new BalanceEntry(memberName, balance));
            }
        }

        // The listener will automatically update the height
        // But call it explicitly to ensure it happens immediately
        updateBalanceTableHeight();
    }

    @FXML
    public void showAddTransactionDialog() {
        try {
            TransactionDialogController dialogController = applicationContext
                    .getBean(TransactionDialogController.class);

            // Set callback to refresh when transaction is saved
            dialogController.setOnTransactionSaved(() -> {
                updateBalanceDisplay();
                updateBalanceSheet();
            });

            dialogController.showDialog();
        } catch (Exception e) {
            System.err.println("Error showing transaction dialog: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void backToHome() {
        loadScene(balanceTable.getScene(), "/main_screen.fxml");
        javafx.application.Platform.runLater(() -> {
            MainScreenController mainScreenController = applicationContext.getBean(MainScreenController.class);
            mainScreenController.initView();
        });
    }

    @FXML
    public void navigateToHistory() {
        loadScene(balanceTable.getScene(), "/transaction_history.fxml");
        javafx.application.Platform.runLater(() -> {
            TransactionHistoryController historyController = applicationContext
                    .getBean(TransactionHistoryController.class);
            historyController.initView();
        });
    }

    public static class BalanceEntry {
        private final String memberName;
        private final double balance;
        private final DecimalFormat format = new DecimalFormat("€#,##0.00");

        public BalanceEntry(String memberName, double balance) {
            this.memberName = memberName;
            this.balance = balance;
        }

        public String getMemberName() {
            return memberName;
        }

        public double getBalance() {
            return balance;
        }

        public String getBalanceFormatted() {
            return format.format(balance);
        }
    }
}
