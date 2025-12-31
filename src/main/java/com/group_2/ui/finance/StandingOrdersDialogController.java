package com.group_2.ui.finance;

import com.group_2.model.finance.StandingOrderFrequency;
import com.group_2.service.core.UserService;
import com.group_2.service.finance.StandingOrderService;
import com.group_2.util.SessionManager;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.group_2.dto.finance.StandingOrderViewDTO;

@Component
public class StandingOrdersDialogController extends com.group_2.ui.core.Controller {

    private final StandingOrderService standingOrderService;
    private final SessionManager sessionManager;
    private final UserService userService;
    private final DecimalFormat currencyFormat = new DecimalFormat("€#,##0.00");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML
    private StackPane dialogOverlay;
    @FXML
    private TableView<StandingOrderViewDTO> standingOrdersTable;
    @FXML
    private TableColumn<StandingOrderViewDTO, String> descriptionColumn;
    @FXML
    private TableColumn<StandingOrderViewDTO, String> payerColumn;
    @FXML
    private TableColumn<StandingOrderViewDTO, String> debtorsColumn;
    @FXML
    private TableColumn<StandingOrderViewDTO, String> amountColumn;
    @FXML
    private TableColumn<StandingOrderViewDTO, String> frequencyColumn;
    @FXML
    private TableColumn<StandingOrderViewDTO, String> nextExecutionColumn;
    @FXML
    private TableColumn<StandingOrderViewDTO, Void> actionsColumn;
    @FXML
    private VBox emptyState;

    private Runnable onOrdersChanged;

    @Autowired
    public StandingOrdersDialogController(StandingOrderService standingOrderService, SessionManager sessionManager,
            UserService userService) {
        this.standingOrderService = standingOrderService;
        this.sessionManager = sessionManager;
        this.userService = userService;
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

        setupTable();
    }

