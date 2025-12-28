package com.group_2.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group_2.repository.UserRepository;
import com.group_2.service.StandingOrderService;
import com.group_2.util.SessionManager;
import com.model.StandingOrder;
import com.model.StandingOrderFrequency;
import com.model.User;
import com.model.WG;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class StandingOrdersDialogController {

    private final StandingOrderService standingOrderService;
    private final SessionManager sessionManager;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DecimalFormat currencyFormat = new DecimalFormat("€#,##0.00");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML
    private StackPane dialogOverlay;
    @FXML
    private TableView<StandingOrder> standingOrdersTable;
    @FXML
    private TableColumn<StandingOrder, String> descriptionColumn;
    @FXML
    private TableColumn<StandingOrder, String> payerColumn;
    @FXML
    private TableColumn<StandingOrder, String> debtorsColumn;
    @FXML
    private TableColumn<StandingOrder, String> amountColumn;
    @FXML
    private TableColumn<StandingOrder, String> frequencyColumn;
    @FXML
    private TableColumn<StandingOrder, String> nextExecutionColumn;
    @FXML
    private TableColumn<StandingOrder, Void> actionsColumn;
    @FXML
    private VBox emptyState;

    private Runnable onOrdersChanged;

    @Autowired
    public StandingOrdersDialogController(StandingOrderService standingOrderService,
            SessionManager sessionManager, UserRepository userRepository) {
        this.standingOrderService = standingOrderService;
        this.sessionManager = sessionManager;
        this.userRepository = userRepository;
    }

    @FXML
    public void initialize() {
        setupTable();
    }

    private void setupTable() {
        // Description column
        descriptionColumn
                .setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDescription()));

        // Payer column
        payerColumn.setCellValueFactory(cellData -> {
            User creditor = cellData.getValue().getCreditor();
            String name = creditor.getName();
            if (creditor.getSurname() != null && !creditor.getSurname().isEmpty()) {
                name += " " + creditor.getSurname().charAt(0) + ".";
            }
            return new SimpleStringProperty(name);
        });

        // Debtors column
        debtorsColumn.setCellValueFactory(cellData -> {
            String debtorNames = parseDebtorNames(cellData.getValue().getDebtorData());
            return new SimpleStringProperty(debtorNames);
        });

        // Amount column
        amountColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(currencyFormat.format(cellData.getValue().getTotalAmount())));

        // Frequency column
        frequencyColumn.setCellValueFactory(cellData -> {
            StandingOrder order = cellData.getValue();
            String freqText = formatFrequency(order);
            return new SimpleStringProperty(freqText);
        });

        // Next execution column
        nextExecutionColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getNextExecution().format(dateFormatter)));

        // Actions column with edit and delete buttons
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✏️ Edit");
            private final Button deleteBtn = new Button("🗑️ Delete");

            {
                editBtn.setStyle(
                        "-fx-background-color: #dbeafe; -fx-text-fill: #2563eb; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 5 10;");
                editBtn.setOnAction(e -> {
                    StandingOrder order = getTableView().getItems().get(getIndex());
                    showEditDialog(order);
                });

                deleteBtn.setStyle(
                        "-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 5 10;");
                deleteBtn.setOnAction(e -> {
                    StandingOrder order = getTableView().getItems().get(getIndex());
                    confirmAndDeleteOrder(order);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    StandingOrder order = getTableView().getItems().get(getIndex());
                    User currentUser = sessionManager.getCurrentUser();

                    // Only show edit/delete for orders created by current user
                    if (currentUser != null && order.getCreatedBy().getId().equals(currentUser.getId())) {
                        HBox buttons = new HBox(5, editBtn, deleteBtn);
                        setGraphic(buttons);
                    } else {
                        // Show only a view indicator for others' orders
                        setGraphic(null);
                    }
                }
            }
        });
    }

    private String formatFrequency(StandingOrder order) {
        StandingOrderFrequency freq = order.getFrequency();
        switch (freq) {
            case WEEKLY:
                return "Weekly";
            case BI_WEEKLY:
                return "Bi-weekly";
            case MONTHLY:
                if (Boolean.TRUE.equals(order.getMonthlyLastDay())) {
                    return "Monthly (last day)";
                } else if (order.getMonthlyDay() != null) {
                    return "Monthly (" + order.getMonthlyDay() + getDaySuffix(order.getMonthlyDay()) + ")";
                } else {
                    return "Monthly";
                }
            default:
                return freq.toString();
        }
    }

    private String getDaySuffix(int day) {
        if (day >= 11 && day <= 13)
            return "th";
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

    private String parseDebtorNames(String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        try {
            List<Map<String, Object>> debtorList = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            List<String> names = debtorList.stream()
                    .map(entry -> {
                        Object userIdObj = entry.get("userId");
                        Long userId = userIdObj instanceof Number ? ((Number) userIdObj).longValue()
                                : Long.parseLong(userIdObj.toString());
                        return userRepository.findById(userId)
                                .map(user -> {
                                    String name = user.getName();
                                    if (user.getSurname() != null && !user.getSurname().isEmpty()) {
                                        name += " " + user.getSurname().charAt(0) + ".";
                                    }
                                    return name;
                                })
                                .orElse("Unknown");
                    })
                    .collect(Collectors.toList());

            return String.join(", ", names);
        } catch (Exception e) {
            return "Error";
        }
    }

    private void confirmAndDeleteOrder(StandingOrder order) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        if (dialogOverlay.getScene() != null) {
            confirm.initOwner(dialogOverlay.getScene().getWindow());
        }
        confirm.setTitle("Delete Standing Order");
        confirm.setHeaderText("Are you sure?");
        confirm.setContentText("This will deactivate the standing order: " + order.getDescription());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            standingOrderService.deactivateStandingOrder(order.getId());
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
        WG wg = sessionManager.getCurrentUser().getWg();
        if (wg == null) {
            showEmptyState(true);
            return;
        }

        List<StandingOrder> orders = standingOrderService.getActiveStandingOrders(wg);

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

    private void showEditDialog(StandingOrder order) {
        // Create edit dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Standing Order");
        if (dialogOverlay.getScene() != null) {
            dialog.initOwner(dialogOverlay.getScene().getWindow());
        }

        // Create dialog content
        VBox content = new VBox(15);
        content.setPadding(new javafx.geometry.Insets(20));
        content.setStyle("-fx-background-color: white;");
        content.setPrefWidth(500);

        // Description field
        javafx.scene.text.Text descLabel = new javafx.scene.text.Text("Description");
        descLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        TextField descField = new TextField(order.getDescription());
        descField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        // Amount field
        javafx.scene.text.Text amountLabel = new javafx.scene.text.Text("Total Amount (€)");
        amountLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        TextField amountField = new TextField(String.format("%.2f", order.getTotalAmount()));
        amountField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        // Frequency selection
        javafx.scene.text.Text freqLabel = new javafx.scene.text.Text("Frequency");
        freqLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        ComboBox<String> freqComboBox = new ComboBox<>();
        freqComboBox.getItems().addAll("Weekly", "Bi-weekly", "Monthly");
        freqComboBox.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6;");

        // Set current frequency
        switch (order.getFrequency()) {
            case WEEKLY -> freqComboBox.setValue("Weekly");
            case BI_WEEKLY -> freqComboBox.setValue("Bi-weekly");
            case MONTHLY -> freqComboBox.setValue("Monthly");
        }

        // Monthly options
        VBox monthlyOptions = new VBox(10);
        CheckBox lastDayCheckbox = new CheckBox("Execute on last day of month");
        lastDayCheckbox.setSelected(Boolean.TRUE.equals(order.getMonthlyLastDay()));

        javafx.scene.text.Text dayLabel = new javafx.scene.text.Text("Day of month (1-31):");
        dayLabel.setStyle("-fx-font-size: 12px;");
        TextField dayField = new TextField(order.getMonthlyDay() != null ? order.getMonthlyDay().toString() : "1");
        dayField.setPrefWidth(60);
        dayField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8;");

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
        parseDebtorDataForEdit(order.getDebtorData(), originalDebtorIds, originalPercentages);

        // Split editing - only show if multiple debtors
        java.util.Map<Long, TextField> splitFields = new java.util.HashMap<>();
        final String[] currentMode = { "AMOUNT" };
        VBox splitsContainer = new VBox(8);
        javafx.scene.text.Text validationLabel = new javafx.scene.text.Text("");

        if (originalDebtorIds.size() > 1) {
            javafx.scene.text.Text splitsLabel = new javafx.scene.text.Text("Split Options");
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
            amountBtn.setSelected(true);

            modeSelector.getChildren().addAll(equalBtn, percentBtn, amountBtn);
            content.getChildren().add(modeSelector);

            splitsContainer.setStyle("-fx-background-color: #f9fafb; -fx-padding: 10; -fx-background-radius: 8;");
            content.getChildren().add(splitsContainer);

            validationLabel.setStyle("-fx-font-size: 11px; -fx-fill: #6b7280;");
            content.getChildren().add(validationLabel);

            // Build a map of userId -> User for names
            java.util.Map<Long, User> userMap = new java.util.HashMap<>();
            for (Long userId : originalDebtorIds) {
                userRepository.findById(userId).ifPresent(u -> userMap.put(userId, u));
            }

            // Function to rebuild split fields
            Runnable rebuildSplitFields = () -> {
                splitsContainer.getChildren().clear();
                splitFields.clear();

                double total;
                try {
                    total = Double.parseDouble(amountField.getText().replace(",", "."));
                } catch (NumberFormatException ex) {
                    total = order.getTotalAmount();
                }

                if (currentMode[0].equals("EQUAL")) {
                    double equalAmount = total / originalDebtorIds.size();
                    for (int i = 0; i < originalDebtorIds.size(); i++) {
                        Long userId = originalDebtorIds.get(i);
                        User user = userMap.get(userId);
                        String name = user != null
                                ? user.getName() + (user.getSurname() != null ? " " + user.getSurname() : "")
                                : "User " + userId;

                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        javafx.scene.text.Text nameText = new javafx.scene.text.Text(
                                name + ": " + String.format("%.2f", equalAmount) + "€");
                        nameText.setStyle("-fx-font-size: 12px;");
                        row.getChildren().add(nameText);
                        splitsContainer.getChildren().add(row);
                    }
                    validationLabel.setText("✓ Equal split");
                    validationLabel.setStyle("-fx-font-size: 11px; -fx-fill: #10b981; -fx-font-weight: 600;");
                } else if (currentMode[0].equals("PERCENT")) {
                    for (int i = 0; i < originalDebtorIds.size(); i++) {
                        Long userId = originalDebtorIds.get(i);
                        User user = userMap.get(userId);
                        String name = user != null
                                ? user.getName() + (user.getSurname() != null ? " " + user.getSurname() : "")
                                : "User " + userId;
                        Double pct = i < originalPercentages.size() ? originalPercentages.get(i)
                                : 100.0 / originalDebtorIds.size();

                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        javafx.scene.text.Text nameText = new javafx.scene.text.Text(name + ":");
                        nameText.setStyle("-fx-font-size: 12px;");
                        nameText.setWrappingWidth(120);

                        TextField field = new TextField(String.format("%.1f", pct));
                        field.setStyle(
                                "-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 4; -fx-padding: 6;");
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

                        javafx.scene.text.Text percentSign = new javafx.scene.text.Text("%");
                        percentSign.setStyle("-fx-font-size: 12px;");

                        row.getChildren().addAll(nameText, field, percentSign);
                        splitsContainer.getChildren().add(row);
                    }
                    double sum = originalPercentages.stream().mapToDouble(Double::doubleValue).sum();
                    double remaining = 100.0 - sum;
                    String style = Math.abs(remaining) < 0.01
                            ? "-fx-font-size: 11px; -fx-fill: #10b981; -fx-font-weight: 600;"
                            : "-fx-font-size: 11px; -fx-fill: #6b7280;";
                    validationLabel.setText(String.format("Total: %.1f%% of 100%%\n%.1f%% left", sum, remaining));
                    validationLabel.setStyle(style);
                } else { // AMOUNT
                    for (int i = 0; i < originalDebtorIds.size(); i++) {
                        Long userId = originalDebtorIds.get(i);
                        User user = userMap.get(userId);
                        String name = user != null
                                ? user.getName() + (user.getSurname() != null ? " " + user.getSurname() : "")
                                : "User " + userId;
                        Double pct = i < originalPercentages.size() ? originalPercentages.get(i)
                                : 100.0 / originalDebtorIds.size();
                        double amount = (pct / 100.0) * total;

                        HBox row = new HBox(10);
                        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                        javafx.scene.text.Text nameText = new javafx.scene.text.Text(name + ":");
                        nameText.setStyle("-fx-font-size: 12px;");
                        nameText.setWrappingWidth(120);

                        TextField field = new TextField(String.format("%.2f", amount));
                        field.setStyle(
                                "-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 4; -fx-padding: 6;");
                        field.setPrefWidth(80);
                        splitFields.put(userId, field);

                        final double totalFinal = total;
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

                        javafx.scene.text.Text euroSign = new javafx.scene.text.Text("€");
                        euroSign.setStyle("-fx-font-size: 12px;");

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
                    String style = Math.abs(remaining) < 0.01
                            ? "-fx-font-size: 11px; -fx-fill: #10b981; -fx-font-weight: 600;"
                            : "-fx-font-size: 11px; -fx-fill: #6b7280;";
                    validationLabel.setText(String.format("Total: %.2f€ of %.2f€\n%.2f€ left", sum, total, remaining));
                    validationLabel.setStyle(style);
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
            javafx.scene.text.Text debtorsLabel = new javafx.scene.text.Text(
                    "Debtor: " + parseDebtorNames(order.getDebtorData()));
            debtorsLabel.setStyle("-fx-font-size: 12px; -fx-fill: #6b7280;");
            content.getChildren().add(debtorsLabel);
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
                // Parse updated values
                String newDescription = descField.getText().trim();
                double newAmount = Double.parseDouble(amountField.getText().replace(",", "."));

                StandingOrderFrequency newFrequency = switch (freqComboBox.getValue()) {
                    case "Weekly" -> StandingOrderFrequency.WEEKLY;
                    case "Bi-weekly" -> StandingOrderFrequency.BI_WEEKLY;
                    case "Monthly" -> StandingOrderFrequency.MONTHLY;
                    default -> order.getFrequency();
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

                // Update the standing order
                standingOrderService.updateStandingOrder(
                        order.getId(),
                        sessionManager.getCurrentUser().getId(),
                        order.getCreditor(),
                        newAmount,
                        newDescription,
                        newFrequency,
                        debtorIds,
                        percentages.isEmpty() ? null : percentages,
                        newMonthlyDay,
                        newMonthlyLastDay);

                // Refresh the table
                loadStandingOrders();

                if (onOrdersChanged != null) {
                    onOrdersChanged.run();
                }

                // Show success message
                Alert success = new Alert(Alert.AlertType.INFORMATION);
                if (dialogOverlay.getScene() != null) {
                    success.initOwner(dialogOverlay.getScene().getWindow());
                }
                success.setTitle("Success");
                success.setHeaderText(null);
                success.setContentText("Standing order updated successfully.");
                success.showAndWait();

            } catch (NumberFormatException e) {
                Alert error = new Alert(Alert.AlertType.ERROR);
                if (dialogOverlay.getScene() != null) {
                    error.initOwner(dialogOverlay.getScene().getWindow());
                }
                error.setTitle("Error");
                error.setHeaderText("Invalid input");
                error.setContentText("Please enter valid numbers.");
                error.showAndWait();
            } catch (Exception e) {
                Alert error = new Alert(Alert.AlertType.ERROR);
                if (dialogOverlay.getScene() != null) {
                    error.initOwner(dialogOverlay.getScene().getWindow());
                }
                error.setTitle("Error");
                error.setHeaderText("Failed to update standing order");
                error.setContentText(e.getMessage());
                error.showAndWait();
            }
        }
    }

    private void parseDebtorDataForEdit(String json, List<Long> debtorIds, List<Double> percentages) {
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            List<Map<String, Object>> debtorList = objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    });

            for (Map<String, Object> entry : debtorList) {
                Object userIdObj = entry.get("userId");
                Object percentageObj = entry.get("percentage");

                Long userId = userIdObj instanceof Number ? ((Number) userIdObj).longValue()
                        : Long.parseLong(userIdObj.toString());
                Double percentage = percentageObj instanceof Number ? ((Number) percentageObj).doubleValue()
                        : Double.parseDouble(percentageObj.toString());

                debtorIds.add(userId);
                percentages.add(percentage);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse debtor data", e);
        }
    }
}
