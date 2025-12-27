package com.group_2.ui;

import com.group_2.service.CleaningScheduleService;
import com.group_2.util.SessionManager;
import com.model.CleaningTaskTemplate;
import com.model.RecurrenceInterval;
import com.model.Room;
import com.model.User;
import com.model.WG;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.fxml.FXML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Controller for the template editor view.
 * Allows users to define a default weekly schedule template.
 * Uses round-robin queue for automatic assignee rotation.
 */
@Component
public class TemplateEditorController extends Controller {

    private final CleaningScheduleService cleaningScheduleService;
    private final SessionManager sessionManager;

    @Autowired
    private ApplicationContext applicationContext;

    @FXML
    private Text headerTitle;
    @FXML
    private Text templateCountText;

    // Navbar
    @FXML
    private NavbarController navbarController;

    // Day columns
    @FXML
    private VBox mondayColumn;
    @FXML
    private VBox tuesdayColumn;
    @FXML
    private VBox wednesdayColumn;
    @FXML
    private VBox thursdayColumn;
    @FXML
    private VBox fridayColumn;
    @FXML
    private VBox saturdayColumn;
    @FXML
    private VBox sundayColumn;

    public TemplateEditorController(CleaningScheduleService cleaningScheduleService, SessionManager sessionManager) {
        this.cleaningScheduleService = cleaningScheduleService;
        this.sessionManager = sessionManager;
    }

    @FXML
    public void initialize() {
        if (navbarController != null) {
            navbarController.setTitle("📝 Template Editor");
            navbarController.setBackDestination("/cleaning_schedule.fxml", false);
            // Sync when back button is clicked
            navbarController.getBackButton().setOnAction(e -> backToCleaningSchedule());
        }
        loadTemplates();
    }

    private void loadTemplates() {
        // Clear all columns (keep headers)
        clearColumns();

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        WG wg = currentUser.getWg();
        List<CleaningTaskTemplate> templates = cleaningScheduleService.getTemplates(wg);

        templateCountText.setText(String.valueOf(templates.size()));

        for (CleaningTaskTemplate template : templates) {
            VBox column = getColumnForDay(template.getDayOfWeek());
            if (column != null) {
                column.getChildren().add(createTemplateCard(template, wg));
            }
        }
    }

    private void clearColumns() {
        VBox[] columns = { mondayColumn, tuesdayColumn, wednesdayColumn,
                thursdayColumn, fridayColumn, saturdayColumn, sundayColumn };
        for (VBox column : columns) {
            // Keep only the header (first child)
            if (column.getChildren().size() > 1) {
                column.getChildren().subList(1, column.getChildren().size()).clear();
            }
        }
    }

    private VBox getColumnForDay(int dayOfWeek) {
        switch (dayOfWeek) {
            case 1:
                return mondayColumn;
            case 2:
                return tuesdayColumn;
            case 3:
                return wednesdayColumn;
            case 4:
                return thursdayColumn;
            case 5:
                return fridayColumn;
            case 6:
                return saturdayColumn;
            case 7:
                return sundayColumn;
            default:
                return mondayColumn;
        }
    }

    private VBox createTemplateCard(CleaningTaskTemplate template, WG wg) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 10;");

        // Room name header
        Text roomName = new Text(template.getRoom().getName());
        roomName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-fill: #1e293b;");
        roomName.setWrappingWidth(130);

        // Show next assignee from round-robin queue
        HBox assigneeRow = new HBox(5);
        assigneeRow.setAlignment(Pos.CENTER_LEFT);

        User nextAssignee = cleaningScheduleService.getNextAssigneeForRoom(wg, template.getRoom());

        if (nextAssignee != null) {
            StackPane avatar = new StackPane();
            avatar.setStyle("-fx-background-color: #ddd6fe; -fx-background-radius: 10; " +
                    "-fx-pref-width: 20; -fx-pref-height: 20; -fx-min-width: 20; -fx-min-height: 20;");
            Text avatarText = new Text(nextAssignee.getName().substring(0, 1).toUpperCase());
            avatarText.setStyle("-fx-font-size: 10px; -fx-fill: #7c3aed;");
            avatar.getChildren().add(avatarText);

            Text nextLabel = new Text("Next: ");
            nextLabel.setStyle("-fx-font-size: 11px; -fx-fill: #64748b;");

            Text assigneeName = new Text(nextAssignee.getName());
            assigneeName.setStyle("-fx-font-size: 11px; -fx-fill: #7c3aed; -fx-font-weight: bold;");

            assigneeRow.getChildren().addAll(avatar, nextLabel, assigneeName);
        } else {
            Text rotationInfo = new Text("🔄 Round-robin");
            rotationInfo.setStyle("-fx-font-size: 11px; -fx-fill: #64748b;");
            assigneeRow.getChildren().add(rotationInfo);
        }