    private void setupTable() {
        // Description column
        descriptionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().description()));

        // Payer column
        payerColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().creditor() != null ? cellData.getValue().creditor().displayName() : "Unknown"));

        // Debtors column
        debtorsColumn.setCellValueFactory(cellData -> {
            String debtorNames = parseDebtorNames(cellData.getValue().debtors());
            return new SimpleStringProperty(debtorNames);
        });

        // Amount column
        amountColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(currencyFormat.format(cellData.getValue().totalAmount())));

        // Frequency column
        frequencyColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatFrequency(cellData.getValue())));

        // Next execution column
        nextExecutionColumn.setCellValueFactory(cellData -> {
            String nextDate = "N/A";
            if (cellData.getValue().nextExecution() != null) {
                nextDate = cellData.getValue().nextExecution().format(dateFormatter);
            }
            return new SimpleStringProperty(nextDate);
        });
        actionsColumn.setCellFactory(col -> new TableCell<StandingOrderViewDTO, Void>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");

            {
                editBtn.getStyleClass().addAll("table-action-button", "table-edit-button");
                editBtn.setOnAction(e -> {
                    StandingOrderViewDTO order = getTableView().getItems().get(getIndex());
                    showEditDialog(order);
                });

                deleteBtn.getStyleClass().addAll("table-action-button", "table-delete-button");
                deleteBtn.setOnAction(e -> {
                    StandingOrderViewDTO order = getTableView().getItems().get(getIndex());
                    confirmAndDelete(order);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    StandingOrderViewDTO order = getTableView().getItems().get(getIndex());
                    Long currentUserId = sessionManager.getCurrentUserId();

                    // Only show actions for creator
                    if (currentUserId != null && order.createdBy() != null
                            && order.createdBy().id().equals(currentUserId)) {
                        HBox buttons = new HBox(5, editBtn, deleteBtn);
                        setGraphic(buttons);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        // Add double click listener
        standingOrdersTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && standingOrdersTable.getSelectionModel().getSelectedItem() != null) {
                StandingOrderViewDTO selectedOrder = standingOrdersTable.getSelectionModel().getSelectedItem();
                Long currentUserId = sessionManager.getCurrentUserId();

                if (currentUserId != null && selectedOrder.createdBy() != null
                        && selectedOrder.createdBy().id().equals(currentUserId)) {
                    showEditDialog(selectedOrder);
                }
            }
        });
    }

    private String formatFrequency(StandingOrderViewDTO order) {
        StandingOrderFrequency freq = order.frequency();
        switch (freq) {
        case WEEKLY:
            return "Weekly";
        case BI_WEEKLY:
            return "Bi-weekly";
        case MONTHLY:
            if (Boolean.TRUE.equals(order.monthlyLastDay())) {
                return "Monthly (last day)";
            } else if (order.monthlyDay() != null) {
                return "Monthly (" + order.monthlyDay() + getDaySuffix(order.monthlyDay()) + ")";
            } else {
                return "Monthly";
            }
        default:
            return freq.toString();
        }
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

    private String parseDebtorNames(List<StandingOrderViewDTO.DebtorShareViewDTO> debtors) {
        if (debtors == null || debtors.isEmpty()) {
            return "None";
        } else if (debtors.size() == 1) {
            return debtors.get(0).user() != null ? debtors.get(0).user().displayName() : "Unknown";
        } else {
            return debtors.stream().map(d -> {
                String name = d.user() != null ? d.user().displayName() : "Unknown";
                String pct = String.format("%.1f%%", d.percentage());
                return name + " (" + pct + ")";
            }).collect(Collectors.joining(", "));
        }
    }

    private void confirmAndDelete(StandingOrderViewDTO order) {
        Window owner = dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow() : null;
        boolean confirmed = showConfirmDialog("Delete Standing Order", "Are you sure?",
                "This will deactivate the standing order: " + order.description(), owner);

        if (confirmed) {
            standingOrderService.deactivateStandingOrder(order.id());
            loadStandingOrders();
            if (onOrdersChanged != null) {
                onOrdersChanged.run();
            }
        }
    }

    public void showDialog() {
        loadStandingOrders();
        dialogOverlay.setVisible(true);
        dialogOverlay.setManaged(true);
    }

    @FXML
    public void closeDialog() {
        dialogOverlay.setVisible(false);
        dialogOverlay.setManaged(false);
    }

    private void loadStandingOrders() {
        Long wgId = sessionManager.getCurrentWgId();
        if (wgId == null) {
            showEmptyState(true);
            return;
        }

        List<StandingOrderViewDTO> orders = standingOrderService.getActiveStandingOrdersView(wgId);

        if (orders.isEmpty()) {
            showEmptyState(true);
        } else {
            showEmptyState(false);
            standingOrdersTable.setItems(FXCollections.observableArrayList(orders));
        }
    }

    private void showEmptyState(boolean show) {
        emptyState.setVisible(show);
        emptyState.setManaged(show);
        standingOrdersTable.setVisible(!show);
        standingOrdersTable.setManaged(!show);
    }

    public void setOnOrdersChanged(Runnable callback) {
        this.onOrdersChanged = callback;
    }

    private void showEditDialog(StandingOrderViewDTO order) {
        // Create edit dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Standing Order");
        if (dialogOverlay.getScene() != null) {
            dialog.initOwner(dialogOverlay.getScene().getWindow());
        }
        String stylesheet = getClass().getResource("/css/styles.css").toExternalForm();
        if (!dialog.getDialogPane().getStylesheets().contains(stylesheet)) {
            dialog.getDialogPane().getStylesheets().add(stylesheet);
        }

        // Create dialog content
        VBox content = new VBox(15);
        content.setPadding(new javafx.geometry.Insets(20));
        content.getStyleClass().add("dialog-content");
        content.setPrefWidth(500);

        // Defaults for create mode
        String defaultDesc = "";
        double tempAmount = 0.0;
        StandingOrderFrequency defaultFreq = StandingOrderFrequency.MONTHLY;
        Boolean defaultMonthlyLastDay = false;
        Integer defaultMonthlyDay = 1;
        List<StandingOrderViewDTO.DebtorShareViewDTO> defaultDebtors = new java.util.ArrayList<>();

        if (order != null) {
            defaultDesc = order.description();
            tempAmount = order.totalAmount();
            defaultFreq = order.frequency();
            defaultMonthlyLastDay = order.monthlyLastDay();
            defaultMonthlyDay = order.monthlyDay();
            defaultDebtors = order.debtors();
        }
        final double defaultAmount = tempAmount;

        // Description field
        javafx.scene.text.Text descLabel = new javafx.scene.text.Text("Description");
        descLabel.getStyleClass().add("form-label-bold");
        TextField descField = new TextField(defaultDesc);
        descField.getStyleClass().addAll("dialog-field", "dialog-field-small");

        // Amount field
        javafx.scene.text.Text amountLabel = new javafx.scene.text.Text("Total Amount (€)");
        amountLabel.getStyleClass().add("form-label-bold");
        TextField amountField = new TextField(String.format("%.2f", defaultAmount));
        amountField.getStyleClass().addAll("dialog-field", "dialog-field-small");

        // Frequency selection
        javafx.scene.text.Text freqLabel = new javafx.scene.text.Text("Frequency");
        freqLabel.getStyleClass().add("form-label-bold");
        ComboBox<String> freqComboBox = new ComboBox<>();
        freqComboBox.getItems().addAll("Weekly", "Bi-weekly", "Monthly");
        freqComboBox.getStyleClass().add("dialog-field");

        // Set current frequency
        if (defaultFreq != null) {
            switch (defaultFreq) {
            case WEEKLY -> freqComboBox.setValue("Weekly");
            case BI_WEEKLY -> freqComboBox.setValue("Bi-weekly");
            case MONTHLY -> freqComboBox.setValue("Monthly");
            }
        } else {
            freqComboBox.setValue("Monthly");
        }

        // Monthly options
        VBox monthlyOptions = new VBox(10);
        CheckBox lastDayCheckbox = new CheckBox("Execute on last day of month");
        lastDayCheckbox.setSelected(Boolean.TRUE.equals(defaultMonthlyLastDay));

        javafx.scene.text.Text dayLabel = new javafx.scene.text.Text("Day of month (1-31):");
        dayLabel.getStyleClass().add("text-small");
        TextField dayField = new TextField(defaultMonthlyDay != null ? defaultMonthlyDay.toString() : "1");
        dayField.setPrefWidth(60);
        dayField.getStyleClass().addAll("dialog-field", "dialog-field-small");

        HBox dayBox = new HBox(10, dayLabel, dayField);
        dayBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        monthlyOptions.getChildren().addAll(lastDayCheckbox, dayBox);
        monthlyOptions.setVisible("Monthly".equals(freqComboBox.getValue()));
        monthlyOptions.setManaged("Monthly".equals(freqComboBox.getValue()));

        freqComboBox.setOnAction(e -> {
            boolean isMonthly = "Monthly".equals(freqComboBox.getValue());
            monthlyOptions.setVisible(isMonthly);
            monthlyOptions.setManaged(isMonthly);
        });

        lastDayCheckbox.setOnAction(e -> {
            dayBox.setVisible(!lastDayCheckbox.isSelected());
            dayBox.setManaged(!lastDayCheckbox.isSelected());
        });
        dayBox.setVisible(!lastDayCheckbox.isSelected());
        dayBox.setManaged(!lastDayCheckbox.isSelected());

        content.getChildren().addAll(descLabel, descField, amountLabel, amountField, freqLabel, freqComboBox,
                monthlyOptions);

        // Parse existing debtor data
        List<Long> originalDebtorIds = new java.util.ArrayList<>();
        List<Double> originalPercentages = new java.util.ArrayList<>();
        parseDebtorDataForEdit(defaultDebtors, originalDebtorIds, originalPercentages);

        // Split editing - only show if multiple debtors
        java.util.Map<Long, TextField> splitFields = new java.util.HashMap<>();
        final String[] currentMode = { "AMOUNT" };
        VBox splitsContainer = new VBox(8);
        javafx.scene.text.Text validationLabel = new javafx.scene.text.Text("");

        if (originalDebtorIds.size() > 1) {
            javafx.scene.text.Text splitsLabel = new javafx.scene.text.Text("Split Options");
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
            amountBtn.setSelected(true);

            modeSelector.getChildren().addAll(equalBtn, percentBtn, amountBtn);
            content.getChildren().add(modeSelector);

            splitsContainer.getStyleClass().add("splits-container");
            content.getChildren().add(splitsContainer);

            validationLabel.getStyleClass().add("validation-label");
            content.getChildren().add(validationLabel);

            // Build a map of userId -> display name
            Map<Long, String> displayNameMap = userService.getDisplayNames(originalDebtorIds);

            // Function to rebuild split fields
            Runnable rebuildSplitFields = () -> {
                splitsContainer.getChildren().clear();
                splitFields.clear();

                double total;
                final double amountFallback = defaultAmount; // Capture for lambda
                try {
                    total = Double.parseDouble(amountField.getText().replace(",", "."));
                } catch (NumberFormatException ex) {
                    total = amountFallback;
                }

                validationLabel.getStyleClass().removeAll("validation-label-success", "validation-label-error",
                        "validation-label-muted");
                if (!validationLabel.getStyleClass().contains("validation-label")) {
                    validationLabel.getStyleClass().add("validation-label");
                }

                if (currentMode[0].equals("EQUAL")) {
                    double equalAmount = total / originalDebtorIds.size();
                    for (int i = 0; i < originalDebtorIds.size(); i++) {
                        Long userId = originalDebtorIds.get(i);
                        String name = displayNameMap.getOrDefault(userId, "User " + userId);

                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        javafx.scene.text.Text nameText = new javafx.scene.text.Text(
                                name + ": " + String.format("%.2f", equalAmount) + " €");
                        nameText.getStyleClass().add("text-small");
                        row.getChildren().add(nameText);
                        splitsContainer.getChildren().add(row);
                    }
                    validationLabel.setText("Equal split");
                    validationLabel.getStyleClass().add("validation-label-success");
                } else if (currentMode[0].equals("PERCENT")) {
                    for (int i = 0; i < originalDebtorIds.size(); i++) {
                        Long userId = originalDebtorIds.get(i);
                        String name = displayNameMap.getOrDefault(userId, "User " + userId);
                        Double pct = i < originalPercentages.size() ? originalPercentages.get(i)
                                : 100.0 / originalDebtorIds.size();

                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        javafx.scene.text.Text nameText = new javafx.scene.text.Text(name + ":");
                        nameText.getStyleClass().add("text-small");
                        nameText.setWrappingWidth(120);

                        TextField field = new TextField(String.format("%.1f", pct));
                        field.getStyleClass().add("input-compact");
                        field.setPrefWidth(70);
                        splitFields.put(userId, field);

                        // Live validation listener
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

                        javafx.scene.text.Text percentSign = new javafx.scene.text.Text("%");
                        percentSign.getStyleClass().add("unit-sign");

                        row.getChildren().addAll(nameText, field, percentSign);
                        splitsContainer.getChildren().add(row);
                    }
                    double sum = originalPercentages.stream().mapToDouble(Double::doubleValue).sum();
                    double remaining = 100.0 - sum;
                    validationLabel.getStyleClass().removeAll("validation-label-success", "validation-label-error",
                            "validation-label-muted");
                    validationLabel.setText(String.format("Total: %.1f%% of 100%%\n%.1f%% left", sum, remaining));
                    if (Math.abs(remaining) < 0.01) {
                        validationLabel.getStyleClass().add("validation-label-success");
                    } else if (remaining < 0) {
                        validationLabel.getStyleClass().add("validation-label-error");
                    } else {
                        validationLabel.getStyleClass().add("validation-label-muted");
                    }
                } else { // AMOUNT
                    for (int i = 0; i < originalDebtorIds.size(); i++) {
                        Long userId = originalDebtorIds.get(i);
                        String name = displayNameMap.getOrDefault(userId, "User " + userId);
                        Double pct = i < originalPercentages.size() ? originalPercentages.get(i)
                                : 100.0 / originalDebtorIds.size();
                        double amount = (pct / 100.0) * total;

                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        javafx.scene.text.Text nameText = new javafx.scene.text.Text(name + ":");
                        nameText.getStyleClass().add("text-small");
                        nameText.setWrappingWidth(120);

                        TextField field = new TextField(String.format("%.2f", amount));
                        field.getStyleClass().add("input-compact");
                        field.setPrefWidth(80);
                        splitFields.put(userId, field);

                        field.textProperty().addListener((obs, oldVal, newVal) -> {
                            try {
                                double totalAmt = Double.parseDouble(amountField.getText().replace(",", "."));
                                double sum = 0;
                                for (TextField f : splitFields.values()) {
                                    sum += Double.parseDouble(f.getText().replace(",", "."));
                                }
                                double remaining = totalAmt - sum;
                                validationLabel.getStyleClass().removeAll("validation-label-success",
                                        "validation-label-error", "validation-label-muted");
                                if (Math.abs(remaining) < 0.01) {
                                    validationLabel.getStyleClass().add("validation-label-success");
                                } else if (remaining < 0) {
                                    validationLabel.getStyleClass().add("validation-label-error");
                                } else {
                                    validationLabel.getStyleClass().add("validation-label-muted");
                                }
                                validationLabel.setText(String.format("Total: %.2f € of %.2f €\n%.2f € left", sum,
                                        totalAmt, remaining));
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

                        javafx.scene.text.Text euroSign = new javafx.scene.text.Text("€");
                        euroSign.getStyleClass().add("unit-sign");

                        row.getChildren().addAll(nameText, field, euroSign);
                        splitsContainer.getChildren().add(row);
                    }
                    double sum = 0;
                    for (int i = 0; i < originalDebtorIds.size(); i++) {
                        Double pct = i < originalPercentages.size() ? originalPercentages.get(i)
                                : 100.0 / originalDebtorIds.size();
                        sum += (pct / 100.0) * total;
                    }
                    double remaining = total - sum;
                    validationLabel.getStyleClass().removeAll("validation-label-success", "validation-label-error",
                            "validation-label-muted");
                    validationLabel
                            .setText(String.format("Total: %.2f € of %.2f €\n%.2f € left", sum, total, remaining));
                    if (Math.abs(remaining) < 0.01) {
                        validationLabel.getStyleClass().add("validation-label-success");
                    } else if (remaining < 0) {
                        validationLabel.getStyleClass().add("validation-label-error");
                    } else {
                        validationLabel.getStyleClass().add("validation-label-muted");
                    }
                }
            };

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

            rebuildSplitFields.run();
        } else {
            // Single debtor info
            String debtorText = defaultDebtors.isEmpty() ? "None" : parseDebtorNames(defaultDebtors);
            javafx.scene.text.Text debtorsLabel = new javafx.scene.text.Text("Debtor: " + debtorText);
            debtorsLabel.getStyleClass().add("dialog-label-secondary");
            content.getChildren().add(debtorsLabel);
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
                // Parse updated values
                String newDescription = descField.getText().trim();
                double newAmount = Double.parseDouble(amountField.getText().replace(",", "."));

                StandingOrderFrequency newFrequency = switch (freqComboBox.getValue()) {
                case "Weekly" -> StandingOrderFrequency.WEEKLY;
                case "Bi-weekly" -> StandingOrderFrequency.BI_WEEKLY;
                case "Monthly" -> StandingOrderFrequency.MONTHLY;
                default -> order != null ? order.frequency() : StandingOrderFrequency.MONTHLY;
                };

                Integer newMonthlyDay = null;
                Boolean newMonthlyLastDay = false;

                if (newFrequency == StandingOrderFrequency.MONTHLY) {
                    newMonthlyLastDay = lastDayCheckbox.isSelected();
                    if (!newMonthlyLastDay) {
                        newMonthlyDay = Integer.parseInt(dayField.getText().trim());
                        if (newMonthlyDay < 1 || newMonthlyDay > 31) {
                            throw new IllegalArgumentException("Day must be between 1 and 31");
                        }
                    }
                }

                // Calculate new percentages based on mode
                List<Long> debtorIds = new java.util.ArrayList<>();
                List<Double> percentages = new java.util.ArrayList<>();

                if (originalDebtorIds.size() > 1) {
                    if (currentMode[0].equals("EQUAL")) {
                        double equalPercent = 100.0 / originalDebtorIds.size();
                        for (Long userId : originalDebtorIds) {
                            debtorIds.add(userId);
                            percentages.add(equalPercent);
                        }
                    } else if (currentMode[0].equals("PERCENT")) {
                        double sum = 0;
                        for (Long userId : originalDebtorIds) {
                            TextField field = splitFields.get(userId);
                            double pct = Double.parseDouble(field.getText().replace(",", "."));
                            sum += pct;
                            debtorIds.add(userId);
                            percentages.add(pct);
                        }
                        if (Math.abs(sum - 100.0) > 0.1) {
                            throw new IllegalArgumentException(
                                    String.format("Percentages must sum to 100%% (current: %.1f%%)", sum));
                        }
                    } else { // AMOUNT
                        double totalSplit = 0;
                        for (Long userId : originalDebtorIds) {
                            TextField field = splitFields.get(userId);
                            totalSplit += Double.parseDouble(field.getText().replace(",", "."));
                        }
                        if (Math.abs(totalSplit - newAmount) > 0.01) {
                            throw new IllegalArgumentException(String
                                    .format("Split amounts (€%.2f) must equal total (€%.2f)", totalSplit, newAmount));
                        }
                        for (Long userId : originalDebtorIds) {
                            TextField field = splitFields.get(userId);
                            double amount = Double.parseDouble(field.getText().replace(",", "."));
                            debtorIds.add(userId);
                            percentages.add((amount / newAmount) * 100.0);
                        }
                    }
                } else {
                    debtorIds.addAll(originalDebtorIds);
                    percentages.addAll(originalPercentages);
                }

                // Create or Update
                if (order == null) {
                    Long currentUserId = sessionManager.getCurrentUserId();
                    Long wgId = sessionManager.getCurrentWgId();
                    standingOrderService.createStandingOrderView(currentUserId, // Creator
                            currentUserId, // Creditor (self)
                            wgId, newAmount, newDescription, newFrequency, null, debtorIds,
                            percentages.isEmpty() ? null : percentages, newMonthlyDay, newMonthlyLastDay);
                } else {
                    Long currentUserId = sessionManager.getCurrentUserId();
                    standingOrderService.updateStandingOrderView(order.id(), currentUserId, order.creditor().id(),
                            newAmount, newDescription, newFrequency, debtorIds,
                            percentages.isEmpty() ? null : percentages, newMonthlyDay, newMonthlyLastDay);
                }

                // Refresh the table
                loadStandingOrders();

                if (onOrdersChanged != null) {
                    onOrdersChanged.run();
                }

                // Show success message
                Window owner = dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow() : null;
                showSuccessAlert("Success", "Standing order updated successfully.", owner);

            } catch (NumberFormatException e) {
                Window owner = dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow() : null;
                showErrorAlert("Invalid input", "Please enter valid numbers.", owner);
            } catch (Exception e) {
                Window owner = dialogOverlay.getScene() != null ? dialogOverlay.getScene().getWindow() : null;
                showErrorAlert("Failed to update standing order", e.getMessage(), owner);
            }
        }
    }

    private void parseDebtorDataForEdit(List<StandingOrderViewDTO.DebtorShareViewDTO> debtors, List<Long> debtorIds,
            List<Double> percentages) {
        if (debtors == null)
            return;

        for (StandingOrderViewDTO.DebtorShareViewDTO d : debtors) {
            debtorIds.add(d.userId());
            percentages.add(d.percentage());
        }
    }
}
