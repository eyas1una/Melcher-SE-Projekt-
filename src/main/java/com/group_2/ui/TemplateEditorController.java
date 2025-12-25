package com.group_2.ui;

import com.group_2.service.CleaningScheduleService;
import com.group_2.util.SessionManager;
import com.model.CleaningTaskTemplate;
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
 */
@Component
public class TemplateEditorController extends Controller {

    private final CleaningScheduleService cleaningScheduleService;
    private final SessionManager sessionManager;

    @Autowired
    private ApplicationContext applicationContext;

    @FXML
    private Text avatarInitial;
    @FXML
    private Text userNameText;
    @FXML
    private Text userEmailText;
    @FXML
    private Text templateCountText;

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
        updateSidebarUserInfo();
        loadTemplates();
    }

    private void updateSidebarUserInfo() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            String initial = currentUser.getName() != null && !currentUser.getName().isEmpty()
                    ? currentUser.getName().substring(0, 1).toUpperCase()
                    : "?";
            avatarInitial.setText(initial);
            userNameText.setText(currentUser.getName() +
                    (currentUser.getSurname() != null ? " " + currentUser.getSurname() : ""));
            userEmailText.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "");
        }
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
                column.getChildren().add(createTemplateCard(template));
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

    private VBox createTemplateCard(CleaningTaskTemplate template) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 8;");

        Text roomName = new Text(template.getRoom().getName());
        roomName.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-fill: #1e293b;");

        HBox assigneeRow = new HBox(5);
        assigneeRow.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = new StackPane();
        avatar.setStyle("-fx-background-color: #e2e8f0; -fx-background-radius: 8; " +
                "-fx-pref-width: 16; -fx-pref-height: 16;");
        Text avatarText = new Text(template.getDefaultAssignee().getName().substring(0, 1).toUpperCase());
        avatarText.setStyle("-fx-font-size: 8px; -fx-fill: #64748b;");
        avatar.getChildren().add(avatarText);

        Text assigneeName = new Text(template.getDefaultAssignee().getName());
        assigneeName.setStyle("-fx-font-size: 10px; -fx-fill: #64748b;");

        assigneeRow.getChildren().addAll(avatar, assigneeName);

        // Action buttons
        HBox actions = new HBox(5);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button editBtn = new Button("✏");
        editBtn.setStyle("-fx-background-color: transparent; -fx-font-size: 10px; -fx-cursor: hand; -fx-padding: 2;");
        editBtn.setTooltip(new Tooltip("Edit"));
        editBtn.setOnAction(e -> showEditTemplateDialog(template));

        Button deleteBtn = new Button("🗑");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-font-size: 10px; -fx-cursor: hand; -fx-padding: 2;");
        deleteBtn.setTooltip(new Tooltip("Delete"));
        deleteBtn.setOnAction(e -> deleteTemplate(template));

        actions.getChildren().addAll(editBtn, deleteBtn);

        card.getChildren().addAll(roomName, assigneeRow, actions);
        return card;
    }

    @FXML
    public void showAddTemplateDialog() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "You must be in a WG.");
            return;
        }

        WG wg = currentUser.getWg();
        if (wg.rooms == null || wg.rooms.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Rooms", "Please add rooms first in the Dashboard.");
            return;
        }

        Dialog<CleaningTaskTemplate> dialog = new Dialog<>();
        dialog.setTitle("Add Template Task");
        dialog.setHeaderText("Add a task to the default schedule");

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

        // Assignee selection
        ComboBox<User> assigneeCombo = new ComboBox<>();
        assigneeCombo.getItems().addAll(wg.getMitbewohner());
        assigneeCombo.setPromptText("Default assignee");
        assigneeCombo.setConverter(new javafx.util.StringConverter<User>() {
            @Override
            public String toString(User user) {
                return user != null ? user.getName() + (user.getSurname() != null ? " " + user.getSurname() : "") : "";
            }

            @Override
            public User fromString(String s) {
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

        content.getChildren().addAll(
                new Text("Room:"), roomCombo,
                new Text("Default Assignee:"), assigneeCombo,
                new Text("Day:"), dayCombo);

        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().lookupButton(addButtonType).setDisable(true);
        roomCombo.valueProperty().addListener(
                (o, oldV, newV) -> updateAddButton(dialog, addButtonType, roomCombo, assigneeCombo, dayCombo));
        assigneeCombo.valueProperty().addListener(
                (o, oldV, newV) -> updateAddButton(dialog, addButtonType, roomCombo, assigneeCombo, dayCombo));
        dayCombo.valueProperty().addListener(
                (o, oldV, newV) -> updateAddButton(dialog, addButtonType, roomCombo, assigneeCombo, dayCombo));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return cleaningScheduleService.addTemplate(
                        wg, roomCombo.getValue(), assigneeCombo.getValue(), dayCombo.getValue());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(t -> loadTemplates());
    }

    private void updateAddButton(Dialog<?> dialog, ButtonType btnType, ComboBox<Room> room, ComboBox<User> user,
            ComboBox<DayOfWeek> day) {
        dialog.getDialogPane().lookupButton(btnType).setDisable(
                room.getValue() == null || user.getValue() == null || day.getValue() == null);
    }

    private void showEditTemplateDialog(CleaningTaskTemplate template) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        WG wg = currentUser.getWg();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Edit Template");
        dialog.setHeaderText("Edit \"" + template.getRoom().getName() + "\"");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Assignee selection
        ComboBox<User> assigneeCombo = new ComboBox<>();
        assigneeCombo.getItems().addAll(wg.getMitbewohner());
        assigneeCombo.setValue(template.getDefaultAssignee());
        assigneeCombo.setConverter(new javafx.util.StringConverter<User>() {
            @Override
            public String toString(User user) {
                return user != null ? user.getName() + (user.getSurname() != null ? " " + user.getSurname() : "") : "";
            }

            @Override
            public User fromString(String s) {
                return null;
            }
        });

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

        content.getChildren().addAll(
                new Text("Assignee:"), assigneeCombo,
                new Text("Day:"), dayCombo);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                cleaningScheduleService.updateTemplate(template, assigneeCombo.getValue(), dayCombo.getValue());
            }
            return null;
        });

        dialog.showAndWait();
        loadTemplates();
    }

    private void deleteTemplate(CleaningTaskTemplate template) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
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
        loadScene(avatarInitial.getScene(), "/main_screen.fxml");
        javafx.application.Platform.runLater(() -> {
            MainScreenController controller = applicationContext.getBean(MainScreenController.class);
            controller.initView();
        });
    }

    @FXML
    public void goToCleaningSchedule() {
        loadScene(avatarInitial.getScene(), "/cleaning_schedule.fxml");
    }
}
