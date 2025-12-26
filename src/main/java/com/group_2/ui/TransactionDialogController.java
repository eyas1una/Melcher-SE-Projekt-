package com.group_2.ui;

import com.group_2.service.TransactionService;
import com.group_2.util.SessionManager;
import com.model.User;
import com.model.WG;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.*;

@Component
public class TransactionDialogController {

    private final TransactionService transactionService;
    private final SessionManager sessionManager;
    private final TransactionDialogState state;

    // Dialog overlay and step screens
    @FXML
    private StackPane dialogOverlay;
    @FXML
    private VBox step1Screen;
    @FXML
    private VBox step2Screen;
    @FXML
    private VBox step3Screen;

    // Step 1: Creditor & Debtor selection
    @FXML
    private VBox creditorListBox;
    @FXML
    private VBox debtorListBox;
    @FXML
    private Text step1ValidationError;

    // Step 2: Description & Amount
    @FXML
    private Text step2Summary;
    @FXML
    private TextField descriptionField;
    @FXML
    private TextField amountField;
    @FXML
    private Text step2ValidationError;

    // Step 3: Split options
    @FXML
    private RadioButton equalSplitRadio;
    @FXML
    private RadioButton percentageSplitRadio;
    @FXML
    private RadioButton customAmountSplitRadio;
    @FXML
    private VBox equalSplitContent;
    @FXML
    private VBox percentageSplitContent;
    @FXML
    private VBox customAmountSplitContent;
    @FXML
    private VBox percentageSplitParticipantsBox;
    @FXML
    private VBox customAmountSplitParticipantsBox;
    @FXML
    private Text equalSplitSummary;
    @FXML
    private Text percentageSplitSummary;
    @FXML
    private Text customAmountSplitSummary;

    private ToggleGroup splitModeToggleGroup;
    private ToggleGroup creditorToggleGroup;
    private DecimalFormat currencyFormat = new DecimalFormat("€#,##0.00");
    private List<User> allWgMembers;
    private Runnable onTransactionSaved;

    @Autowired
    public TransactionDialogController(TransactionService transactionService,
            SessionManager sessionManager) {
        this.transactionService = transactionService;
        this.sessionManager = sessionManager;
        this.state = new TransactionDialogState();
    }

    public void setOnTransactionSaved(Runnable callback) {
        this.onTransactionSaved = callback;
    }

    @FXML
    public void initialize() {
        // Setup toggle groups
        splitModeToggleGroup = new ToggleGroup();
        equalSplitRadio.setToggleGroup(splitModeToggleGroup);
        percentageSplitRadio.setToggleGroup(splitModeToggleGroup);
        customAmountSplitRadio.setToggleGroup(splitModeToggleGroup);

        creditorToggleGroup = new ToggleGroup();
    }

