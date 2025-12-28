package com.group_2.ui;

import com.group_2.service.CleaningScheduleService;
import com.group_2.util.SessionManager;
import com.model.CleaningTask;
import com.model.Room;
import com.model.User;
import com.model.WG;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.fxml.FXML;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

/**
 * Controller for the cleaning schedule view with calendar-style layout.
 */
@Component
public class CleaningScheduleController extends Controller {

    private final CleaningScheduleService cleaningScheduleService;
    private final SessionManager sessionManager;

    // Current displayed week
    private LocalDate displayedWeekStart;

    // Header elements
    @FXML
    private Text weekTitle;
    @FXML
    private Text weekDateRange;
    @FXML
    private Text completedTasksText;
    @FXML
    private Text myTasksCountText;

    // Calendar containers
    @FXML
    private HBox calendarDaysContainer;
    @FXML
    private HBox roomCardsContainer;

    // Navbar
    @FXML
    private NavbarController navbarController;

    public CleaningScheduleController(CleaningScheduleService cleaningScheduleService, SessionManager sessionManager) {
        this.cleaningScheduleService = cleaningScheduleService;
        this.sessionManager = sessionManager;
    }

    @FXML
    public void initialize() {
        if (navbarController != null) {
            navbarController.setTitle("🧹 Cleaning Schedule");
        }
        displayedWeekStart = cleaningScheduleService.getCurrentWeekStart();
        refreshView();
    }

    private void refreshView() {
        updateWeekDisplay();
        loadCalendarDays();
        loadRoomCards();
        updateStats();
    }

    private void updateWeekDisplay() {
        LocalDate weekEnd = displayedWeekStart.plusDays(6);

        // Week number
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int weekNumber = displayedWeekStart.get(weekFields.weekOfWeekBasedYear());
        int year = displayedWeekStart.getYear();
        weekTitle.setText("Week " + weekNumber + ", " + year);

        // Date range
        DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("MMMM d");
        String dateRange = displayedWeekStart.format(fullFormatter) + " - " + weekEnd.format(fullFormatter);
        weekDateRange.setText(dateRange);
    }

    private void loadCalendarDays() {
        calendarDaysContainer.getChildren().clear();

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        WG wg = currentUser.getWg();
        List<CleaningTask> weekTasks = cleaningScheduleService.getTasksForWeek(wg, displayedWeekStart);
        LocalDate today = LocalDate.now();

        // Create 7 day cells
        for (int i = 0; i < 7; i++) {
            LocalDate day = displayedWeekStart.plusDays(i);
            VBox dayCell = createDayCell(day, weekTasks, today, currentUser);
            calendarDaysContainer.getChildren().add(dayCell);
        }
    }

    private VBox createDayCell(LocalDate day, List<CleaningTask> weekTasks, LocalDate today, User currentUser) {
        VBox cell = new VBox(8);
        cell.setPrefWidth(130);
        cell.setMinHeight(150);
        cell.setPadding(new Insets(10));

        boolean isToday = day.equals(today);
        boolean isWeekend = day.getDayOfWeek().getValue() >= 6;

        // Style based on day type
        String bgColor = isToday ? "#eef2ff" : (isWeekend ? "#f8fafc" : "white");
        String borderColor = isToday ? "#6366f1" : "#e2e8f0";
        cell.setStyle("-fx-background-color: " + bgColor + "; " +
                "-fx-border-color: " + borderColor + "; " +
                "-fx-border-width: 1;");

        // Day number header
        HBox header = new HBox(5);
        header.setAlignment(Pos.CENTER_LEFT);
        Text dayNumber = new Text(String.valueOf(day.getDayOfMonth()));
        dayNumber.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-fill: " +
                (isToday ? "#6366f1" : "#1e293b") + ";");
        header.getChildren().add(dayNumber);

        if (isToday) {
            StackPane todayBadge = new StackPane();
            todayBadge.setStyle("-fx-background-color: #6366f1; -fx-background-radius: 8; -fx-padding: 2 6;");
            Text todayText = new Text("Today");
            todayText.setStyle("-fx-font-size: 9px; -fx-fill: white;");
            todayBadge.getChildren().add(todayText);
            header.getChildren().add(todayBadge);
        }

        cell.getChildren().add(header);

