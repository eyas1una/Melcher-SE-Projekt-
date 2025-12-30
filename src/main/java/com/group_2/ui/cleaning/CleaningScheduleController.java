package com.group_2.ui.cleaning;

import com.group_2.dto.cleaning.RoomDTO;
import com.group_2.dto.cleaning.CleaningTaskDTO;
import com.group_2.dto.core.UserSummaryDTO;
import com.group_2.service.cleaning.CleaningScheduleService;
import com.group_2.service.core.HouseholdSetupService;
import com.group_2.service.core.WGService;
import com.group_2.ui.core.Controller;
import com.group_2.ui.core.NavbarController;
import com.group_2.util.SessionManager;

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
    private final HouseholdSetupService householdSetupService;
    private final WGService wgService;
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
    private FlowPane roomCardsContainer;

    // Navbar
    @FXML
    private NavbarController navbarController;

    public CleaningScheduleController(CleaningScheduleService cleaningScheduleService,
            HouseholdSetupService householdSetupService, WGService wgService, SessionManager sessionManager) {
        this.cleaningScheduleService = cleaningScheduleService;
        this.householdSetupService = householdSetupService;
        this.wgService = wgService;
        this.sessionManager = sessionManager;
    }

    @FXML
    public void initialize() {
        if (navbarController != null) {
            navbarController.setTitle("Cleaning Schedule");
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

        SessionManager.UserSession session = sessionManager.getCurrentUserSession().orElse(null);
        if (session == null || session.wgId() == null)
            return;

        List<CleaningTaskDTO> weekTasks = cleaningScheduleService.getTasksForWeekDTO(session.wgId(),
                displayedWeekStart);
        LocalDate today = LocalDate.now();

        // Create 7 day cells
        for (int i = 0; i < 7; i++) {
            LocalDate day = displayedWeekStart.plusDays(i);
            VBox dayCell = createDayCell(day, weekTasks, today, session.userId());
            calendarDaysContainer.getChildren().add(dayCell);
        }
    }

    private VBox createDayCell(LocalDate day, List<CleaningTaskDTO> weekTasks, LocalDate today, Long currentUserId) {
        VBox cell = new VBox(8);
        cell.setPrefWidth(130);
        cell.setMinHeight(150);
        cell.setPadding(new Insets(10));

        boolean isToday = day.equals(today);
        boolean isWeekend = day.getDayOfWeek().getValue() >= 6;

        // Apply CSS classes based on day type
        cell.getStyleClass().add("calendar-cell");
        if (isToday) {
            cell.getStyleClass().add("calendar-cell-today");
        } else if (isWeekend) {
            cell.getStyleClass().add("calendar-cell-weekend");
        }

        // Day number header
        HBox header = new HBox(5);
        header.setAlignment(Pos.CENTER_LEFT);
        Text dayNumber = new Text(String.valueOf(day.getDayOfMonth()));
        dayNumber.getStyleClass().add("calendar-day-number");
        if (isToday) {
            dayNumber.getStyleClass().add("calendar-day-number-today");
        } else if (isWeekend) {
            dayNumber.getStyleClass().add("calendar-day-number-weekend");
        }
        header.getChildren().add(dayNumber);

        if (isToday) {
            StackPane todayBadge = new StackPane();
            todayBadge.getStyleClass().add("calendar-today-badge");
            Text todayText = new Text("Today");
            todayText.getStyleClass().add("calendar-today-badge-text");
            todayBadge.getChildren().add(todayText);
            header.getChildren().add(todayBadge);
        }

        cell.getChildren().add(header);

        // Show tasks that are due on this specific day
        for (CleaningTaskDTO task : weekTasks) {
            LocalDate taskDueDate = task.dueDate();
            // If no dueDate is set, fall back to weekStartDate
            if (taskDueDate == null) {
                taskDueDate = task.weekStartDate();
            }
            if (taskDueDate.equals(day)) {
                HBox taskPill = createTaskPill(task, currentUserId);
                cell.getChildren().add(taskPill);
            }
        }

        return cell;
    }

    private HBox createTaskPill(CleaningTaskDTO task, Long currentUserId) {
        HBox pill = new HBox(5);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.setPadding(new Insets(4, 8, 4, 8));
        pill.setCursor(javafx.scene.Cursor.HAND);

        boolean isMyTask = task.assigneeId() != null && task.assigneeId().equals(currentUserId);

        // Apply CSS classes based on task state
        if (task.completed()) {
            pill.getStyleClass().add("task-pill-done");
        } else if (isMyTask) {
            pill.getStyleClass().addAll("task-pill-pending", "task-pill-pending-mine");
        } else {
            pill.getStyleClass().add("task-pill-pending");
        }

        Text roomIcon = new Text(task.completed() ? "C" : "P");
        roomIcon.getStyleClass().add("task-pill-icon");

        Text roomName = new Text(truncate(task.roomName(), 10));
        roomName.getStyleClass().add("task-pill-text");
        if (task.completed()) {
            roomName.getStyleClass().add("task-pill-text-done");
        } else if (isMyTask) {
            roomName.getStyleClass().add("task-pill-text-pending-mine");
        } else {
            roomName.getStyleClass().add("task-pill-text-pending");
        }

        pill.getChildren().addAll(roomIcon, roomName);

        // Click to toggle complete
        pill.setOnMouseClicked(e -> {
            if (task.completed()) {
                cleaningScheduleService.markTaskIncomplete(task.id());
            } else {
                cleaningScheduleService.markTaskComplete(task.id());
            }
            refreshView();
        });

        return pill;
    }

    private void loadRoomCards() {
        roomCardsContainer.getChildren().clear();

        SessionManager.UserSession session = sessionManager.getCurrentUserSession().orElse(null);
        if (session == null || session.wgId() == null) {
            showEmptyState();
            return;
        }

        List<CleaningTaskDTO> weekTasks = cleaningScheduleService.getTasksForWeekDTO(session.wgId(),
                displayedWeekStart);

        if (weekTasks.isEmpty()) {
            showEmptyState();
            return;
        }

        // Create a card for each task
        for (CleaningTaskDTO task : weekTasks) {
            VBox card = createRoomCard(task, session.userId());
            roomCardsContainer.getChildren().add(card);
        }
    }

    private VBox createRoomCard(CleaningTaskDTO task, Long currentUserId) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(25, 15, 20, 15)); // Extra top padding for delete button
        card.setPrefWidth(220);
        card.setAlignment(Pos.TOP_CENTER);

        boolean isCompleted = task.completed();
        boolean isMyTask = task.assigneeId() != null && task.assigneeId().equals(currentUserId);

        // Apply CSS classes based on task state
        card.getStyleClass().add("task-card");
        if (isCompleted) {
            card.getStyleClass().add("task-card-completed");
        } else if (isMyTask) {
            card.getStyleClass().add("task-card-my-task");
        }

        // Wrap card content in a StackPane to position delete button
        StackPane cardWrapper = new StackPane();
        cardWrapper.getChildren().add(card);

        // Only show delete button for manually created tasks (not template-generated)
        if (task.manualOverride()) {
            Button deleteBtn = new Button("X");
            deleteBtn.getStyleClass().add("task-delete-button");
            deleteBtn.setTooltip(new Tooltip("Delete task"));
            deleteBtn.setOnAction(e -> showDeleteConfirmDialog(task));

            // Position delete button at top-right
            StackPane.setAlignment(deleteBtn, Pos.TOP_RIGHT);
            StackPane.setMargin(deleteBtn, new Insets(5, 5, 0, 0));
            cardWrapper.getChildren().add(deleteBtn);
        }

        // Room name
        Text roomName = new Text(task.roomName());
        roomName.getStyleClass().add("task-room-name");
        if (isCompleted) {
            roomName.getStyleClass().add("task-room-name-completed");
        }

        // Assignee
        HBox assigneeBox = new HBox(6);
        assigneeBox.setAlignment(Pos.CENTER);

        StackPane assigneeAvatar = new StackPane();
        assigneeAvatar.getStyleClass().add("task-assignee-avatar");
        String assigneeInitial = task.assigneeName() != null && !task.assigneeName().isEmpty()
                ? task.assigneeName().substring(0, 1).toUpperCase()
                : "?";
        Text avatarText = new Text(assigneeInitial);
        avatarText.getStyleClass().add("task-assignee-avatar-text");
        assigneeAvatar.getChildren().add(avatarText);

        String assigneeName = isMyTask ? "You" : task.assigneeName();
        Text assigneeText = new Text(assigneeName);
        assigneeText.getStyleClass().add("task-assignee-text");
        if (isMyTask) {
            assigneeText.getStyleClass().add("task-assignee-text-mine");
        }

        assigneeBox.getChildren().addAll(assigneeAvatar, assigneeText);

        // Due date
        LocalDate dueDate = task.dueDate() != null ? task.dueDate() : task.weekStartDate();
        String dayName = dueDate.getDayOfWeek().toString().substring(0, 1)
                + dueDate.getDayOfWeek().toString().substring(1).toLowerCase();
        Text dueDateText = new Text(dayName + ", " + dueDate.getDayOfMonth());
        dueDateText.getStyleClass().add("task-due-date");

        // Status badge
        HBox statusBadge = new HBox(4);
        statusBadge.setAlignment(Pos.CENTER);
        statusBadge.setPadding(new Insets(4, 10, 4, 10));
        statusBadge.getStyleClass().add("status-badge");
        statusBadge.getStyleClass().add(isCompleted ? "status-badge-completed" : "status-badge-pending");

        Text statusText = new Text(isCompleted ? "Completed" : "Pending");
        statusText.getStyleClass().add("status-badge-text");
        statusText.getStyleClass().add(isCompleted ? "status-badge-text-completed" : "status-badge-text-pending");
        statusBadge.getChildren().add(statusText);

        // Action buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);

        Button completeBtn = new Button(isCompleted ? "Undo" : "Done");
        completeBtn.getStyleClass().add("complete-button");
        if (isCompleted) {
            completeBtn.getStyleClass().add("complete-button-done");
        }
        completeBtn.setOnAction(e -> {
            if (isCompleted) {
                cleaningScheduleService.markTaskIncomplete(task.id());
            } else {
                cleaningScheduleService.markTaskComplete(task.id());
            }
            refreshView();
        });

        actions.getChildren().add(completeBtn);

        // Only show reassign button for your own tasks
        if (isMyTask) {
            Button reassignBtn = new Button("Assign");
            reassignBtn.getStyleClass().add("task-action-button");
            reassignBtn.setMinWidth(Region.USE_PREF_SIZE);
            reassignBtn.setTooltip(new Tooltip("Reassign to someone else"));
            reassignBtn.setOnAction(e -> showReassignDialog(task));
            actions.getChildren().add(reassignBtn);
        }

        Button rescheduleBtn = new Button("Cal");
        rescheduleBtn.getStyleClass().add("task-action-button");
        rescheduleBtn.setMinWidth(Region.USE_PREF_SIZE);
        rescheduleBtn.setTooltip(new Tooltip("Reschedule"));
        rescheduleBtn.setOnAction(e -> showRescheduleDialog(task));

        actions.getChildren().add(rescheduleBtn);

        card.getChildren().addAll(roomName, assigneeBox, dueDateText, statusBadge, actions);

        // Return the wrapper as a VBox containing the StackPane
        VBox wrapper = new VBox(cardWrapper);
        return wrapper;
    }

    private void showDeleteConfirmDialog(CleaningTaskDTO task) {
        boolean confirmed = showConfirmDialog(
                "Delete Task",
                "Are you sure you want to delete this task?",
                "Task: " + task.roomName() + " assigned to " + task.assigneeName()
                        + "\n\nThis action cannot be undone.",
                getOwnerWindow(weekTitle));

        if (confirmed) {
            cleaningScheduleService.deleteTask(task.id());
            refreshView();
        }
    }

    private void showEmptyState() {
        VBox emptyState = new VBox(15);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setPadding(new Insets(40));
        emptyState.setPrefWidth(400);
        emptyState.getStyleClass().add("empty-state-card");

        Text emptyIcon = new Text("LIST");
        emptyIcon.getStyleClass().add("empty-state-icon-large");

        Text emptyTitle = new Text("No Tasks This Week");
        emptyTitle.getStyleClass().add("empty-state-title");

        Text emptySubtitle = new Text("Add tasks manually to create a schedule");
        emptySubtitle.getStyleClass().add("empty-state-subtitle");

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);

        Button addBtn = new Button("+ Add Task");
        addBtn.getStyleClass().add("secondary-button");
        addBtn.setOnAction(e -> showAddTaskDialog());

        buttons.getChildren().addAll(addBtn);
        emptyState.getChildren().addAll(emptyIcon, emptyTitle, emptySubtitle, buttons);
        roomCardsContainer.getChildren().add(emptyState);
    }

    private void updateStats() {
        SessionManager.UserSession session = sessionManager.getCurrentUserSession().orElse(null);
        if (session == null || session.wgId() == null) {
            completedTasksText.setText("0/0");
            myTasksCountText.setText("0");
            return;
        }

        List<CleaningTaskDTO> weekTasks = cleaningScheduleService.getTasksForWeekDTO(session.wgId(),
                displayedWeekStart);

        int total = weekTasks.size();
        int completed = (int) weekTasks.stream().filter(CleaningTaskDTO::completed).count();
        int myTasks = (int) weekTasks.stream()
                .filter(t -> t.assigneeId() != null && t.assigneeId().equals(session.userId())).count();

        completedTasksText.setText(completed + "/" + total);
        myTasksCountText.setText(String.valueOf(myTasks));
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength - 1) + "...";
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
        SessionManager.UserSession session = sessionManager.getCurrentUserSession().orElse(null);
        if (session == null || session.wgId() == null) {
            showErrorAlert("Error", "You must be in a WG to add tasks.", getOwnerWindow(weekTitle));
            return;
        }

        Long wgId = session.wgId();
        java.util.List<RoomDTO> rooms = householdSetupService.getRoomsForWgDTO(wgId);
        if (rooms.isEmpty()) {
            showWarningAlert("No Rooms", "Please add rooms first.", getOwnerWindow(weekTitle));
            return;
        }

        List<UserSummaryDTO> members = wgService.getMemberSummaries(wgId);
        if (members.isEmpty()) {
            showWarningAlert("No Members", "WG has no members.", getOwnerWindow(weekTitle));
            return;
        }

        Dialog<CleaningTaskDTO> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(weekTitle));
        styleDialog(dialog);
        dialog.setTitle("Add Cleaning Task");
        dialog.setHeaderText("Assign a room to a member");

        ButtonType addButtonType = new ButtonType("Add Task", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Room selection
        ComboBox<RoomDTO> roomCombo = new ComboBox<>();
        roomCombo.getItems().addAll(rooms);
        roomCombo.setPromptText("Select a room");
        roomCombo.setConverter(new javafx.util.StringConverter<RoomDTO>() {
            @Override
            public String toString(RoomDTO room) {
                return room != null ? room.name() : "";
            }

            @Override
            public RoomDTO fromString(String string) {
                return null;
            }
        });

        // Assignee selection
        ComboBox<UserSummaryDTO> assigneeCombo = new ComboBox<>();
        assigneeCombo.getItems().addAll(members);
        assigneeCombo.setPromptText("Assign to...");
        assigneeCombo.setConverter(new javafx.util.StringConverter<UserSummaryDTO>() {
            @Override
            public String toString(UserSummaryDTO user) {
                return formatUserName(user);
            }

            @Override
            public UserSummaryDTO fromString(String string) {
                return null;
            }
        });

        // Date selection
        DatePicker datePicker = new DatePicker();
        LocalDate now = LocalDate.now();
        // Default to the displayed week start, but not earlier than today
        LocalDate defaultValue = displayedWeekStart.isBefore(now) ? now : displayedWeekStart;
        datePicker.setValue(defaultValue);
        datePicker.setPromptText("Select due date");

        // Disable past dates in the picker
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (date != null && date.isBefore(now)) {
                    setDisable(true);
                    setStyle("-fx-background-color: #f0f0f0;");
                }
            }
        });

        content.getChildren().addAll(
                new Text("Room:"), roomCombo,
                new Text("Assign to:"), assigneeCombo,
                new Text("Due Date:"), datePicker);

        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().lookupButton(addButtonType).setDisable(true);

        // Validation: enable button only when all fields are filled and date is not in
        // the past
        Runnable validateFields = () -> {
            boolean isValid = roomCombo.getValue() != null
                    && assigneeCombo.getValue() != null
                    && datePicker.getValue() != null
                    && !datePicker.getValue().isBefore(LocalDate.now());
            dialog.getDialogPane().lookupButton(addButtonType).setDisable(!isValid);
        };

        roomCombo.valueProperty().addListener((obs, oldVal, newVal) -> validateFields.run());
        assigneeCombo.valueProperty().addListener((obs, oldVal, newVal) -> validateFields.run());
        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> validateFields.run());

        // Run initial validation
        validateFields.run();

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return cleaningScheduleService.assignTaskByIdsWithDate(
                        roomCombo.getValue().id(),
                        assigneeCombo.getValue().id(),
                        wgId,
                        datePicker.getValue());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(task -> refreshView());
    }

    private void showReassignDialog(CleaningTaskDTO task) {
        SessionManager.UserSession session = sessionManager.getCurrentUserSession().orElse(null);
        if (session == null || session.wgId() == null)
            return;

        List<UserSummaryDTO> members = wgService.getMemberSummaries(session.wgId());
        Dialog<UserSummaryDTO> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(weekTitle));
        styleDialog(dialog);
        dialog.getDialogPane().setMinWidth(360);
        dialog.getDialogPane().setPrefWidth(360);
        dialog.setTitle("Reassign Task");
        dialog.setHeaderText("Reassign \"" + task.roomName() + "\"");

        ButtonType assignButtonType = new ButtonType("Reassign", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(assignButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        ToggleGroup group = new ToggleGroup();
        for (UserSummaryDTO member : members) {
            RadioButton rb = new RadioButton(formatUserName(member));
            rb.setUserData(member);
            rb.setToggleGroup(group);
            if (task.assigneeId() != null && member.id().equals(task.assigneeId())) {
                rb.setSelected(true);
            }
            content.getChildren().add(rb);
        }

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == assignButtonType && group.getSelectedToggle() != null) {
                return (UserSummaryDTO) group.getSelectedToggle().getUserData();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newAssignee -> {
            cleaningScheduleService.reassignTask(task.id(), newAssignee.id());
            refreshView();
        });
    }

    private void showRescheduleDialog(CleaningTaskDTO task) {
        Dialog<LocalDate> dialog = new Dialog<>();
        configureDialogOwner(dialog, getOwnerWindow(weekTitle));
        styleDialog(dialog);
        dialog.setTitle("Reschedule Task");
        dialog.setHeaderText("Reschedule \"" + task.roomName() + "\"");

        ButtonType rescheduleButtonType = new ButtonType("Reschedule", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(rescheduleButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Text label = new Text("Select new day:");
        label.getStyleClass().add("normal-text");
        content.getChildren().add(label);

        ToggleGroup group = new ToggleGroup();
        String[] dayNames = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };

        LocalDate currentDueDate = task.dueDate() != null ? task.dueDate() : displayedWeekStart;
        int currentDayIndex = currentDueDate.getDayOfWeek().getValue() - 1;
        LocalDate now = LocalDate.now();

        for (int i = 0; i < 7; i++) {
            LocalDate day = displayedWeekStart.plusDays(i);
            RadioButton rb = new RadioButton(dayNames[i] + " (" + day.getDayOfMonth() + ")");
            rb.setUserData(day);
            rb.setToggleGroup(group);

            // Disable days in the past
            if (day.isBefore(now)) {
                rb.setDisable(true);
            }

            if (i == currentDayIndex) {
                rb.setSelected(true);
            }
            content.getChildren().add(rb);
        }

        dialog.getDialogPane().setContent(content);

        // Validation for reschedule button
        dialog.getDialogPane().lookupButton(rescheduleButtonType).setDisable(group.getSelectedToggle() == null
                || ((LocalDate) group.getSelectedToggle().getUserData()).isBefore(now));

        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isInvalid = newVal == null || ((LocalDate) newVal.getUserData()).isBefore(now);
            dialog.getDialogPane().lookupButton(rescheduleButtonType).setDisable(isInvalid);
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == rescheduleButtonType && group.getSelectedToggle() != null) {
                return (LocalDate) group.getSelectedToggle().getUserData();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newDueDate -> {
            cleaningScheduleService.rescheduleTask(task.id(), newDueDate);
            refreshView();
        });
    }

    @FXML
    public void saveAsTemplate() {
        SessionManager.UserSession session = sessionManager.getCurrentUserSession().orElse(null);
        if (session == null || session.wgId() == null) {
            showErrorAlert("Error", "You must be in a WG.", getOwnerWindow(weekTitle));
            return;
        }

        if (cleaningScheduleService.getTasksForWeekDTO(session.wgId(), displayedWeekStart).isEmpty()) {
            showWarningAlert("No Tasks", "Create a schedule first before saving it as a template.",
                    getOwnerWindow(weekTitle));
            return;
        }

        boolean confirmed = showConfirmDialog("Save Template", "Save current week as default template?",
                "This will overwrite any existing template.", getOwnerWindow(weekTitle));

        if (confirmed) {
            cleaningScheduleService.saveAsTemplate(session.wgId());
            showSuccessAlert("Saved", "Template saved! Use 'Load Default' to apply it to new weeks.",
                    getOwnerWindow(weekTitle));
        }
    }

    @FXML
    public void loadFromTemplate() {
        SessionManager.UserSession session = sessionManager.getCurrentUserSession().orElse(null);
        if (session == null || session.wgId() == null) {
            showErrorAlert("Error", "You must be in a WG.", getOwnerWindow(weekTitle));
            return;
        }

        if (!cleaningScheduleService.hasTemplate(session.wgId())) {
            showWarningAlert("No Template",
                    "No default template found. First create a schedule and save it as template.",
                    getOwnerWindow(weekTitle));
            return;
        }

        boolean confirmed = showConfirmDialog("Load Template", "Load default template for this week?",
                "This will replace any existing tasks for this week.", getOwnerWindow(weekTitle));

        if (confirmed) {
            cleaningScheduleService.generateFromTemplate(session.wgId());
            refreshView();
        }
    }

    @FXML
    public void openTemplateEditor() {
        loadScene(weekTitle.getScene(), "/cleaning/template_editor.fxml");
    }

    @FXML
    public void handleLogout() {
        sessionManager.clear();
        loadScene(weekTitle.getScene(), "/core/login.fxml");
    }

    private String formatUserName(UserSummaryDTO user) {
        if (user == null) {
            return "";
        }
        if (user.surname() == null || user.surname().isBlank()) {
            return user.name();
        }
        return user.name() + " " + user.surname();
    }

}