    public void showDialog() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null) {
            return;
        }

        WG wg = currentUser.getWg();
        allWgMembers = wg.mitbewohner != null ? wg.mitbewohner : new ArrayList<>();

        // Reset state
        state.reset(currentUser, allWgMembers);

        // Show dialog and go to step 1
        dialogOverlay.setVisible(true);
        dialogOverlay.setManaged(true);
        goToStep1();
    }

    @FXML
    public void closeDialog() {
        dialogOverlay.setVisible(false);
        dialogOverlay.setManaged(false);
    }

    @FXML
    public void goToStep1() {
        // Hide all screens
        step1Screen.setVisible(true);
        step1Screen.setManaged(true);
        step2Screen.setVisible(false);
        step2Screen.setManaged(false);
        step3Screen.setVisible(false);
        step3Screen.setManaged(false);

        // Populate Step 1
        populateCreditorList();
        populateDebtorList();
        step1ValidationError.setVisible(false);
        step1ValidationError.setManaged(false);
    }

    @FXML
    public void goToStep2() {
        // Validate Step 1
        if (!validateStep1()) {
            return;
        }

        // Hide all screens
        step1Screen.setVisible(false);
        step1Screen.setManaged(false);
        step2Screen.setVisible(true);
        step2Screen.setManaged(true);
        step3Screen.setVisible(false);
        step3Screen.setManaged(false);

        // Update Step 2 summary
        updateStep2Summary();
        step2ValidationError.setVisible(false);
        step2ValidationError.setManaged(false);
    }

    @FXML
    public void goToStep3() {
        // Validate Step 2
        if (!validateStep2()) {
            return;
        }

        // Hide all screens
        step1Screen.setVisible(false);
        step1Screen.setManaged(false);
        step2Screen.setVisible(false);
        step2Screen.setManaged(false);
        step3Screen.setVisible(true);
        step3Screen.setManaged(true);

        // Populate Step 3
        populateStep3();
    }

    private boolean validateStep1() {
        if (state.getPayer() == null) {
            step1ValidationError.setText("Please select who paid for this expense");
            step1ValidationError.setVisible(true);
            step1ValidationError.setManaged(true);
            return false;
        }

        if (state.getParticipants().isEmpty()) {
            step1ValidationError.setText("Please select at least one person who owes");
            step1ValidationError.setVisible(true);
            step1ValidationError.setManaged(true);
            return false;
        }

        return true;
    }

    private boolean validateStep2() {
        String description = descriptionField.getText();
        if (description == null || description.trim().isEmpty()) {
            step2ValidationError.setText("Please enter a description");
            step2ValidationError.setVisible(true);
            step2ValidationError.setManaged(true);
            return false;
        }

        try {
            String amountText = amountField.getText().replace(",", ".");
            double amount = Double.parseDouble(amountText);
            if (amount <= 0) {
                step2ValidationError.setText("Amount must be greater than 0");
                step2ValidationError.setVisible(true);
                step2ValidationError.setManaged(true);
                return false;
            }
            state.setDescription(description);
            state.setTotalAmount(amount);
        } catch (NumberFormatException e) {
            step2ValidationError.setText("Please enter a valid amount");
            step2ValidationError.setVisible(true);
            step2ValidationError.setManaged(true);
            return false;
        }

        return true;
    }

    private void populateCreditorList() {
        creditorListBox.getChildren().clear();

        for (User member : allWgMembers) {
            RadioButton radio = new RadioButton(member.getName() +
                    (member.getSurname() != null ? " " + member.getSurname() : ""));
            radio.setToggleGroup(creditorToggleGroup);
            radio.setStyle("-fx-font-size: 14px; -fx-padding: 8;");

            // Select current user by default
            if (member.getId().equals(sessionManager.getCurrentUser().getId())) {
                radio.setSelected(true);
                state.setPayer(member);
            }

            radio.setOnAction(e -> {
                state.setPayer(member);
                step1ValidationError.setVisible(false);
                step1ValidationError.setManaged(false);
            });

            creditorListBox.getChildren().add(radio);
        }
    }

    private void populateDebtorList() {
        debtorListBox.getChildren().clear();

        for (User member : allWgMembers) {
            CheckBox checkbox = new CheckBox(member.getName() +
                    (member.getSurname() != null ? " " + member.getSurname() : ""));
            checkbox.setSelected(state.isParticipant(member));
            checkbox.setStyle("-fx-font-size: 14px; -fx-padding: 8;");

            checkbox.setOnAction(e -> {
                if (checkbox.isSelected()) {
                    state.addParticipant(member);
                } else {
                    state.removeParticipant(member);
                }
                step1ValidationError.setVisible(false);
                step1ValidationError.setManaged(false);
            });

            debtorListBox.getChildren().add(checkbox);
        }
    }

    private void updateStep2Summary() {
        String payerName = state.getPayer().getName();
        int debtorCount = state.getParticipants().size();
        String summary = String.format("%s paid for %d person%s",
                payerName, debtorCount, debtorCount == 1 ? "" : "s");
        step2Summary.setText(summary);
    }

    private void populateStep3() {
        // Populate percentage split list
        percentageSplitParticipantsBox.getChildren().clear();
        for (User participant : state.getParticipants()) {
            HBox row = createPercentageRow(participant);
            percentageSplitParticipantsBox.getChildren().add(row);
        }

        // Populate custom amount split list
        customAmountSplitParticipantsBox.getChildren().clear();
        for (User participant : state.getParticipants()) {
            HBox row = createCustomAmountRow(participant);
            customAmountSplitParticipantsBox.getChildren().add(row);
        }

        // Update summaries
        updateEqualSplitSummary();
    }

    @FXML
    public void handleSplitModeChange() {
        if (equalSplitRadio.isSelected()) {
            state.setSplitMode(TransactionDialogState.SplitMode.EQUAL);
            equalSplitContent.setVisible(true);
            equalSplitContent.setManaged(true);
            percentageSplitContent.setVisible(false);
            percentageSplitContent.setManaged(false);
            customAmountSplitContent.setVisible(false);
            customAmountSplitContent.setManaged(false);
            updateEqualSplitSummary();
        } else if (percentageSplitRadio.isSelected()) {
            state.setSplitMode(TransactionDialogState.SplitMode.PERCENTAGE);
            equalSplitContent.setVisible(false);
            equalSplitContent.setManaged(false);
            percentageSplitContent.setVisible(true);
            percentageSplitContent.setManaged(true);
            customAmountSplitContent.setVisible(false);
            customAmountSplitContent.setManaged(false);
        } else if (customAmountSplitRadio.isSelected()) {
            state.setSplitMode(TransactionDialogState.SplitMode.CUSTOM_AMOUNT);
            equalSplitContent.setVisible(false);
            equalSplitContent.setManaged(false);
            percentageSplitContent.setVisible(false);
            percentageSplitContent.setManaged(false);
            customAmountSplitContent.setVisible(true);
            customAmountSplitContent.setManaged(true);
        }
    }

    @FXML
    public void saveTransaction() {
        // Validate final state
        if (!state.isValid()) {
            // Show error in alert dialog
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Validation Error");
            alert.setHeaderText("Cannot save transaction");
            alert.setContentText(state.getValidationError());
            alert.showAndWait();
            return;
        }

        // Prepare data for service
        List<Long> debtorIds = new ArrayList<>();
        List<Double> percentages = new ArrayList<>();

        for (User participant : state.getParticipants()) {
            debtorIds.add(participant.getId());

            switch (state.getSplitMode()) {
                case EQUAL:
                    // Equal split - service will handle this
                    break;
                case PERCENTAGE:
                    Double percentage = state.getCustomValue(participant);
                    percentages.add(percentage != null ? percentage : 0.0);
                    break;
                case CUSTOM_AMOUNT:
                    Double customAmount = state.getCustomValue(participant);
                    double percent = (customAmount / state.getTotalAmount()) * 100.0;
                    percentages.add(percent);
                    break;
            }
        }

        try {
            // Create transaction
            transactionService.createTransaction(
                    state.getPayer().getId(),
                    debtorIds,
                    state.getSplitMode() == TransactionDialogState.SplitMode.EQUAL ? null : percentages,
                    state.getTotalAmount(),
                    state.getDescription());

            // Close dialog
            closeDialog();

            // Trigger callback to refresh parent view
            if (onTransactionSaved != null) {
                onTransactionSaved.run();
            }

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to save transaction");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    private HBox createPercentageRow(User user) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Text nameText = new Text(user.getName() +
                (user.getSurname() != null ? " " + user.getSurname() : ""));
        nameText.setStyle("-fx-font-size: 14px;");

        TextField percentField = new TextField();
        percentField.setPromptText("0");
        percentField.setPrefWidth(80);
        percentField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8;");

        Text percentSign = new Text("%");

        percentField.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                state.getCustomValues().remove(user);
                updatePercentageSummary();
                return;
            }
            try {
                double value = Double.parseDouble(newVal.replace(",", "."));
                state.setCustomValue(user, value);
                updatePercentageSummary();
            } catch (NumberFormatException e) {
                state.getCustomValues().remove(user);
                updatePercentageSummary();
            }
        });

        row.getChildren().addAll(nameText, percentField, percentSign);
        return row;
    }

    private HBox createCustomAmountRow(User user) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Text nameText = new Text(user.getName() +
                (user.getSurname() != null ? " " + user.getSurname() : ""));
        nameText.setStyle("-fx-font-size: 14px;");

        Text euroSign = new Text("€");

        TextField amountField = new TextField();
        amountField.setPromptText("0.00");
        amountField.setPrefWidth(100);
        amountField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8;");

        amountField.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                state.getCustomValues().remove(user);
                updateCustomAmountSummary();
                return;
            }
            try {
                double value = Double.parseDouble(newVal.replace(",", "."));
                state.setCustomValue(user, value);
                updateCustomAmountSummary();
            } catch (NumberFormatException e) {
                state.getCustomValues().remove(user);
                updateCustomAmountSummary();
            }
        });

        row.getChildren().addAll(nameText, euroSign, amountField);
        return row;
    }

    private void updateEqualSplitSummary() {
        int count = state.getParticipants().size();
        if (count > 0 && state.getTotalAmount() > 0) {
            double perPerson = state.getTotalAmount() / count;
            equalSplitSummary.setText(String.format("Each person pays %s", currencyFormat.format(perPerson)));
        } else {
            equalSplitSummary.setText("");
        }
    }

    private void updatePercentageSummary() {
        double total = 0.0;
        for (User participant : state.getParticipants()) {
            Double value = state.getCustomValue(participant);
            if (value != null) {
                total += value;
            }
        }

        double remaining = 100.0 - total;
        String remainingText;
        String remainingStyle;

        if (Math.abs(remaining) < 0.01) {
            remainingText = String.format("%.1f%% left", remaining);
            remainingStyle = "-fx-fill: #10b981; -fx-font-weight: 600;";
        } else if (remaining < 0) {
            remainingText = String.format("%.1f%% left", remaining);
            remainingStyle = "-fx-fill: #ef4444; -fx-font-weight: 600;";
        } else {
            remainingText = String.format("%.1f%% left", remaining);
            remainingStyle = "-fx-fill: #6b7280; -fx-font-weight: 500;";
        }

        percentageSplitSummary.setText(String.format("Total: %.1f%% of 100%% \n %s", total, remainingText));
        percentageSplitSummary.setStyle(remainingStyle);
    }

    private void updateCustomAmountSummary() {
        double total = 0.0;
        for (User participant : state.getParticipants()) {
            Double value = state.getCustomValue(participant);
            if (value != null) {
                total += value;
            }
        }

        double totalAmount = state.getTotalAmount();
        double remaining = totalAmount - total;
        String remainingText;
        String remainingStyle;

        if (Math.abs(remaining) < 0.01) {
            remainingText = String.format("%s left", currencyFormat.format(remaining));
            remainingStyle = "-fx-fill: #10b981; -fx-font-weight: 600;";
        } else if (remaining < 0) {
            remainingText = String.format("%s left", currencyFormat.format(remaining));
            remainingStyle = "-fx-fill: #ef4444; -fx-font-weight: 600;";
        } else {
            remainingText = String.format("%s left", currencyFormat.format(remaining));
            remainingStyle = "-fx-fill: #6b7280; -fx-font-weight: 500;";
        }

        customAmountSplitSummary.setText(String.format("Total: %s of %s \n %s",
                currencyFormat.format(total), currencyFormat.format(totalAmount), remainingText));
        customAmountSplitSummary.setStyle(remainingStyle);
    }
}