        // Frequency display
        HBox frequencyRow = new HBox(5);
        frequencyRow.setAlignment(Pos.CENTER_LEFT);
        Text freqIcon = new Text("📅");
        freqIcon.setStyle("-fx-font-size: 11px;");
        Text freqText = new Text(template.getRecurrenceInterval().getDisplayName());
        freqText.setStyle("-fx-font-size: 11px; -fx-fill: #64748b;");
        frequencyRow.getChildren().addAll(freqIcon, freqText);

        // Separator
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setStyle("-fx-background-color: #e2e8f0;");

        // Action buttons - stacked vertically for better visibility
        VBox actions = new VBox(6);
        actions.setAlignment(Pos.CENTER);

        Button editBtn = new Button("✏️ Edit Day");
        editBtn.setMaxWidth(Double.MAX_VALUE);
        editBtn.setStyle("-fx-background-color: #e0e7ff; -fx-text-fill: #4338ca; -fx-font-size: 12px; " +
                "-fx-cursor: hand; -fx-padding: 8 12; -fx-background-radius: 6;");
        editBtn.setTooltip(new Tooltip("Edit day of week"));
        editBtn.setOnAction(e -> showEditTemplateDialog(template));

        Button deleteBtn = new Button("🗑️ Delete");
        deleteBtn.setMaxWidth(Double.MAX_VALUE);
        deleteBtn.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-font-size: 12px; " +
                "-fx-cursor: hand; -fx-padding: 8 12; -fx-background-radius: 6;");
        deleteBtn.setTooltip(new Tooltip("Delete this task"));
        deleteBtn.setOnAction(e -> deleteTemplate(template));

        actions.getChildren().addAll(editBtn, deleteBtn);

        card.getChildren().addAll(roomName, assigneeRow, frequencyRow, separator, actions);
        return card;
    }

    @FXML
    public void showAddTemplateDialog() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "You must be in a WG.", getOwnerWindow(headerTitle));
            return;
        }

        WG wg = currentUser.getWg();
        if (wg.rooms == null || wg.rooms.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Rooms", "Please add rooms first in the Dashboard.",
                    getOwnerWindow(headerTitle));
            return;
        }

        if (wg.getMitbewohner().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Members", "WG has no members.",
                    getOwnerWindow(headerTitle));
            return;
        }

        Dialog<CleaningTaskTemplate> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(headerTitle));
        dialog.setTitle("Add Template Task");
        dialog.setHeaderText("Add a room to the cleaning schedule");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Room selection
        ComboBox<Room> roomCombo = new ComboBox<>();
        roomCombo.getItems().addAll(wg.rooms);
        roomCombo.setPromptText("Select a room");
        roomCombo.setConverter(new javafx.util.StringConverter<Room>() {
            @Override
            public String toString(Room room) {
                return room != null ? room.getName() : "";
            }

            @Override
            public Room fromString(String s) {
                return null;
            }
        });

        // Day selection
        ComboBox<DayOfWeek> dayCombo = new ComboBox<>();
        dayCombo.getItems().addAll(DayOfWeek.values());
        dayCombo.setPromptText("Day of week");
        dayCombo.setConverter(new javafx.util.StringConverter<DayOfWeek>() {
            @Override
            public String toString(DayOfWeek d) {
                if (d == null)
                    return "";
                return d.toString().substring(0, 1) + d.toString().substring(1).toLowerCase();
            }

            @Override
            public DayOfWeek fromString(String s) {
                return null;
            }
        });

        // Frequency selection
        ComboBox<RecurrenceInterval> freqCombo = new ComboBox<>();
        freqCombo.getItems().addAll(RecurrenceInterval.values());
        freqCombo.setValue(RecurrenceInterval.WEEKLY);
        freqCombo.setPromptText("Frequency");

        // Info about round-robin
        HBox infoBox = new HBox(8);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 8; -fx-padding: 10;");
        Text infoIcon = new Text("🔄");
        infoIcon.setStyle("-fx-font-size: 14px;");
        Text infoText = new Text("Assignees rotate automatically each week");
        infoText.setStyle("-fx-font-size: 12px; -fx-fill: #166534;");
        infoBox.getChildren().addAll(infoIcon, infoText);

        content.getChildren().addAll(
                new Text("Room:"), roomCombo,
                new Text("Day:"), dayCombo,
                new Text("Frequency:"), freqCombo,
                infoBox);

        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().lookupButton(addButtonType).setDisable(true);
        roomCombo.valueProperty().addListener(
                (o, oldV, newV) -> updateAddButton(dialog, addButtonType, roomCombo, dayCombo));
        dayCombo.valueProperty().addListener(
                (o, oldV, newV) -> updateAddButton(dialog, addButtonType, roomCombo, dayCombo));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return cleaningScheduleService.addTemplate(
                        wg, roomCombo.getValue(), dayCombo.getValue(), freqCombo.getValue());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(t -> loadTemplates());
    }

    private void updateAddButton(Dialog<?> dialog, ButtonType btnType, ComboBox<Room> room,
            ComboBox<DayOfWeek> day) {
        dialog.getDialogPane().lookupButton(btnType).setDisable(
                room.getValue() == null || day.getValue() == null);
    }

    private void showEditTemplateDialog(CleaningTaskTemplate template) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        Dialog<Void> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(headerTitle));
        dialog.setTitle("Edit Template");
        dialog.setHeaderText("Edit \"" + template.getRoom().getName() + "\"");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Day selection
        ComboBox<DayOfWeek> dayCombo = new ComboBox<>();
        dayCombo.getItems().addAll(DayOfWeek.values());
        dayCombo.setValue(DayOfWeek.of(template.getDayOfWeek()));
        dayCombo.setConverter(new javafx.util.StringConverter<DayOfWeek>() {
            @Override
            public String toString(DayOfWeek d) {
                if (d == null)
                    return "";
                return d.toString().substring(0, 1) + d.toString().substring(1).toLowerCase();
            }

            @Override
            public DayOfWeek fromString(String s) {
                return null;
            }
        });

        // Frequency selection
        ComboBox<RecurrenceInterval> freqCombo = new ComboBox<>();
        freqCombo.getItems().addAll(RecurrenceInterval.values());
        freqCombo.setValue(template.getRecurrenceInterval());

        // Info about round-robin
        HBox infoBox = new HBox(8);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 8; -fx-padding: 10;");
        Text infoIcon = new Text("🔄");
        infoIcon.setStyle("-fx-font-size: 14px;");
        Text infoText = new Text("Assignees rotate automatically - no need to change");
        infoText.setStyle("-fx-font-size: 12px; -fx-fill: #166534;");
        infoBox.getChildren().addAll(infoIcon, infoText);

        content.getChildren().addAll(
                new Text("Day of Week:"), dayCombo,
                new Text("Frequency:"), freqCombo,
                infoBox);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                cleaningScheduleService.updateTemplate(template, dayCombo.getValue(), freqCombo.getValue());
            }
            return null;
        });

        dialog.showAndWait();
        loadTemplates();
    }

    private void deleteTemplate(CleaningTaskTemplate template) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        configureDialogOwner(confirm, getOwnerWindow(headerTitle));
        confirm.setTitle("Delete Template");
        confirm.setHeaderText("Delete \"" + template.getRoom().getName() + "\"?");
        confirm.setContentText("This will remove it from the default schedule.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                cleaningScheduleService.deleteTemplate(template);
                loadTemplates();
            }
        });
    }

    @FXML
    public void clearAllTemplates() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        configureDialogOwner(confirm, getOwnerWindow(headerTitle));
        confirm.setTitle("Clear All");
        confirm.setHeaderText("Clear all template tasks?");
        confirm.setContentText("This will remove the entire default schedule.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                cleaningScheduleService.clearTemplates(currentUser.getWg());
                loadTemplates();
            }
        });
    }

    @FXML
    public void backToHome() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null && currentUser.getWg() != null) {
            cleaningScheduleService.syncCurrentWeekWithTemplate(currentUser.getWg());
        }

        loadScene(headerTitle.getScene(), "/main_screen.fxml");
        javafx.application.Platform.runLater(() -> {
            MainScreenController controller = applicationContext.getBean(MainScreenController.class);
            controller.initView();
        });
    }

    @FXML
    public void backToCleaningSchedule() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null && currentUser.getWg() != null) {
            cleaningScheduleService.syncCurrentWeekWithTemplate(currentUser.getWg());
        }
        loadScene(headerTitle.getScene(), "/cleaning_schedule.fxml");
    }

    @FXML
    public void goToCleaningSchedule() {
        backToCleaningSchedule();
    }
}
