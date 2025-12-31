package com.group_2.ui.finance;

import com.group_2.dto.core.UserSessionDTO;
import com.group_2.dto.core.UserSummaryDTO;
import com.group_2.model.finance.StandingOrderFrequency;
import com.group_2.service.finance.StandingOrderService;
import com.group_2.service.finance.TransactionService;
import com.group_2.util.SessionManager;
import com.group_2.util.MonthlyScheduleUtil;

import com.group_2.util.FormatUtils;

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

import java.time.LocalDate;
import java.util.*;

@Component
public class TransactionDialogController extends com.group_2.ui.core.Controller {

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
    private List<UserSummaryDTO> allWgMembers;
    private Runnable onTransactionSaved;

    @Autowired
    public TransactionDialogController(TransactionService transactionService, StandingOrderService standingOrderService,
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
        // Ensure dialog overlay uses shared stylesheet and fills the parent when shown
        String stylesheet = getClass().getResource("/css/styles.css").toExternalForm();
        if (!dialogOverlay.getStylesheets().contains(stylesheet)) {
            dialogOverlay.getStylesheets().add(stylesheet);
        }
        if (!dialogOverlay.getStyleClass().contains("dialog-overlay")) {
            dialogOverlay.getStyleClass().add("dialog-overlay");
        }
        dialogOverlay.setPickOnBounds(true);
        dialogOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Setup toggle groups
        splitModeToggleGroup = new ToggleGroup();
        equalSplitRadio.setToggleGroup(splitModeToggleGroup);
        percentageSplitRadio.setToggleGroup(splitModeToggleGroup);
        customAmountSplitRadio.setToggleGroup(splitModeToggleGroup);

        creditorToggleGroup = new ToggleGroup();

        // Setup standing order frequency options
        frequencyComboBox.setItems(FXCollections.observableArrayList("Weekly", "Bi-weekly", "Monthly"));

        // Block past dates in DatePicker - standing orders can only start from today
        startDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate today = LocalDate.now();
                if (date.isBefore(today)) {
                    setDisable(true);
                    if (!getStyleClass().contains("date-cell-disabled")) {
                        getStyleClass().add("date-cell-disabled");
                    }
                } else {
                    setDisable(false);
                    getStyleClass().remove("date-cell-disabled");
                }
            }
        });

        // Shared label styles for split summaries
        equalSplitSummary.getStyleClass().add("validation-label");
        percentageSplitSummary.getStyleClass().add("validation-label");
        customAmountSplitSummary.getStyleClass().add("validation-label");
    }

    public void showDialog() {
        UserSessionDTO session = sessionManager.getCurrentUserSession().orElse(null);
        if (session == null || session.wgId() == null) {
            return;
        }
        allWgMembers = new ArrayList<>(transactionService.getMemberSummaries(session.wgId()));
        UserSummaryDTO currentUser = findMemberSummary(session.userId(), session);
        if (currentUser != null) {
            boolean exists = allWgMembers.stream()
                    .anyMatch(member -> member != null && currentUser.id() != null
                            && currentUser.id().equals(member.id()));
            if (!exists) {
                allWgMembers.add(currentUser);
            }
        }

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

            boolean confirmed = showConfirmDialog("Confirm Standing Order", "Standing Order Confirmation",
                    scheduleDescription,
                    dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow() : null);

            if (!confirmed) {
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
                    LocalDate firstExec = now.withDayOfMonth(MonthlyScheduleUtil.getEffectiveLastDay(now));
                    if (firstExec.isBefore(now) || firstExec.equals(now)) {
                        LocalDate nextMonth = now.plusMonths(1);
                        firstExec = nextMonth.withDayOfMonth(MonthlyScheduleUtil.getEffectiveLastDay(nextMonth));
                    }
                    sb.append(" starting ").append(firstExec.toString());
                } else {
                    int day = startDate.getDayOfMonth();
                    String suffix = FormatUtils.getDaySuffix(day);
                    sb.append("on the ").append(day).append(suffix).append(" of every month");
                    // Set the monthly day from the selected date
                    state.setMonthlyDay(day);
                    // Calculate first execution
                    LocalDate now = LocalDate.now();
                    int actualDay = MonthlyScheduleUtil.getEffectiveDay(now, day);
                    LocalDate firstExec = now.withDayOfMonth(actualDay);
                    if (firstExec.isBefore(now) || firstExec.equals(now)) {
                        LocalDate nextMonth = now.plusMonths(1);
                        firstExec = nextMonth.withDayOfMonth(MonthlyScheduleUtil.getEffectiveDay(nextMonth, day));
                    }
                    sb.append(" starting ").append(firstExec.toString());
                }
                break;
        }

        return sb.toString();
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
        Long currentUserId = sessionManager.getCurrentUserId();

        for (UserSummaryDTO member : allWgMembers) {
            RadioButton radio = new RadioButton(member.displayName());
            radio.setToggleGroup(creditorToggleGroup);
            radio.getStyleClass().add("option-toggle");

            // Select current user by default
            if (currentUserId != null && member.id() != null && member.id().equals(currentUserId)) {
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

        for (UserSummaryDTO member : allWgMembers) {
            CheckBox checkbox = new CheckBox(member.displayName());
            checkbox.setSelected(state.isParticipant(member));
            checkbox.getStyleClass().add("option-toggle");

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
            for (UserSummaryDTO member : allWgMembers) {
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
            selectAllDebtorsButton.getStyleClass().removeAll("select-all-button-inactive");
            if (!selectAllDebtorsButton.getStyleClass().contains("select-all-button-active")) {
                selectAllDebtorsButton.getStyleClass().add("select-all-button-active");
            }
        } else {
            selectAllDebtorsButton.setText("Select All");
            selectAllDebtorsButton.getStyleClass().removeAll("select-all-button-active");
            if (!selectAllDebtorsButton.getStyleClass().contains("select-all-button-inactive")) {
                selectAllDebtorsButton.getStyleClass().add("select-all-button-inactive");
            }
        }
    }

    private void updateStep2Summary() {
        String payerName = state.getPayer().displayName();
        int debtorCount = state.getParticipants().size();
        String summary = String.format("%s paid for %d person%s", payerName, debtorCount, debtorCount == 1 ? "" : "s");
        step2Summary.setText(summary);
    }

    private void populateStep3() {
        // Populate percentage split list
        percentageSplitParticipantsBox.getChildren().clear();
        for (UserSummaryDTO participant : state.getParticipants()) {
            HBox row = createPercentageRow(participant);
            percentageSplitParticipantsBox.getChildren().add(row);
        }

        // Populate custom amount split list
        customAmountSplitParticipantsBox.getChildren().clear();
        for (UserSummaryDTO participant : state.getParticipants()) {
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
            showErrorAlert("Validation Error", state.getValidationError(),
                    dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow() : null);
            return;
        }

        // Validate standing order fields if enabled
        if (state.isStandingOrder()) {
            if (state.getStandingOrderFrequency() == null) {
                showErrorAlert("Validation Error", "Please select a frequency for the standing order",
                        dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow() : null);
                return;
            }

            // For monthly with "last day" checkbox, no date needed
            boolean needsDate = state.getStandingOrderFrequency() != StandingOrderFrequency.MONTHLY
                    || !state.isMonthlyLastDay();

            if (needsDate) {
                LocalDate startDate = startDatePicker.getValue();
                if (startDate == null) {
                    String message = state.getStandingOrderFrequency() == StandingOrderFrequency.MONTHLY
                            ? "Please select a date to determine the day of month"
                            : "Please select a start date for the standing order";
                    showErrorAlert("Validation Error", message,
                            dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow() : null);
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

        for (UserSummaryDTO participant : state.getParticipants()) {
            debtorIds.add(participant.id());

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
            Long currentUserId = sessionManager.getCurrentUserId();
            Long wgId = sessionManager.getCurrentWgId();
            if (currentUserId == null) {
                showErrorAlert("Error", "No active user session.",
                        dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow() : null);
                return;
            }

            if (state.isStandingOrder()) {
                // Use DTO-creating path to keep controllers off entities
                standingOrderService.createStandingOrderDTO(currentUserId, // creator (gets edit rights)
                        state.getPayer().id(), // creditor (payer)
                        wgId, state.getTotalAmount(), state.getDescription(),
                        state.getStandingOrderFrequency(), state.getStandingOrderStartDate(), debtorIds,
                        state.getSplitMode() == TransactionDialogState.SplitMode.EQUAL ? null : percentages,
                        state.getMonthlyDay(), state.isMonthlyLastDay());
            } else {
                // Create immediate transaction (current user is the creator, payer is the
                // creditor)
                transactionService.createTransactionDTO(currentUserId, // creator (gets edit rights)
                        state.getPayer().id(), // creditor (payer)
                        debtorIds, state.getSplitMode() == TransactionDialogState.SplitMode.EQUAL ? null : percentages,
                        state.getTotalAmount(), state.getDescription());
            }

            // Get window reference before closing dialog
            javafx.stage.Window ownerWindow = dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow()
                    : null;

            // Close dialog
            closeDialog();

            // Show success alert
            String successMessage = state.isStandingOrder() ? "Standing order created successfully!"
                    : "Transaction saved successfully!";
            showSuccessAlert("Success", successMessage, ownerWindow);

            // Trigger callback to refresh parent view
            if (onTransactionSaved != null) {
                onTransactionSaved.run();
            }

        } catch (Exception e) {
            showErrorAlert("Error", e.getMessage(),
                    dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow() : null);
        }
    }

    private HBox createPercentageRow(UserSummaryDTO user) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Text nameText = new Text(user.displayName() + ":");
        nameText.getStyleClass().add("text-small");
        nameText.setWrappingWidth(120);

        TextField percentField = new TextField();
        percentField.setPromptText("0.0");
        percentField.setPrefWidth(70);
        percentField.getStyleClass().add("input-compact");

        Text percentSign = new Text("%");
        percentSign.getStyleClass().add("unit-sign");

        // X button to remove participant
        Button removeButton = new Button("X");
        removeButton.getStyleClass().add("split-remove-button");
        removeButton.setOnAction(e -> {
            state.removeParticipant(user);
            percentageSplitParticipantsBox.getChildren().remove(row);
            // Also remove from custom amount list
            customAmountSplitParticipantsBox.getChildren().removeIf(node -> {
                if (node instanceof HBox) {
                    HBox hbox = (HBox) node;
                    return hbox.getUserData() != null && hbox.getUserData().equals(user.id());
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
        row.setUserData(user.id());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(nameText, spacer, percentField, percentSign, removeButton);
        return row;
    }

    private HBox createCustomAmountRow(UserSummaryDTO user) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Text nameText = new Text(user.displayName() + ":");
        nameText.getStyleClass().add("text-small");
        nameText.setWrappingWidth(120);

        TextField amountField = new TextField();
        amountField.setPromptText("0.00");
        amountField.setPrefWidth(80);
        amountField.getStyleClass().add("input-compact");

        Text euroSign = new Text("€");
        euroSign.getStyleClass().add("unit-sign");

        // X button to remove participant
        Button removeButton = new Button("X");
        removeButton.getStyleClass().add("split-remove-button");
        removeButton.setOnAction(e -> {
            state.removeParticipant(user);
            customAmountSplitParticipantsBox.getChildren().remove(row);
            // Also remove from percentage list
            percentageSplitParticipantsBox.getChildren().removeIf(node -> {
                if (node instanceof HBox) {
                    HBox hbox = (HBox) node;
                    return hbox.getUserData() != null && hbox.getUserData().equals(user.id());
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
        row.setUserData(user.id());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(nameText, spacer, amountField, euroSign, removeButton);
        return row;
    }

    private void updateEqualSplitSummary() {
        int count = state.getParticipants().size();
        equalSplitSummary.getStyleClass().removeAll("validation-label-success", "validation-label-error",
                "validation-label-muted");
        if (count > 0 && state.getTotalAmount() > 0) {
            double perPerson = state.getTotalAmount() / count;
            equalSplitSummary.setText(String.format("Each person pays %.2f€", perPerson));
            equalSplitSummary.getStyleClass().add("validation-label-muted");
        } else {
            equalSplitSummary.setText("");
        }
    }

    private void updatePercentageSummary() {
        double total = 0.0;
        for (UserSummaryDTO participant : state.getParticipants()) {
            Double value = state.getCustomValue(participant);
            if (value != null) {
                total += value;
            }
        }

        double remaining = 100.0 - total;
        String remainingText;

        percentageSplitSummary.getStyleClass().removeAll("validation-label-success", "validation-label-error",
                "validation-label-muted");
        if (Math.abs(remaining) < 0.01) {
            remainingText = String.format("%.1f%% left", remaining);
            percentageSplitSummary.getStyleClass().add("validation-label-success");
        } else if (remaining < 0) {
            remainingText = String.format("%.1f%% left", remaining);
            percentageSplitSummary.getStyleClass().add("validation-label-error");
        } else {
            remainingText = String.format("%.1f%% left", remaining);
            percentageSplitSummary.getStyleClass().add("validation-label-muted");
        }

        if (!percentageSplitSummary.getStyleClass().contains("validation-label")) {
            percentageSplitSummary.getStyleClass().add("validation-label");
        }
        percentageSplitSummary.setText(String.format("Total: %.1f%% of 100%%\n%s", total, remainingText));
    }

    private void updateCustomAmountSummary() {
        double total = 0.0;
        for (UserSummaryDTO participant : state.getParticipants()) {
            Double value = state.getCustomValue(participant);
            if (value != null) {
                total += value;
            }
        }

        double totalAmount = state.getTotalAmount();
        double remaining = totalAmount - total;
        String remainingText;

        customAmountSplitSummary.getStyleClass().removeAll("validation-label-success", "validation-label-error",
                "validation-label-muted");
        if (Math.abs(remaining) < 0.01) {
            remainingText = String.format("%.2f€ left", remaining);
            customAmountSplitSummary.getStyleClass().add("validation-label-success");
        } else if (remaining < 0) {
            remainingText = String.format("%.2f€ left", remaining);
            customAmountSplitSummary.getStyleClass().add("validation-label-error");
        } else {
            remainingText = String.format("%.2f€ left", remaining);
            customAmountSplitSummary.getStyleClass().add("validation-label-muted");
        }

        if (!customAmountSplitSummary.getStyleClass().contains("validation-label")) {
            customAmountSplitSummary.getStyleClass().add("validation-label");
        }
        customAmountSplitSummary.setText(String.format("Total: %.2f€ of %.2f€\n%s",
                total, totalAmount, remainingText));
    }

    private UserSummaryDTO findMemberSummary(Long userId, UserSessionDTO session) {
        if (userId == null) {
            return null;
        }
        if (allWgMembers != null) {
            for (UserSummaryDTO member : allWgMembers) {
                if (member != null && userId.equals(member.id())) {
                    return member;
                }
            }
        }
        if (session != null) {
            return new UserSummaryDTO(session.userId(), session.name(), session.surname(), null, session.wgId());
        }
        return null;
    }
}
