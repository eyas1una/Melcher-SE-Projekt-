package com.group_2.ui;

import com.group_2.service.StandingOrderService;
import com.group_2.service.TransactionService;
import com.group_2.util.SessionManager;
import com.model.StandingOrderFrequency;
import com.model.User;
import com.model.WG;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;

@Component
public class TransactionDialogController {

    private final TransactionService transactionService;
    private final StandingOrderService standingOrderService;
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
    private Button selectAllDebtorsButton;
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

    // Step 2: Standing order controls
    @FXML
    private CheckBox standingOrderCheckbox;
    @FXML
    private HBox standingOrderOptionsBox;
    @FXML
    private ComboBox<String> frequencyComboBox;
    @FXML
    private VBox startDateBox;
    @FXML
    private Text startDateLabel;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private VBox monthlyLastDayBox;
    @FXML
    private CheckBox monthlyLastDayCheckbox;

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
            StandingOrderService standingOrderService,
            SessionManager sessionManager) {
        this.transactionService = transactionService;
        this.standingOrderService = standingOrderService;
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

        // Setup standing order frequency options
        frequencyComboBox.setItems(FXCollections.observableArrayList(
                "Weekly", "Bi-weekly", "Monthly"));

        // Block past dates in DatePicker - standing orders can only start from today
        startDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate today = LocalDate.now();
                if (date.isBefore(today)) {
                    setDisable(true);
                    setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #999999;");
                }
            }
        });
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
        // Clear input fields for next time
        descriptionField.clear();
        amountField.clear();

        // Reset split option to equal (default)
        equalSplitRadio.setSelected(true);
        percentageSplitRadio.setSelected(false);
        customAmountSplitRadio.setSelected(false);
        state.setSplitMode(TransactionDialogState.SplitMode.EQUAL);

        // Reset split content visibility
        equalSplitContent.setVisible(true);
        equalSplitContent.setManaged(true);
        percentageSplitContent.setVisible(false);
        percentageSplitContent.setManaged(false);
        customAmountSplitContent.setVisible(false);
        customAmountSplitContent.setManaged(false);

        // Reset standing order options
        standingOrderCheckbox.setSelected(false);
        standingOrderOptionsBox.setVisible(false);
        standingOrderOptionsBox.setManaged(false);
        frequencyComboBox.getSelectionModel().clearSelection();
        startDateBox.setVisible(false);
        startDateBox.setManaged(false);
        startDatePicker.setValue(null);
        monthlyLastDayBox.setVisible(false);
        monthlyLastDayBox.setManaged(false);
        monthlyLastDayCheckbox.setSelected(false);
        state.setStandingOrder(false);
        state.setStandingOrderFrequency(null);
        state.setStandingOrderStartDate(null);
        state.setMonthlyDay(1);
        state.setMonthlyLastDay(false);

        dialogOverlay.setVisible(false);
        dialogOverlay.setManaged(false);
    }

    @FXML
    public void handleStandingOrderToggle() {
        boolean isStanding = standingOrderCheckbox.isSelected();
        standingOrderOptionsBox.setVisible(isStanding);
        standingOrderOptionsBox.setManaged(isStanding);
        state.setStandingOrder(isStanding);

        if (!isStanding) {
            frequencyComboBox.getSelectionModel().clearSelection();
            startDatePicker.setValue(null);
            state.setStandingOrderFrequency(null);
            state.setStandingOrderStartDate(null);
        }
    }

    @FXML
    public void handleFrequencyChange() {
        String selected = frequencyComboBox.getValue();
        if (selected == null) {
            state.setStandingOrderFrequency(null);
            startDateBox.setVisible(false);
            startDateBox.setManaged(false);
            monthlyLastDayBox.setVisible(false);
            monthlyLastDayBox.setManaged(false);
            return;
        }

        StandingOrderFrequency freq;
        switch (selected) {
            case "Weekly":
                freq = StandingOrderFrequency.WEEKLY;
                break;
            case "Bi-weekly":
                freq = StandingOrderFrequency.BI_WEEKLY;
                break;
            case "Monthly":
                freq = StandingOrderFrequency.MONTHLY;
                break;
            default:
                freq = null;
        }
        state.setStandingOrderFrequency(freq);

        boolean isMonthly = freq == StandingOrderFrequency.MONTHLY;

        // Always show date picker (used for all frequencies)
        startDateBox.setVisible(true);
        startDateBox.setManaged(true);

        // Update label based on frequency
        if (isMonthly) {
            startDateLabel.setText("Day of Month (select any date)");
        } else {
            startDateLabel.setText("Start Date");
        }

        // Show "last day of month" checkbox only for monthly
        monthlyLastDayBox.setVisible(isMonthly);
        monthlyLastDayBox.setManaged(isMonthly);

        // Reset fields
        startDatePicker.setValue(null);
        state.setStandingOrderStartDate(null);
        monthlyLastDayCheckbox.setSelected(false);
        state.setMonthlyLastDay(false);
    }

    @FXML
    public void handleMonthlyLastDayToggle() {
        boolean lastDay = monthlyLastDayCheckbox.isSelected();
        state.setMonthlyLastDay(lastDay);

        // Hide date picker when "last day" is checked (day is auto-determined)
        startDateBox.setVisible(!lastDay);
        startDateBox.setManaged(!lastDay);
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

        // If standing order is enabled, show confirmation dialog
        if (state.isStandingOrder()) {
            String scheduleDescription = buildStandingOrderDescription();

            Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
            if (dialogOverlay.getScene() != null) {
                confirmDialog.initOwner(dialogOverlay.getScene().getWindow());
            }
            confirmDialog.setTitle("Confirm Standing Order");
            confirmDialog.setHeaderText("Standing Order Confirmation");
            confirmDialog.setContentText(scheduleDescription);

            Optional<ButtonType> result = confirmDialog.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return; // User cancelled
            }
        }

        // If only one debtor is selected, skip split options and save directly
        if (state.getParticipants().size() == 1) {
            // Force equal split mode (100% to single debtor)
            state.setSplitMode(TransactionDialogState.SplitMode.EQUAL);
            saveTransaction();
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

    /**
     * Build a human-readable description of the standing order schedule
     */
    private String buildStandingOrderDescription() {
        StandingOrderFrequency freq = state.getStandingOrderFrequency();
        LocalDate startDate = startDatePicker.getValue();

        StringBuilder sb = new StringBuilder("The standing order will be executed ");

        switch (freq) {
            case WEEKLY:
                String dayOfWeek = startDate.getDayOfWeek().toString().toLowerCase();
                dayOfWeek = dayOfWeek.substring(0, 1).toUpperCase() + dayOfWeek.substring(1);
                sb.append("every ").append(dayOfWeek);
                sb.append(" starting ").append(startDate.toString());
                break;
            case BI_WEEKLY:
                String biWeeklyDay = startDate.getDayOfWeek().toString().toLowerCase();
                biWeeklyDay = biWeeklyDay.substring(0, 1).toUpperCase() + biWeeklyDay.substring(1);
                sb.append("every second ").append(biWeeklyDay);
                sb.append(" starting ").append(startDate.toString());
                break;
            case MONTHLY:
                if (state.isMonthlyLastDay()) {
                    sb.append("on the last day of every month");
                    // Calculate first execution (last day of current or next month)
                    LocalDate now = LocalDate.now();
                    LocalDate firstExec = now.withDayOfMonth(now.lengthOfMonth());
                    if (firstExec.isBefore(now) || firstExec.equals(now)) {
                        firstExec = now.plusMonths(1).withDayOfMonth(now.plusMonths(1).lengthOfMonth());
                    }
                    sb.append(" starting ").append(firstExec.toString());
                } else {
                    int day = startDate.getDayOfMonth();
                    String suffix = getDaySuffix(day);
                    sb.append("on the ").append(day).append(suffix).append(" of every month");
                    // Set the monthly day from the selected date
                    state.setMonthlyDay(day);
                    // Calculate first execution
                    LocalDate now = LocalDate.now();
                    LocalDate firstExec;
                    if (day <= now.lengthOfMonth()) {
                        firstExec = now.withDayOfMonth(day);
                        if (firstExec.isBefore(now) || firstExec.equals(now)) {
                            firstExec = now.plusMonths(1)
                                    .withDayOfMonth(Math.min(day, now.plusMonths(1).lengthOfMonth()));
                        }
                    } else {
                        firstExec = now.withDayOfMonth(now.lengthOfMonth());
                    }
                    sb.append(" starting ").append(firstExec.toString());
                }
                break;
        }

        return sb.toString();
    }

    private String getDaySuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        switch (day % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
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

        // Check that payer is not the only debtor
        if (state.getParticipants().size() == 1 && state.getParticipants().contains(state.getPayer())) {
            step1ValidationError.setText("The payer cannot be the only debtor. Please add at least one other person.");
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
                // Update select all button text when individual checkbox changes
                updateSelectAllButtonText();
            });

            debtorListBox.getChildren().add(checkbox);
        }

        // Update button text based on current selection state
        updateSelectAllButtonText();
    }

    @FXML
    public void handleSelectAllDebtors() {
        boolean allSelected = state.getParticipants().size() == allWgMembers.size();

        if (allSelected) {
            // Deselect all
            state.getParticipants().clear();
        } else {
            // Select all
            for (User member : allWgMembers) {
                state.addParticipant(member);
            }
        }

        // Update the checkboxes to reflect the new state
        for (javafx.scene.Node node : debtorListBox.getChildren()) {
            if (node instanceof CheckBox) {
                CheckBox checkbox = (CheckBox) node;
                checkbox.setSelected(!allSelected);
            }
        }

        // Update button text
        updateSelectAllButtonText();

        // Hide validation error
        step1ValidationError.setVisible(false);
        step1ValidationError.setManaged(false);
    }

    private void updateSelectAllButtonText() {
        boolean allSelected = state.getParticipants().size() == allWgMembers.size();
        if (allSelected) {
            selectAllDebtorsButton.setText("Deselect All");
            selectAllDebtorsButton.setStyle(
                    "-fx-background-color: #fef2f2; -fx-text-fill: #ef4444; -fx-background-radius: 6; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-weight: 500; -fx-font-size: 12px; -fx-border-color: #ef4444; -fx-border-radius: 6;");
        } else {
            selectAllDebtorsButton.setText("Select All");
            selectAllDebtorsButton.setStyle(
                    "-fx-background-color: #eff6ff; -fx-text-fill: #3b82f6; -fx-background-radius: 6; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-weight: 500; -fx-font-size: 12px; -fx-border-color: #3b82f6; -fx-border-radius: 6;");
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
            if (dialogOverlay.getScene() != null) {
                alert.initOwner(dialogOverlay.getScene().getWindow());
            }
            alert.setTitle("Validation Error");
            alert.setHeaderText("Cannot save transaction");
            alert.setContentText(state.getValidationError());
            alert.showAndWait();
            return;
        }

        // Validate standing order fields if enabled
        if (state.isStandingOrder()) {
            if (state.getStandingOrderFrequency() == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                if (dialogOverlay.getScene() != null) {
                    alert.initOwner(dialogOverlay.getScene().getWindow());
                }
                alert.setTitle("Validation Error");
                alert.setHeaderText("Cannot save standing order");
                alert.setContentText("Please select a frequency for the standing order");
                alert.showAndWait();
                return;
            }

            // For monthly with "last day" checkbox, no date needed
            boolean needsDate = state.getStandingOrderFrequency() != StandingOrderFrequency.MONTHLY
                    || !state.isMonthlyLastDay();

            if (needsDate) {
                LocalDate startDate = startDatePicker.getValue();
                if (startDate == null) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    if (dialogOverlay.getScene() != null) {
                        alert.initOwner(dialogOverlay.getScene().getWindow());
                    }
                    alert.setTitle("Validation Error");
                    alert.setHeaderText("Cannot save standing order");
                    String message = state.getStandingOrderFrequency() == StandingOrderFrequency.MONTHLY
                            ? "Please select a date to determine the day of month"
                            : "Please select a start date for the standing order";
                    alert.setContentText(message);
                    alert.showAndWait();
                    return;
                }
                state.setStandingOrderStartDate(startDate);

                // For monthly, extract day from the date
                if (state.getStandingOrderFrequency() == StandingOrderFrequency.MONTHLY) {
                    state.setMonthlyDay(startDate.getDayOfMonth());
                }
            }
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
            if (state.isStandingOrder()) {
                // Create standing order (current user is the creator, payer is the creditor)
                User currentUser = sessionManager.getCurrentUser();
                WG wg = currentUser.getWg();
                standingOrderService.createStandingOrder(
                        currentUser, // creator (gets edit rights)
                        state.getPayer(), // creditor (payer)
                        wg,
                        state.getTotalAmount(),
                        state.getDescription(),
                        state.getStandingOrderFrequency(),
                        state.getStandingOrderStartDate(),
                        debtorIds,
                        state.getSplitMode() == TransactionDialogState.SplitMode.EQUAL ? null : percentages,
                        state.getMonthlyDay(),
                        state.isMonthlyLastDay());
            } else {
                // Create immediate transaction (current user is the creator, payer is the
                // creditor)
                User currentUser = sessionManager.getCurrentUser();
                transactionService.createTransaction(
                        currentUser.getId(), // creator (gets edit rights)
                        state.getPayer().getId(), // creditor (payer)
                        debtorIds,
                        state.getSplitMode() == TransactionDialogState.SplitMode.EQUAL ? null : percentages,
                        state.getTotalAmount(),
                        state.getDescription());
            }

            // Close dialog
            closeDialog();

            // Trigger callback to refresh parent view
            if (onTransactionSaved != null) {
                onTransactionSaved.run();
            }

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (dialogOverlay.getScene() != null) {
                alert.initOwner(dialogOverlay.getScene().getWindow());
            }
            alert.setTitle("Error");
            alert.setHeaderText("Failed to save " + (state.isStandingOrder() ? "standing order" : "transaction"));
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    private HBox createPercentageRow(User user) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Text nameText = new Text(user.getName() +
                (user.getSurname() != null ? " " + user.getSurname() : "") + ":");
        nameText.setStyle("-fx-font-size: 12px;");
        nameText.setWrappingWidth(120);

        TextField percentField = new TextField();
        percentField.setPromptText("0.0");
        percentField.setPrefWidth(70);
        percentField.setStyle(
                "-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6;");

        Text percentSign = new Text("%");
        percentSign.setStyle("-fx-font-size: 12px;");

        // X button to remove participant
        Button removeButton = new Button("X");
        removeButton.setStyle(
                "-fx-background-color: #fef2f2; -fx-text-fill: #ef4444; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 2 6; -fx-background-radius: 4; -fx-border-color: #fecaca; -fx-border-radius: 4;");
        removeButton.setOnAction(e -> {
            state.removeParticipant(user);
            percentageSplitParticipantsBox.getChildren().remove(row);
            // Also remove from custom amount list
            customAmountSplitParticipantsBox.getChildren().removeIf(node -> {
                if (node instanceof HBox) {
                    HBox hbox = (HBox) node;
                    return hbox.getUserData() != null && hbox.getUserData().equals(user.getId());
                }
                return false;
            });
            updatePercentageSummary();
            updateEqualSplitSummary();
        });

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

        // Store user ID for cross-referencing
        row.setUserData(user.getId());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(nameText, spacer, percentField, percentSign, removeButton);
        return row;
    }

    private HBox createCustomAmountRow(User user) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Text nameText = new Text(user.getName() +
                (user.getSurname() != null ? " " + user.getSurname() : "") + ":");
        nameText.setStyle("-fx-font-size: 12px;");
        nameText.setWrappingWidth(120);

        TextField amountField = new TextField();
        amountField.setPromptText("0.00");
        amountField.setPrefWidth(80);
        amountField.setStyle(
                "-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6;");

        Text euroSign = new Text("€");
        euroSign.setStyle("-fx-font-size: 12px;");

        // X button to remove participant
        Button removeButton = new Button("X");
        removeButton.setStyle(
                "-fx-background-color: #fef2f2; -fx-text-fill: #ef4444; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 2 6; -fx-background-radius: 4; -fx-border-color: #fecaca; -fx-border-radius: 4;");
        removeButton.setOnAction(e -> {
            state.removeParticipant(user);
            customAmountSplitParticipantsBox.getChildren().remove(row);
            // Also remove from percentage list
            percentageSplitParticipantsBox.getChildren().removeIf(node -> {
                if (node instanceof HBox) {
                    HBox hbox = (HBox) node;
                    return hbox.getUserData() != null && hbox.getUserData().equals(user.getId());
                }
                return false;
            });
            updateCustomAmountSummary();
            updateEqualSplitSummary();
        });

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

        // Store user ID for cross-referencing
        row.setUserData(user.getId());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(nameText, spacer, amountField, euroSign, removeButton);
        return row;
    }

    private void updateEqualSplitSummary() {
        int count = state.getParticipants().size();
        if (count > 0 && state.getTotalAmount() > 0) {
            double perPerson = state.getTotalAmount() / count;
            equalSplitSummary.setText(String.format("Each person pays %.2f€", perPerson));
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
            remainingText = String.format("%.2f€ left", remaining);
            remainingStyle = "-fx-fill: #10b981; -fx-font-weight: 600;";
        } else if (remaining < 0) {
            remainingText = String.format("%.2f€ left", remaining);
            remainingStyle = "-fx-fill: #ef4444; -fx-font-weight: 600;";
        } else {
            remainingText = String.format("%.2f€ left", remaining);
            remainingStyle = "-fx-fill: #6b7280; -fx-font-weight: 500;";
        }

        customAmountSplitSummary.setText(String.format("Total: %.2f€ of %.2f€\n%s",
                total, totalAmount, remainingText));
        customAmountSplitSummary.setStyle(remainingStyle);
    }
}
