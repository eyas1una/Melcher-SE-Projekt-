package com.group_2.ui;

import com.group_2.service.TransactionService;
import com.group_2.util.SessionManager;
import com.model.User;
import com.model.WG;
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

    // Navbar
    @FXML
    private NavbarController navbarController;

    // Balance card
    @FXML
    private VBox balanceCard;

    private DecimalFormat currencyFormat = new DecimalFormat("€#,##0.00");

    @Autowired
    public TransactionsController(TransactionService transactionService,
            SessionManager sessionManager) {
        this.transactionService = transactionService;
        this.sessionManager = sessionManager;
    }

    @FXML
    public void initialize() {
        if (navbarController != null) {
            navbarController.setTitle("💰 Transactions");
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
        balanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        balanceTable.setFocusTraversable(false);

        // Make rows clickable for settlement
        balanceTable.setRowFactory(tv -> {
            TableRow<BalanceEntry> row = new TableRow<>();
            row.setStyle("-fx-cursor: hand;");
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
                balanceTable.getItems().add(new BalanceEntry(memberName, balance, member));
            }
        }

        // The listener will automatically update the height
        // But call it explicitly to ensure it happens immediately
        updateBalanceTableHeight();
    }

    private void showSettlementDialog(BalanceEntry entry) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        double balance = entry.getBalance();
        double absBalance = Math.abs(balance);
        String memberName = entry.getMemberName();
        User otherUser = entry.getUser();

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
        content.setStyle("-fx-background-color: white;");
        content.setPrefWidth(500);

        // Header icon
        Text headerIcon = new Text(balance < 0 ? "💸" : "💰");
        headerIcon.setStyle("-fx-font-size: 48px;");

        // Message
        Text messageText;
        if (balance < 0) {
            // You owe them
            messageText = new Text("You owe " + memberName);
        } else {
            // They owe you
            messageText = new Text(memberName + " owes you");
        }
        messageText.setStyle("-fx-font-size: 16px; -fx-fill: #374151;");

        // Amount
        Text amountText = new Text(currencyFormat.format(absBalance));
        amountText.setStyle("-fx-font-size: 36px; -fx-font-weight: bold; -fx-fill: " +
                (balance < 0 ? "#ef4444" : "#10b981") + ";");

        // Payment method selection label
        Text paymentLabel = new Text("Select payment method to settle:");
        paymentLabel.setStyle("-fx-font-size: 14px; -fx-fill: #6b7280; -fx-font-weight: 500;");

        // Create payment method buttons
        // Cash button
        Button cashButton = new Button("💵  Cash");
        cashButton.setStyle("-fx-background-color: linear-gradient(to bottom right, #10b981, #059669); " +
                "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 140;");
        cashButton.setOnMouseEntered(
                e -> cashButton.setStyle("-fx-background-color: linear-gradient(to bottom right, #059669, #047857); " +
                        "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                        "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 140;"));
        cashButton.setOnMouseExited(
                e -> cashButton.setStyle("-fx-background-color: linear-gradient(to bottom right, #10b981, #059669); " +
                        "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                        "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 140;"));

        // Bank transfer button
        Button bankButton = new Button("🏦  Bank Transfer");
        bankButton.setStyle("-fx-background-color: linear-gradient(to bottom right, #3b82f6, #2563eb); " +
                "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 140;");
        bankButton.setOnMouseEntered(
                e -> bankButton.setStyle("-fx-background-color: linear-gradient(to bottom right, #2563eb, #1d4ed8); " +
                        "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                        "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 140;"));
        bankButton.setOnMouseExited(
                e -> bankButton.setStyle("-fx-background-color: linear-gradient(to bottom right, #3b82f6, #2563eb); " +
                        "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                        "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 140;"));

        // PayPal button with icon
        Button paypalButton = new Button("PayPal");
        try {
            Image paypalImage = new Image(getClass().getResourceAsStream("/icon_paypal.png"));
            ImageView paypalIcon = new ImageView(paypalImage);
            paypalIcon.setFitWidth(20);
            paypalIcon.setFitHeight(20);
            paypalIcon.setPreserveRatio(true);
            paypalButton.setGraphic(paypalIcon);
        } catch (Exception e) {
            paypalButton.setText("💳  PayPal");
        }
        paypalButton.setStyle("-fx-background-color: linear-gradient(to bottom right, #0070ba, #003087); " +
                "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 140;");
        paypalButton.setOnMouseEntered(e -> paypalButton
                .setStyle("-fx-background-color: linear-gradient(to bottom right, #003087, #001f5f); " +
                        "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                        "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 140;"));
        paypalButton.setOnMouseExited(e -> paypalButton
                .setStyle("-fx-background-color: linear-gradient(to bottom right, #0070ba, #003087); " +
                        "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                        "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 140;"));

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
                            String name = member.getName() +
                                    (member.getSurname() != null ? " " + member.getSurname() : "");
                            availableCredits.add(new BalanceEntry(name, memberBalance, member));
                        }
                    }
                }
            }

            if (!availableCredits.isEmpty()) {
                // Add separator
                Text orText = new Text("— or —");
                orText.setStyle("-fx-font-size: 12px; -fx-fill: #9ca3af;");

                // Credit Transfer button
                Button creditTransferButton = new Button("🔄  Credit Transfer");
                creditTransferButton
                        .setStyle("-fx-background-color: linear-gradient(to bottom right, #8b5cf6, #7c3aed); " +
                                "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                                "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 200;");
                creditTransferButton.setOnMouseEntered(e -> creditTransferButton
                        .setStyle("-fx-background-color: linear-gradient(to bottom right, #7c3aed, #6d28d9); " +
                                "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                                "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 200;"));
                creditTransferButton.setOnMouseExited(e -> creditTransferButton
                        .setStyle("-fx-background-color: linear-gradient(to bottom right, #8b5cf6, #7c3aed); " +
                                "-fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15 30; " +
                                "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-min-width: 200;"));

                creditTransferButton.setOnAction(e -> {
                    dialog.setResult("CreditTransfer");
                    dialog.close();
                });

                Text creditHint = new Text("Use credit from another roommate to settle this debt");
                creditHint.setStyle("-fx-font-size: 11px; -fx-fill: #9ca3af;");

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
                showSettlementConfirmation(currentUser, otherUser, finalAbsBalance, paymentMethod,
                        finalCurrentUserPays, memberName);
            }
        }
    }

    private void showSettlementConfirmation(User currentUser, User otherUser, double amount,
            String paymentMethod, boolean currentUserPays, String memberName) {

        // Create confirmation dialog
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Settlement");
        confirmDialog.setHeaderText("Confirm your settlement");

        String action = currentUserPays ? "pay" : "mark as received from";
        String message = String.format("You are about to %s %s to %s via %s.\n\nDo you want to proceed?",
                action, currencyFormat.format(amount), memberName, paymentMethod);
        confirmDialog.setContentText(message);

        // Set owner window
        Window owner = balanceTable.getScene().getWindow();
        confirmDialog.initOwner(owner);

        // Style the dialog
        confirmDialog.getDialogPane().setStyle("-fx-background-color: white;");

        // Add custom buttons
        ButtonType confirmButton = new ButtonType("✓ Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getButtonTypes().setAll(confirmButton, cancelButton);

        // Style the confirm button
        Button confirmBtn = (Button) confirmDialog.getDialogPane().lookupButton(confirmButton);
        confirmBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-background-radius: 8; " +
                "-fx-padding: 8 20; -fx-font-weight: bold; -fx-cursor: hand;");

        // Handle result
        Optional<ButtonType> confirmResult = confirmDialog.showAndWait();
        if (confirmResult.isPresent() && confirmResult.get() == confirmButton) {
            // Create settlement transaction
            createSettlementTransaction(currentUser, otherUser, amount, paymentMethod, currentUserPays);
        }
    }

    private void createSettlementTransaction(User currentUser, User otherUser, double amount,
            String paymentMethod, boolean currentUserPays) {
        try {
            // Determine payer and debtor
            Long payerId;
            Long debtorId;
            String description;

            if (currentUserPays) {
                // Current user is paying off their debt
                payerId = currentUser.getId();
                debtorId = otherUser.getId();
                description = "Settlement via " + paymentMethod + " (paid to " +
                        otherUser.getName() + (otherUser.getSurname() != null ? " " + otherUser.getSurname() : "")
                        + ")";
            } else {
                // Other user is paying off their debt to current user
                payerId = otherUser.getId();
                debtorId = currentUser.getId();
                description = "Settlement via " + paymentMethod + " (received from " +
                        otherUser.getName() + (otherUser.getSurname() != null ? " " + otherUser.getSurname() : "")
                        + ")";
            }

            // Create the transaction (current user is always the creator)
            transactionService.createTransaction(
                    currentUser.getId(), // creator
                    payerId,
                    List.of(debtorId),
                    null, // Equal split (100% to single debtor)
                    amount,
                    description);

            // Refresh the display
            updateBalanceDisplay();
            updateBalanceSheet();

            // Show success message
            showAlert(Alert.AlertType.INFORMATION, "Settlement Complete",
                    "The balance with " + otherUser.getName() +
                            (otherUser.getSurname() != null ? " " + otherUser.getSurname() : "") +
                            " has been settled.",
                    balanceTable.getScene().getWindow());

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Settlement Failed",
                    "Could not create settlement: " + e.getMessage(),
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
                        String name = member.getName() +
                                (member.getSurname() != null ? " " + member.getSurname() : "");
                        availableCredits.add(new BalanceEntry(name, memberBalance, member));
                    }
                }
            }
        }

        if (availableCredits.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Credits Available",
                    "There are no roommates who currently owe you money.",
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
        content.setStyle("-fx-background-color: white;");
        content.setPrefWidth(450);

        // Header
        Text headerIcon = new Text("🔄");
        headerIcon.setStyle("-fx-font-size: 48px;");

        Text titleText = new Text("Select Credit Source");
        titleText.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-fill: #1f2937;");

        Text subtitleText = new Text("Choose a roommate's credit to transfer to " + debtorName);
        subtitleText.setStyle("-fx-font-size: 13px; -fx-fill: #6b7280;");

        Text debtInfo = new Text("Debt to settle: " + currencyFormat.format(debtAmount));
        debtInfo.setStyle("-fx-font-size: 14px; -fx-fill: #ef4444; -fx-font-weight: 500;");

        // Create buttons for each available credit
        VBox creditButtons = new VBox(10);
        creditButtons.setAlignment(Pos.CENTER);
        creditButtons.setPadding(new Insets(10, 0, 0, 0));

        for (BalanceEntry credit : availableCredits) {
            double availableAmount = credit.getBalance();
            double transferAmount = Math.min(availableAmount, debtAmount);

            Button creditButton = new Button();
            creditButton.setText(credit.getMemberName() + " owes you " + currencyFormat.format(availableAmount) +
                    "\n→ Transfer " + currencyFormat.format(transferAmount));
            creditButton.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; " +
                    "-fx-background-radius: 12; -fx-padding: 15 25; -fx-font-size: 13px; " +
                    "-fx-cursor: hand; -fx-min-width: 350; -fx-alignment: center-left;");
            creditButton.setOnMouseEntered(e -> creditButton.setStyle(
                    "-fx-background-color: linear-gradient(to bottom right, #8b5cf6, #7c3aed); -fx-text-fill: white; " +
                            "-fx-background-radius: 12; -fx-padding: 15 25; -fx-font-size: 13px; " +
                            "-fx-cursor: hand; -fx-min-width: 350; -fx-alignment: center-left;"));
            creditButton.setOnMouseExited(e -> creditButton.setStyle(
                    "-fx-background-color: #f3f4f6; -fx-text-fill: #374151; " +
                            "-fx-background-radius: 12; -fx-padding: 15 25; -fx-font-size: 13px; " +
                            "-fx-cursor: hand; -fx-min-width: 350; -fx-alignment: center-left;"));

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
            showCreditTransferConfirmation(currentUser, selectedCredit.getUser(), debtorTo,
                    transferAmount, selectedCredit.getMemberName(), debtorName);
        }
    }

    private void showCreditTransferConfirmation(User currentUser, User creditSource, User debtorTo,
            double amount, String creditSourceName, String debtorName) {

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Credit Transfer");
        confirmDialog.setHeaderText("Confirm credit transfer");

        String message = String.format(
                "Transfer %s of credit from %s to settle your debt with %s.\n\n" +
                        "This will:\n" +
                        "• Reduce %s's credit with you by %s\n" +
                        "• Reduce your debt to %s by %s\n\n" +
                        "Do you want to proceed?",
                currencyFormat.format(amount), creditSourceName, debtorName,
                creditSourceName, currencyFormat.format(amount),
                debtorName, currencyFormat.format(amount));
        confirmDialog.setContentText(message);

        confirmDialog.initOwner(balanceTable.getScene().getWindow());
        confirmDialog.getDialogPane().setStyle("-fx-background-color: white;");

        ButtonType confirmButton = new ButtonType("✓ Confirm Transfer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getButtonTypes().setAll(confirmButton, cancelButton);

        Button confirmBtn = (Button) confirmDialog.getDialogPane().lookupButton(confirmButton);
        confirmBtn.setStyle("-fx-background-color: #8b5cf6; -fx-text-fill: white; -fx-background-radius: 8; " +
                "-fx-padding: 8 20; -fx-font-weight: bold; -fx-cursor: hand;");

        Optional<ButtonType> confirmResult = confirmDialog.showAndWait();
        if (confirmResult.isPresent() && confirmResult.get() == confirmButton) {
            executeCreditTransfer(currentUser, creditSource, debtorTo, amount, creditSourceName, debtorName);
        }
    }

    private void executeCreditTransfer(User currentUser, User creditSource, User debtorTo,
            double amount, String creditSourceName, String debtorName) {
        try {
            // Create a transaction that represents: creditSource pays debtorTo on behalf of
            // currentUser
            // This is recorded as: currentUser pays debtorTo (settling the debt)
            // And: creditSource's credit with currentUser is reduced

            // Transaction 1: Current user settles debt with debtorTo
            transactionService.createTransaction(
                    currentUser.getId(), // creator
                    currentUser.getId(), // creditor (payer)
                    List.of(debtorTo.getId()),
                    null,
                    amount,
                    "Credit Transfer from " + creditSourceName + " (settled debt)");

            // Transaction 2: Credit source settles their debt with current user
            transactionService.createTransaction(
                    currentUser.getId(), // creator (current user is creating this on behalf of credit source)
                    creditSource.getId(), // creditor (credit source is the payer)
                    List.of(currentUser.getId()),
                    null,
                    amount,
                    "Credit Transfer to " + debtorName + " (used credit)");

            // Refresh displays
            updateBalanceDisplay();
            updateBalanceSheet();

            showAlert(Alert.AlertType.INFORMATION, "Credit Transfer Complete",
                    String.format("Successfully transferred %s of credit from %s to settle debt with %s.",
                            currencyFormat.format(amount), creditSourceName, debtorName),
                    balanceTable.getScene().getWindow());

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Transfer Failed",
                    "Could not complete credit transfer: " + e.getMessage(),
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
            System.err.println("Error showing transaction dialog: " + e.getMessage());
            e.printStackTrace();
        }
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

    @FXML
    public void showStandingOrders() {
        try {
            StandingOrdersDialogController dialogController = applicationContext
                    .getBean(StandingOrdersDialogController.class);
            dialogController.showDialog();
        } catch (Exception e) {
            System.err.println("Error showing standing orders dialog: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static class BalanceEntry {
        private final String memberName;
        private final double balance;
        private final User user;
        private final DecimalFormat format = new DecimalFormat("€#,##0.00");

        public BalanceEntry(String memberName, double balance, User user) {
            this.memberName = memberName;
            this.balance = balance;
            this.user = user;
        }

        public String getMemberName() {
            return memberName;
        }

        public double getBalance() {
            return balance;
        }

        public User getUser() {
            return user;
        }

        public String getBalanceFormatted() {
            return format.format(balance);
        }
    }
}
