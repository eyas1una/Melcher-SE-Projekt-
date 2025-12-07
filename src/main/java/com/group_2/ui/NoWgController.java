package com.group_2.ui;

import com.group_2.service.RoomService;
import com.group_2.service.UserService;
import com.group_2.service.WGService;
import com.group_2.util.SessionManager;
import com.model.Room;
import com.model.User;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.fxml.FXML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for users who are not yet part of a WG.
 * Allows them to create or join a WG.
 */
@Component
public class NoWgController extends Controller {

    private final UserService userService;
    private final WGService wgService;
    private final RoomService roomService;
    private final SessionManager sessionManager;

    @Autowired
    private ApplicationContext applicationContext;

    private final List<TextField> roomFields = new ArrayList<>();

    @FXML
    private Text welcomeText;

    @FXML
    private HBox actionCardsBox;

    @FXML
    private VBox createWgForm;
    @FXML
    private TextField wgNameField;
    @FXML
    private VBox roomFieldsContainer;

    @FXML
    private VBox joinWgForm;
    @FXML
    private TextField wgIdField;

    public NoWgController(UserService userService, WGService wgService, RoomService roomService,
            SessionManager sessionManager) {
        this.userService = userService;
        this.wgService = wgService;
        this.roomService = roomService;
        this.sessionManager = sessionManager;
    }

    public void initView() {
        sessionManager.refreshCurrentUser();
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            welcomeText.setText("Welcome, " + currentUser.getName() + "!");
        }
        hideAllForms();
        roomFields.clear();
        roomFieldsContainer.getChildren().clear();
        // Add one room field by default
        addRoomField();
    }

    @FXML
    public void showCreateWgForm() {
        actionCardsBox.setVisible(false);
        actionCardsBox.setManaged(false);
        createWgForm.setVisible(true);
        createWgForm.setManaged(true);
        joinWgForm.setVisible(false);
        joinWgForm.setManaged(false);
    }

    @FXML
    public void showJoinWgForm() {
        actionCardsBox.setVisible(false);
        actionCardsBox.setManaged(false);
        joinWgForm.setVisible(true);
        joinWgForm.setManaged(true);
        createWgForm.setVisible(false);
        createWgForm.setManaged(false);
    }

    @FXML
    public void hideAllForms() {
        actionCardsBox.setVisible(true);
        actionCardsBox.setManaged(true);
        createWgForm.setVisible(false);
        createWgForm.setManaged(false);
        joinWgForm.setVisible(false);
        joinWgForm.setManaged(false);
        // Reset form fields
        wgNameField.clear();
        wgIdField.clear();
        roomFields.clear();
        roomFieldsContainer.getChildren().clear();
        addRoomField();
    }

    @FXML
    public void addRoomField() {
        HBox roomRow = new HBox(10);
        roomRow.setAlignment(Pos.CENTER_LEFT);

        TextField roomField = new TextField();
        roomField.setPromptText("Room name (e.g., Living Room)");
        roomField.getStyleClass().add("modern-text-field");
        roomField.setPrefWidth(300);

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("secondary-button");
        removeBtn.setStyle(
                "-fx-padding: 8 12; -fx-font-size: 12px; -fx-background-color: #ef4444; -fx-text-fill: white;");
        removeBtn.setOnAction(e -> {
            roomFields.remove(roomField);
            roomFieldsContainer.getChildren().remove(roomRow);
        });

        roomRow.getChildren().addAll(roomField, removeBtn);
        roomFields.add(roomField);
        roomFieldsContainer.getChildren().add(roomRow);
    }

    @FXML
    public void handleCreateWg() {
        String wgName = wgNameField.getText().trim();

        if (wgName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "WG Name cannot be empty.");
            return;
        }

        List<Room> rooms = new ArrayList<>();
        for (TextField roomField : roomFields) {
            String roomName = roomField.getText().trim();
            if (!roomName.isEmpty()) {
                Room room = roomService.createRoom(roomName);
                rooms.add(room);
            }
        }

        try {
            User currentUser = sessionManager.getCurrentUser();
            wgService.createWG(wgName, currentUser, rooms);
            // Refresh user and their WG data
            sessionManager.refreshCurrentUser();

            // Should get the updated user with WG reference
            currentUser = sessionManager.getCurrentUser();
            if (currentUser.getWg() == null) {
                // Fallback if not updated correctly, though refreshCurrentUser should handle it
                // via sessionManager logic
                currentUser.setWg(wgService.getWG(currentUser.getId()).orElse(null));
            }

            showAlert(Alert.AlertType.INFORMATION, "Success", "WG created successfully!");
            navigateToMainScreen();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to create WG: " + e.getMessage());
        }
    }

    @FXML
    public void handleJoinWg() {
        String inviteCode = wgIdField.getText().trim().toUpperCase();

        if (inviteCode.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Please enter an invite code.");
            return;
        }

        try {
            wgService.addMitbewohnerByInviteCode(inviteCode, sessionManager.getCurrentUser());
            sessionManager.refreshCurrentUser();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Successfully joined the WG!");
            navigateToMainScreen();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to join WG. Invalid invite code.");
        }
    }

    @FXML
    public void handleLogout() {
        sessionManager.clear();
        loadScene(welcomeText.getScene(), "/login.fxml");
    }

    private void navigateToMainScreen() {
        // Refresh done in operations
        loadScene(welcomeText.getScene(), "/main_screen.fxml");
        // Use Platform.runLater to ensure FXML is fully loaded
        javafx.application.Platform.runLater(() -> {
            MainScreenController mainScreenController = applicationContext.getBean(MainScreenController.class);
            mainScreenController.initView();
        });
    }
}
