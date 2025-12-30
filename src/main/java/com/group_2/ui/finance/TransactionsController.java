package com.group_2.ui.finance;

import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.service.finance.TransactionService;
import com.group_2.ui.core.Controller;
import com.group_2.ui.core.NavbarController;
import com.group_2.util.SessionManager;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.*;

import com.group_2.dto.finance.BalanceViewDTO;

@Component
public class TransactionsController extends Controller {

    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    private final TransactionService transactionService;
    private final SessionManager sessionManager;

    @Autowired
    private com.group_2.service.core.UserService userService;

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

    // Navbar
    @FXML
    private NavbarController navbarController;

    // Balance card
    @FXML
    private VBox balanceCard;

    private DecimalFormat currencyFormat = new DecimalFormat("EUR #,##0.00");

    @Autowired
    public TransactionsController(TransactionService transactionService, SessionManager sessionManager) {
        this.transactionService = transactionService;
        this.sessionManager = sessionManager;
    }

    @FXML
    public void initialize() {
        if (navbarController != null) {
            navbarController.setTitle("Transactions");
        }
        setupBalanceTable();
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
                    getStyleClass().removeAll("balance-text-positive", "balance-text-negative", "balance-text-neutral");
                } else {
                    setText(item);
                    BalanceEntry entry = getTableView().getItems().get(getIndex());
                    double balance = entry.getBalance();

                    if (balance > 0) {
                        getStyleClass().removeAll("balance-text-negative", "balance-text-neutral");
                        if (!getStyleClass().contains("balance-text-positive")) {
                            getStyleClass().add("balance-text-positive");
                        }
                    } else if (balance < 0) {
                        getStyleClass().removeAll("balance-text-positive", "balance-text-neutral");
                        if (!getStyleClass().contains("balance-text-negative")) {
                            getStyleClass().add("balance-text-negative");
                        }
                    } else {
                        getStyleClass().removeAll("balance-text-positive", "balance-text-negative");
                        if (!getStyleClass().contains("balance-text-neutral")) {
                            getStyleClass().add("balance-text-neutral");
                        }
                    }
                }
            }
        });

        // Remove placeholder rows and configure table properly
        balanceTable.setPlaceholder(new Text("No balance data available"));
        balanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        balanceTable.setFocusTraversable(false);

        // Make rows clickable for settlement
        balanceTable.setRowFactory(tv -> {
            TableRow<BalanceEntry> row = new TableRow<>();
            row.getStyleClass().add("clickable-row");
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 1) {
                    BalanceEntry entry = row.getItem();
                    if (entry != null && entry.getBalance() != 0) {
                        showSettlementDialog(entry);
                    }
                }
            });
            return row;
        });

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

    private void updateBalanceDisplay() {
        Long currentUserId = sessionManager.getCurrentUserId();
        if (currentUserId == null)
            return;

        double totalBalance = transactionService.getTotalBalance(currentUserId);
        totalBalanceText.setText(currencyFormat.format(totalBalance));

        // Change card color based on balance
        if (!balanceCard.getStyleClass().contains("balance-card")) {
            balanceCard.getStyleClass().add("balance-card");
        }
        balanceCard.getStyleClass()
                .removeAll("balance-card-positive", "balance-card-negative", "balance-card-neutral");
        if (totalBalance > 0) {
            // Green gradient - they owe you
            balanceCard.getStyleClass().add("balance-card-positive");
        } else if (totalBalance < 0) {
            // Red gradient - you owe them
            balanceCard.getStyleClass().add("balance-card-negative");
        } else {
            // Blue gradient - all settled
            balanceCard.getStyleClass().add("balance-card-neutral");
        }
    }

    private void updateBalanceSheet() {
        Long currentUserId = sessionManager.getCurrentUserId();

        balanceTable.getItems().clear();

        if (currentUserId == null) {
            return;
        }

        // Use view DTO method instead of entity-based calculation
        List<BalanceViewDTO> balances = transactionService.calculateAllBalancesView(currentUserId);

        for (BalanceViewDTO dto : balances) {
            if (dto.user() != null) {
                balanceTable.getItems().add(new BalanceEntry(dto.user().displayName(), dto.balance(), dto.user().id()));
            }
        }

        // The listener will automatically update the height
        // But call it explicitly to ensure it happens immediately
        updateBalanceTableHeight();
    }

    private void showSettlementDialog(BalanceEntry entry) {
        Long currentUserId = sessionManager.getCurrentUserId();
        if (currentUserId == null)
            return;

        User currentUser = userService.getUser(currentUserId).orElse(null);
        if (currentUser == null)
            return;

        double balance = entry.getBalance();
        double absBalance = Math.abs(balance);
        String memberName = entry.getMemberName();

        // Resolve the User from userId when needed
        User otherUser = userService.getUser(entry.getUserId()).orElse(null);
        if (otherUser == null) {
            showErrorAlert("Error", "User not found", balanceTable.getScene().getWindow());
            return;
        }

        // Create dialog
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Settle Balance");

        // Set owner window
        Window owner = balanceTable.getScene().getWindow();
        dialog.initOwner(owner);

        // Create dialog content
        VBox content = new VBox(20);
        content.setPadding(new Insets(30));
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("dialog-content");
        content.setPrefWidth(500);

        // Header icon
        Text headerIcon = new Text(balance < 0 ? "-" : "+");
        headerIcon.getStyleClass().add("dialog-header-icon");

        // Message
        Text messageText;
        if (balance < 0) {
            // You owe them
            messageText = new Text("You owe " + memberName);
        } else {
            // They owe you
            messageText = new Text(memberName + " owes you");
        }
        messageText.getStyleClass().add("dialog-message-text");

        // Amount
        Text amountText = new Text(currencyFormat.format(absBalance));
        amountText.getStyleClass().addAll("dialog-amount-text",
                balance < 0 ? "dialog-amount-negative"
                        : balance > 0 ? "dialog-amount-positive" : "dialog-amount-neutral");

        // Payment method selection label
        Text paymentLabel = new Text("Select payment method to settle:");
        paymentLabel.getStyleClass().add("payment-section-label");

        // Create payment method buttons
        // Cash button
        Button cashButton = new Button("Cash");
        cashButton.getStyleClass().addAll("payment-button", "payment-button-cash");

        // Bank transfer button
        Button bankButton = new Button("Bank Transfer");
        bankButton.getStyleClass().addAll("payment-button", "payment-button-bank");

        // PayPal button with icon
        Button paypalButton = new Button("PayPal");
        try {
            Image paypalImage = new Image(getClass().getResourceAsStream("/pictures/icon_paypal.png"));
            ImageView paypalIcon = new ImageView(paypalImage);
            paypalIcon.setFitWidth(20);
            paypalIcon.setFitHeight(20);
            paypalIcon.setPreserveRatio(true);
            paypalButton.setGraphic(paypalIcon);
        } catch (Exception e) {
            paypalButton.setText("PayPal");
        }
        paypalButton.getStyleClass().addAll("payment-button", "payment-button-paypal");

        // Set up button actions to show confirmation
        cashButton.setOnAction(e -> {
            dialog.setResult("Cash");
            dialog.close();
        });
        bankButton.setOnAction(e -> {
            dialog.setResult("Bank Transfer");
            dialog.close();
        });
        paypalButton.setOnAction(e -> {
            dialog.setResult("PayPal");
            dialog.close();
        });

        // Arrange buttons in first row
        HBox paymentButtons = new HBox(15, cashButton, bankButton, paypalButton);
        paymentButtons.setAlignment(Pos.CENTER);
        paymentButtons.setPadding(new Insets(10, 0, 0, 0));

        // Credit Transfer button (only show when user owes someone - negative balance)
        // This allows using credit from another roommate to pay this debt
        VBox creditTransferSection = new VBox(10);
        creditTransferSection.setAlignment(Pos.CENTER);

        if (balance < 0) {
            // Find roommates who owe the current user (positive balances = they owe us)
            Map<Long, Double> allBalances = transactionService.calculateAllBalances(currentUser.getId());
            List<BalanceEntry> availableCredits = new ArrayList<>();

            WG wg = currentUser.getWg();
            if (wg != null && wg.mitbewohner != null) {
                for (User member : wg.mitbewohner) {
                    if (!member.getId().equals(currentUser.getId()) && !member.getId().equals(otherUser.getId())) {
                        double memberBalance = allBalances.getOrDefault(member.getId(), 0.0);
                        if (memberBalance > 0) { // They owe us money
                            String name = member.getName()
                                    + (member.getSurname() != null ? " " + member.getSurname() : "");
                            availableCredits.add(new BalanceEntry(name, memberBalance, member.getId()));
                        }
                    }
                }
            }

            if (!availableCredits.isEmpty()) {
                // Add separator
                Text orText = new Text("-- or --");
                orText.getStyleClass().add("or-separator-text");

                // Credit Transfer button
                Button creditTransferButton = new Button("Credit Transfer");
                creditTransferButton.getStyleClass()
                        .addAll("payment-button", "payment-button-credit", "payment-button-wide");

                creditTransferButton.setOnAction(e -> {
                    dialog.setResult("CreditTransfer");
                    dialog.close();
                });

                Text creditHint = new Text("Use credit from another roommate to settle this debt");
                creditHint.getStyleClass().add("credit-hint-text");

                creditTransferSection.getChildren().addAll(orText, creditTransferButton, creditHint);
            }
        }

        content.getChildren().addAll(headerIcon, messageText, amountText, paymentLabel, paymentButtons);
        if (!creditTransferSection.getChildren().isEmpty()) {
            content.getChildren().add(creditTransferSection);
        }

        dialog.getDialogPane().setContent(content);

        // Add only cancel button (payment method buttons handle selection)
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(cancelButton);

        // Store data needed for credit transfer
        final double finalAbsBalance = absBalance;
        final boolean finalCurrentUserPays = balance < 0;

        // Handle result
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().equals("Cancel")) {
            String paymentMethod = result.get();

            if (paymentMethod.equals("CreditTransfer")) {
                // Show credit transfer selection dialog
                showCreditTransferDialog(currentUser, otherUser, finalAbsBalance, memberName);
            } else {
                // Show confirmation dialog for regular payment methods
                showSettlementConfirmation(currentUser, otherUser, finalAbsBalance, paymentMethod, finalCurrentUserPays,
                        memberName);
            }
        }
    }

    private void showSettlementConfirmation(User currentUser, User otherUser, double amount, String paymentMethod,
            boolean currentUserPays, String memberName) {

        // Create confirmation dialog
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Settlement");
        confirmDialog.setHeaderText("Confirm your settlement");

        String action = currentUserPays ? "pay" : "mark as received from";
        String message = String.format("You are about to %s %s to %s via %s.\n\nDo you want to proceed?", action,
                currencyFormat.format(amount), memberName, paymentMethod);
        confirmDialog.setContentText(message);

        // Set owner window
        Window owner = balanceTable.getScene().getWindow();
        confirmDialog.initOwner(owner);

        // Style the dialog
        confirmDialog.getDialogPane().getStyleClass().add("dialog-content");

        // Add custom buttons
        ButtonType confirmButton = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getButtonTypes().setAll(confirmButton, cancelButton);

        // Style the confirm button
        Button confirmBtn = (Button) confirmDialog.getDialogPane().lookupButton(confirmButton);
        confirmBtn.getStyleClass().addAll("confirm-button", "confirm-button-success");

        // Handle result
        Optional<ButtonType> confirmResult = confirmDialog.showAndWait();
        if (confirmResult.isPresent() && confirmResult.get() == confirmButton) {
            // Create settlement transaction
            createSettlementTransaction(currentUser, otherUser, amount, paymentMethod, currentUserPays);
        }
    }

    private void createSettlementTransaction(User currentUser, User otherUser, double amount, String paymentMethod,
            boolean currentUserPays) {
        try {
            transactionService.settleBalance(currentUser.getId(), otherUser.getId(), amount, currentUserPays,
                    paymentMethod);

            updateBalanceDisplay();
            updateBalanceSheet();

            showSuccessAlert("Settlement Complete", "The balance with " + otherUser.getName()
                    + (otherUser.getSurname() != null ? " " + otherUser.getSurname() : "") + " has been settled.",
                    balanceTable.getScene().getWindow());

        } catch (Exception e) {
            showErrorAlert("Settlement Failed", "Could not create settlement: " + e.getMessage(),
                    balanceTable.getScene().getWindow());
        }
    }

    private void showCreditTransferDialog(User currentUser, User debtorTo, double debtAmount, String debtorName) {
        // Find roommates who owe the current user
        Map<Long, Double> allBalances = transactionService.calculateAllBalances(currentUser.getId());
        List<BalanceEntry> availableCredits = new ArrayList<>();

        WG wg = currentUser.getWg();
        if (wg != null && wg.mitbewohner != null) {
            for (User member : wg.mitbewohner) {
                if (!member.getId().equals(currentUser.getId()) && !member.getId().equals(debtorTo.getId())) {
                    double memberBalance = allBalances.getOrDefault(member.getId(), 0.0);
                    if (memberBalance > 0) {
                        String name = member.getName() + (member.getSurname() != null ? " " + member.getSurname() : "");
                        availableCredits.add(new BalanceEntry(name, memberBalance, member.getId()));
                    }
                }
            }
        }

        if (availableCredits.isEmpty()) {
            showSuccessAlert("No Credits Available", "There are no roommates who currently owe you money.",
                    balanceTable.getScene().getWindow());
            return;
        }

        // Create dialog to select credit source
        Dialog<BalanceEntry> dialog = new Dialog<>();
        dialog.setTitle("Credit Transfer");
        dialog.initOwner(balanceTable.getScene().getWindow());

        VBox content = new VBox(20);
        content.setPadding(new Insets(30));
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("dialog-content");
        content.setPrefWidth(450);

        // Header
        Text headerIcon = new Text("Transfer");
        headerIcon.getStyleClass().add("dialog-header-icon");

        Text titleText = new Text("Select Credit Source");
        titleText.getStyleClass().add("dialog-title");

        Text subtitleText = new Text("Choose a roommate's credit to transfer to " + debtorName);
        subtitleText.getStyleClass().add("dialog-subtitle");

        Text debtInfo = new Text("Debt to settle: " + currencyFormat.format(debtAmount));
        debtInfo.getStyleClass().add("debt-info-text");

        // Create buttons for each available credit
        VBox creditButtons = new VBox(10);
        creditButtons.setAlignment(Pos.CENTER);
        creditButtons.setPadding(new Insets(10, 0, 0, 0));

        for (BalanceEntry credit : availableCredits) {
            double availableAmount = credit.getBalance();
            double transferAmount = Math.min(availableAmount, debtAmount);

            Button creditButton = new Button();
            creditButton.getStyleClass().addAll("credit-option-button", "credit-option-button-wide",
                    "credit-option-button-accent");
            creditButton.setText(credit.getMemberName() + " owes you " + currencyFormat.format(availableAmount)
                    + "\n-> Transfer " + currencyFormat.format(transferAmount));

            final BalanceEntry selectedCredit = credit;
            creditButton.setOnAction(e -> {
                dialog.setResult(selectedCredit);
                dialog.close();
            });

            creditButtons.getChildren().add(creditButton);
        }

        content.getChildren().addAll(headerIcon, titleText, subtitleText, debtInfo, creditButtons);
        dialog.getDialogPane().setContent(content);

        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(cancelButton);

        Optional<BalanceEntry> result = dialog.showAndWait();
        if (result.isPresent()) {
            BalanceEntry selectedCredit = result.get();
            double transferAmount = Math.min(selectedCredit.getBalance(), debtAmount);

            // Show confirmation for credit transfer
            // Resolve user first
            User creditSourceUser = userService.getUser(selectedCredit.getUserId()).orElse(null);

            if (creditSourceUser != null) {
                showCreditTransferConfirmation(currentUser, creditSourceUser, debtorTo, transferAmount,
                        selectedCredit.getMemberName(), debtorName);
            } else {
                showErrorAlert("Error", "Selected credit source user could not be found.",
                        balanceTable.getScene().getWindow());
            }
        }
    }

    private void showCreditTransferConfirmation(User currentUser, User creditSource, User debtorTo, double amount,
            String creditSourceName, String debtorName) {

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Credit Transfer");
        confirmDialog.setHeaderText("Confirm credit transfer");

        String message = String.format(
                "Transfer %s of credit from %s to settle your debt with %s.\n\n" + "This will:\n"
                        + "- Reduce %s's credit with you by %s\n" + "- Reduce your debt to %s by %s\n\n"
                        + "Do you want to proceed?",
                currencyFormat.format(amount), creditSourceName, debtorName, creditSourceName,
                currencyFormat.format(amount), debtorName, currencyFormat.format(amount));
        confirmDialog.setContentText(message);

        confirmDialog.initOwner(balanceTable.getScene().getWindow());
        confirmDialog.getDialogPane().getStyleClass().add("dialog-content");

        ButtonType confirmButton = new ButtonType("Confirm Transfer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getButtonTypes().setAll(confirmButton, cancelButton);

        Button confirmBtn = (Button) confirmDialog.getDialogPane().lookupButton(confirmButton);
        confirmBtn.getStyleClass().addAll("confirm-button", "confirm-button-primary");

        Optional<ButtonType> confirmResult = confirmDialog.showAndWait();
        if (confirmResult.isPresent() && confirmResult.get() == confirmButton) {
            executeCreditTransfer(currentUser, creditSource, debtorTo, amount, creditSourceName, debtorName);
        }
    }

    private void executeCreditTransfer(User currentUser, User creditSource, User debtorTo, double amount,
            String creditSourceName, String debtorName) {
        try {
            transactionService.transferCredit(currentUser.getId(), creditSource.getId(), debtorTo.getId(), amount);

            updateBalanceDisplay();
            updateBalanceSheet();

            showSuccessAlert("Credit Transfer Complete",
                    String.format("Successfully transferred %s of credit from %s to settle debt with %s.",
                            currencyFormat.format(amount), creditSourceName, debtorName),
                    balanceTable.getScene().getWindow());

        } catch (Exception e) {
            showErrorAlert("Transfer Failed", "Could not complete credit transfer: " + e.getMessage(),
                    balanceTable.getScene().getWindow());
        }
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
            log.error("Error showing transaction dialog: {}", e.getMessage(), e);
        }
    }

    @FXML
    public void navigateToHistory() {
        loadScene(balanceTable.getScene(), "/finance/transaction_history.fxml");
        javafx.application.Platform.runLater(() -> {
            TransactionHistoryController historyController = applicationContext
                    .getBean(TransactionHistoryController.class);
            historyController.initView();
        });
    }

    @FXML
    public void showStandingOrders() {
        try {
            StandingOrdersDialogController dialogController = applicationContext
                    .getBean(StandingOrdersDialogController.class);
            dialogController.showDialog();
        } catch (Exception e) {
            log.error("Error showing standing orders dialog: {}", e.getMessage(), e);
        }
    }

    public static class BalanceEntry {
        private final String memberName;
        private final double balance;
        private final Long userId;
        private final DecimalFormat format = new DecimalFormat("EUR #,##0.00");

        public BalanceEntry(String memberName, double balance, Long userId) {
            this.memberName = memberName;
            this.balance = balance;
            this.userId = userId;
        }

        public String getMemberName() {
            return memberName;
        }

        public double getBalance() {
            return balance;
        }

        public Long getUserId() {
            return userId;
        }

        public String getBalanceFormatted() {
            return format.format(balance);
        }
    }
}