        // Show tasks that are due on this specific day
        for (CleaningTask task : weekTasks) {
            LocalDate taskDueDate = task.getDueDate();
            // If no dueDate is set, fall back to weekStartDate
            if (taskDueDate == null) {
                taskDueDate = task.getWeekStartDate();
            }
            if (taskDueDate.equals(day)) {
                HBox taskPill = createTaskPill(task, currentUser);
                cell.getChildren().add(taskPill);
            }
        }

        return cell;
    }

    private HBox createTaskPill(CleaningTask task, User currentUser) {
        HBox pill = new HBox(5);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.setPadding(new Insets(4, 8, 4, 8));
        pill.setCursor(javafx.scene.Cursor.HAND);

        boolean isMyTask = task.getAssignee().getId().equals(currentUser.getId());
        // User's own tasks get a distinct orange/amber highlight
        String bgColor = task.isCompleted() ? "#dcfce7" : (isMyTask ? "#fef3c7" : "#f1f5f9");
        String textColor = task.isCompleted() ? "#15803d" : (isMyTask ? "#b45309" : "#475569");

        pill.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 6;" +
                (isMyTask && !task.isCompleted()
                        ? " -fx-border-color: #f59e0b; -fx-border-width: 1.5; -fx-border-radius: 6;"
                        : ""));

        Text roomIcon = new Text(task.isCompleted() ? "✓" : "🚪");
        roomIcon.setStyle("-fx-font-size: 10px;");

        Text roomName = new Text(truncate(task.getRoom().getName(), 10));
        roomName.setStyle("-fx-font-size: 10px; -fx-fill: " + textColor + ";");

        pill.getChildren().addAll(roomIcon, roomName);

        // Click to toggle complete
        pill.setOnMouseClicked(e -> {
            if (task.isCompleted()) {
                cleaningScheduleService.markTaskIncomplete(task);
            } else {
                cleaningScheduleService.markTaskComplete(task);
            }
            refreshView();
        });

        return pill;
    }

    private void loadRoomCards() {
        roomCardsContainer.getChildren().clear();

        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null) {
            showEmptyState();
            return;
        }

        WG wg = currentUser.getWg();
        List<CleaningTask> weekTasks = cleaningScheduleService.getTasksForWeek(wg, displayedWeekStart);

        if (weekTasks.isEmpty()) {
            showEmptyState();
            return;
        }

        // Create a card for each task
        for (CleaningTask task : weekTasks) {
            VBox card = createRoomCard(task, currentUser);
            roomCardsContainer.getChildren().add(card);
        }
    }

    private VBox createRoomCard(CleaningTask task, User currentUser) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20, 15, 20, 15));
        card.setPrefWidth(220);
        card.setAlignment(Pos.TOP_CENTER);

        boolean isCompleted = task.isCompleted();
        boolean isMyTask = task.getAssignee().getId().equals(currentUser.getId());

        // User's own tasks get a distinct orange/amber highlight
        String bgColor = isCompleted ? "#f0fdf4" : (isMyTask ? "#fffbeb" : "white");
        String borderColor = isCompleted ? "#86efac" : (isMyTask ? "#f59e0b" : "#e2e8f0");
        String borderWidth = isMyTask && !isCompleted ? "3" : "2";

        card.setStyle("-fx-background-color: " + bgColor + "; " +
                "-fx-background-radius: 16; " +
                "-fx-border-color: " + borderColor + "; " +
                "-fx-border-radius: 16; " +
                "-fx-border-width: " + borderWidth + "; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 10, 0, 0, 2);");

        // Room icon
        StackPane iconPane = new StackPane();
        String iconBg = isCompleted
                ? "linear-gradient(to bottom right, #10b981, #059669)"
                : "linear-gradient(to bottom right, #6366f1, #8b5cf6)";
        iconPane.setStyle("-fx-background-color: " + iconBg + "; " +
                "-fx-background-radius: 14; " +
                "-fx-pref-width: 56; -fx-pref-height: 56; " +
                "-fx-min-width: 56; -fx-min-height: 56;");
        Text iconText = new Text(isCompleted ? "✓" : "🚪");
        iconText.setStyle("-fx-font-size: 24px;");
        iconPane.getChildren().add(iconText);

        // Room name
        Text roomName = new Text(task.getRoom().getName());
        roomName.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-fill: #1e293b;");
        if (isCompleted) {
            roomName.setStyle(roomName.getStyle() + " -fx-strikethrough: true;");
        }

        // Assignee
        HBox assigneeBox = new HBox(6);
        assigneeBox.setAlignment(Pos.CENTER);

        StackPane assigneeAvatar = new StackPane();
        assigneeAvatar.setStyle("-fx-background-color: #e2e8f0; -fx-background-radius: 10; " +
                "-fx-pref-width: 20; -fx-pref-height: 20;");
        String assigneeInitial = task.getAssignee().getName().substring(0, 1).toUpperCase();
        Text avatarText = new Text(assigneeInitial);
        avatarText.setStyle("-fx-font-size: 10px; -fx-fill: #64748b;");
        assigneeAvatar.getChildren().add(avatarText);

        String assigneeName = isMyTask ? "You" : task.getAssignee().getName();
        Text assigneeText = new Text(assigneeName);
        assigneeText.setStyle("-fx-font-size: 12px; -fx-fill: " + (isMyTask ? "#4338ca" : "#64748b") + "; " +
                (isMyTask ? "-fx-font-weight: bold;" : ""));

        assigneeBox.getChildren().addAll(assigneeAvatar, assigneeText);

        // Due date
        LocalDate dueDate = task.getDueDate() != null ? task.getDueDate() : task.getWeekStartDate();
        String dayName = dueDate.getDayOfWeek().toString().substring(0, 1) +
                dueDate.getDayOfWeek().toString().substring(1).toLowerCase();
        Text dueDateText = new Text("📅 " + dayName + ", " + dueDate.getDayOfMonth());
        dueDateText.setStyle("-fx-font-size: 11px; -fx-fill: #64748b;");

        // Status badge
        HBox statusBadge = new HBox(4);
        statusBadge.setAlignment(Pos.CENTER);
        statusBadge.setPadding(new Insets(4, 10, 4, 10));
        String badgeBg = isCompleted ? "#dcfce7" : "#fef3c7";
        String badgeColor = isCompleted ? "#15803d" : "#b45309";
        statusBadge.setStyle("-fx-background-color: " + badgeBg + "; -fx-background-radius: 12;");

        Text statusText = new Text(isCompleted ? "✓ Completed" : "⏳ Pending");
        statusText.setStyle("-fx-font-size: 11px; -fx-fill: " + badgeColor + "; -fx-font-weight: bold;");
        statusBadge.getChildren().add(statusText);

        // Action buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);

        Button completeBtn = new Button(isCompleted ? "↩ Undo" : "✓ Done");
        completeBtn.setStyle("-fx-background-color: " + (isCompleted ? "#f1f5f9" : "#10b981") + "; " +
                "-fx-text-fill: " + (isCompleted ? "#475569" : "white") + "; " +
                "-fx-background-radius: 8; -fx-padding: 6 12; -fx-font-size: 11px; -fx-cursor: hand;");
        completeBtn.setOnAction(e -> {
            if (isCompleted) {
                cleaningScheduleService.markTaskIncomplete(task);
            } else {
                cleaningScheduleService.markTaskComplete(task);
            }
            refreshView();
        });

        actions.getChildren().add(completeBtn);

        // Only show reassign button for your own tasks
        if (isMyTask) {
            Button reassignBtn = new Button("👤");
            reassignBtn.getStyleClass().add("task-action-button");
            reassignBtn.setMinWidth(Region.USE_PREF_SIZE);
            reassignBtn.setTooltip(new Tooltip("Reassign to someone else"));
            reassignBtn.setOnAction(e -> showReassignDialog(task));
            actions.getChildren().add(reassignBtn);
        }

        Button rescheduleBtn = new Button("📅");
        rescheduleBtn.getStyleClass().add("task-action-button");
        rescheduleBtn.setMinWidth(Region.USE_PREF_SIZE);
        rescheduleBtn.setTooltip(new Tooltip("Reschedule"));
        rescheduleBtn.setOnAction(e -> showRescheduleDialog(task));

        actions.getChildren().add(rescheduleBtn);

        card.getChildren().addAll(iconPane, roomName, assigneeBox, dueDateText, statusBadge, actions);
        return card;
    }

    private void showEmptyState() {
        VBox emptyState = new VBox(15);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setPadding(new Insets(40));
        emptyState.setPrefWidth(400);
        emptyState.setStyle("-fx-background-color: white; -fx-background-radius: 16;");

        Text emptyIcon = new Text("📋");
        emptyIcon.setStyle("-fx-font-size: 48px;");

        Text emptyTitle = new Text("No Tasks This Week");
        emptyTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-fill: #1e293b;");

        Text emptySubtitle = new Text("Add tasks manually to create a schedule");
        emptySubtitle.setStyle("-fx-font-size: 13px; -fx-fill: #64748b; -fx-text-alignment: center;");

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);

        Button addBtn = new Button("➕ Add Task");
        addBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; " +
                "-fx-background-radius: 8; -fx-padding: 10 20; -fx-cursor: hand;");
        addBtn.setOnAction(e -> showAddTaskDialog());

        buttons.getChildren().addAll(addBtn);
        emptyState.getChildren().addAll(emptyIcon, emptyTitle, emptySubtitle, buttons);
        roomCardsContainer.getChildren().add(emptyState);
    }

    private void updateStats() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null) {
            completedTasksText.setText("0/0");
            myTasksCountText.setText("0");
            return;
        }

        WG wg = currentUser.getWg();
        List<CleaningTask> weekTasks = cleaningScheduleService.getTasksForWeek(wg, displayedWeekStart);

        int total = weekTasks.size();
        int completed = (int) weekTasks.stream().filter(CleaningTask::isCompleted).count();
        int myTasks = (int) weekTasks.stream()
                .filter(t -> t.getAssignee().getId().equals(currentUser.getId()))
                .count();

        completedTasksText.setText(completed + "/" + total);
        myTasksCountText.setText(String.valueOf(myTasks));
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength - 1) + "…";
    }

    // Week navigation
    @FXML
    public void previousWeek() {
        displayedWeekStart = displayedWeekStart.minusWeeks(1);
        refreshView();
    }

    @FXML
    public void nextWeek() {
        displayedWeekStart = displayedWeekStart.plusWeeks(1);
        refreshView();
    }

    @FXML
    public void goToCurrentWeek() {
        displayedWeekStart = cleaningScheduleService.getCurrentWeekStart();
        refreshView();
    }

    @FXML
    public void showAddTaskDialog() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "You must be in a WG to add tasks.", getOwnerWindow(weekTitle));
            return;
        }

        WG wg = currentUser.getWg();
        if (wg.rooms == null || wg.rooms.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Rooms", "Please add rooms first.", getOwnerWindow(weekTitle));
            return;
        }

        Dialog<CleaningTask> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(weekTitle));
        dialog.setTitle("Add Cleaning Task");
        dialog.setHeaderText("Assign a room to a member");

        ButtonType addButtonType = new ButtonType("Add Task", ButtonBar.ButtonData.OK_DONE);
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
            public Room fromString(String string) {
                return null;
            }
        });

        // Assignee selection
        ComboBox<User> assigneeCombo = new ComboBox<>();
        assigneeCombo.getItems().addAll(wg.getMitbewohner());
        assigneeCombo.setPromptText("Assign to...");
        assigneeCombo.setConverter(new javafx.util.StringConverter<User>() {
            @Override
            public String toString(User user) {
                return user != null ? user.getName() + (user.getSurname() != null ? " " + user.getSurname() : "") : "";
            }

            @Override
            public User fromString(String string) {
                return null;
            }
        });

        content.getChildren().addAll(
                new Text("Room:"), roomCombo,
                new Text("Assign to:"), assigneeCombo);

        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().lookupButton(addButtonType).setDisable(true);
        roomCombo.valueProperty().addListener((obs, oldVal, newVal) -> dialog.getDialogPane()
                .lookupButton(addButtonType).setDisable(newVal == null || assigneeCombo.getValue() == null));
        assigneeCombo.valueProperty().addListener((obs, oldVal, newVal) -> dialog.getDialogPane()
                .lookupButton(addButtonType).setDisable(newVal == null || roomCombo.getValue() == null));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return cleaningScheduleService.assignTask(roomCombo.getValue(), assigneeCombo.getValue(), wg);
            }
            return null;
        });

        dialog.showAndWait().ifPresent(task -> refreshView());
    }

    private void showReassignDialog(CleaningTask task) {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null)
            return;

        Dialog<User> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(weekTitle));
        dialog.setTitle("Reassign Task");
        dialog.setHeaderText("Reassign \"" + task.getRoom().getName() + "\"");

        ButtonType assignButtonType = new ButtonType("Reassign", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(assignButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        ToggleGroup group = new ToggleGroup();
        for (User member : currentUser.getWg().getMitbewohner()) {
            RadioButton rb = new RadioButton(member.getName() +
                    (member.getSurname() != null ? " " + member.getSurname() : ""));
            rb.setUserData(member);
            rb.setToggleGroup(group);
            if (member.getId().equals(task.getAssignee().getId())) {
                rb.setSelected(true);
            }
            content.getChildren().add(rb);
        }

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == assignButtonType && group.getSelectedToggle() != null) {
                return (User) group.getSelectedToggle().getUserData();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newAssignee -> {
            cleaningScheduleService.reassignTask(task, newAssignee);
            refreshView();
        });
    }

    private void showRescheduleDialog(CleaningTask task) {
        Dialog<LocalDate> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(weekTitle));
        dialog.setTitle("Reschedule Task");
        dialog.setHeaderText("Reschedule \"" + task.getRoom().getName() + "\"");

        ButtonType rescheduleButtonType = new ButtonType("Reschedule", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(rescheduleButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Text label = new Text("Select new day:");
        label.setStyle("-fx-font-size: 13px;");
        content.getChildren().add(label);

        ToggleGroup group = new ToggleGroup();
        String[] dayNames = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };

        LocalDate currentDueDate = task.getDueDate() != null ? task.getDueDate() : displayedWeekStart;
        int currentDayIndex = currentDueDate.getDayOfWeek().getValue() - 1;

        for (int i = 0; i < 7; i++) {
            LocalDate day = displayedWeekStart.plusDays(i);
            RadioButton rb = new RadioButton(dayNames[i] + " (" + day.getDayOfMonth() + ")");
            rb.setUserData(day);
            rb.setToggleGroup(group);
            if (i == currentDayIndex) {
                rb.setSelected(true);
            }
            content.getChildren().add(rb);
        }

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == rescheduleButtonType && group.getSelectedToggle() != null) {
                return (LocalDate) group.getSelectedToggle().getUserData();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newDueDate -> {
            cleaningScheduleService.rescheduleTask(task, newDueDate);
            refreshView();
        });
    }

    @FXML
    public void saveAsTemplate() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "You must be in a WG.", getOwnerWindow(weekTitle));
            return;
        }

        WG wg = currentUser.getWg();
        if (cleaningScheduleService.getTasksForWeek(wg, displayedWeekStart).isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Tasks",
                    "Create a schedule first before saving it as a template.", getOwnerWindow(weekTitle));
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        configureDialogOwner(confirm, getOwnerWindow(weekTitle));
        confirm.setTitle("Save Template");
        confirm.setHeaderText("Save current week as default template?");
        confirm.setContentText("This will overwrite any existing template.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                cleaningScheduleService.saveAsTemplate(wg);
                showAlert(Alert.AlertType.INFORMATION, "Saved",
                        "Template saved! Use 'Load Default' to apply it to new weeks.", getOwnerWindow(weekTitle));
            }
        });
    }

    @FXML
    public void loadFromTemplate() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "You must be in a WG.", getOwnerWindow(weekTitle));
            return;
        }

        WG wg = currentUser.getWg();
        if (!cleaningScheduleService.hasTemplate(wg)) {
            showAlert(Alert.AlertType.WARNING, "No Template",
                    "No default template found. First create a schedule and save it as template.",
                    getOwnerWindow(weekTitle));
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        configureDialogOwner(confirm, getOwnerWindow(weekTitle));
        confirm.setTitle("Load Template");
        confirm.setHeaderText("Load default template for this week?");
        confirm.setContentText("This will replace any existing tasks for this week.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                cleaningScheduleService.generateFromTemplate(wg);
                refreshView();
            }
        });
    }

    @FXML
    public void openTemplateEditor() {
        loadScene(weekTitle.getScene(), "/template_editor.fxml");
    }

    @FXML
    public void handleLogout() {
        sessionManager.clear();
        loadScene(weekTitle.getScene(), "/login.fxml");
    }

}
