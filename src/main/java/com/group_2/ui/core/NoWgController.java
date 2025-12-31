package com.group_2.ui.core;

import com.group_2.dto.cleaning.RoomDTO;
import com.group_2.dto.core.UserSessionDTO;
import com.group_2.service.core.HouseholdSetupService;
import com.group_2.service.core.WGService;
import com.group_2.util.SessionManager;

import javafx.geometry.Pos;

import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
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
 * Controller for users who are not yet part of a WG. Allows them to create or
 * join a WG.
 */
@Component
public class NoWgController extends Controller {

    private final WGService wgService;
    private final HouseholdSetupService householdSetupService;
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
    private ScrollPane roomScrollPane;
    @FXML
    private VBox roomFieldsContainer;

    @FXML
    private VBox joinWgForm;
    @FXML
    private TextField wgIdField;

    public NoWgController(WGService wgService, HouseholdSetupService householdSetupService,
            SessionManager sessionManager) {
        this.wgService = wgService;
        this.householdSetupService = householdSetupService;
        this.sessionManager = sessionManager;
    }

    public void initView() {
        sessionManager.refreshCurrentUser();
        UserSessionDTO session = sessionManager.getCurrentUserSession().orElse(null);
        if (session != null) {
            welcomeText.setText("Welcome, " + session.name() + "!");
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

        Button removeBtn = new Button("Remove");
        removeBtn.getStyleClass().addAll("secondary-button", "secondary-button-danger");
        removeBtn.setOnAction(e -> {
            roomFields.remove(roomField);
            roomFieldsContainer.getChildren().remove(roomRow);
        });

        roomRow.getChildren().addAll(roomField, removeBtn);
        roomFields.add(roomField);
        roomFieldsContainer.getChildren().add(roomRow);

        // Scroll to bottom after adding a room
        javafx.application.Platform.runLater(() -> roomScrollPane.setVvalue(1.0));
    }

    @FXML
    public void handleCreateWg() {
        String wgName = wgNameField.getText().trim();

        if (wgName.isEmpty()) {
            showErrorAlert("Error", "WG Name cannot be empty.");
            return;
        }

        List<Long> roomIds = new ArrayList<>();
        for (TextField roomField : roomFields) {
            String roomName = roomField.getText().trim();
            if (!roomName.isEmpty()) {
                RoomDTO room = householdSetupService.createRoomDTO(roomName);
                if (room != null) {
                    roomIds.add(room.id());
                }
            }
        }

        try {
            UserSessionDTO session = sessionManager.getCurrentUserSession().orElse(null);
            if (session == null) {
                showErrorAlert("Error", "You must be logged in to create a WG.");
                return;
            }
            wgService.createWGWithRoomIds(wgName, session.userId(), roomIds);
            sessionManager.refreshCurrentUser();

            showSuccessAlert("Success", "WG created successfully!");
            navigateToMainScreen();
        } catch (Exception e) {
            showErrorAlert("Error", "Failed to create WG: " + e.getMessage());
        }
    }

    @FXML
    public void handleJoinWg() {
        String inviteCode = wgIdField.getText().trim().toUpperCase();

        if (inviteCode.isEmpty()) {
            showErrorAlert("Error", "Please enter an invite code.");
            return;
        }

        try {
            UserSessionDTO session = sessionManager.getCurrentUserSession().orElse(null);
            if (session == null) {
                showErrorAlert("Error", "You must be logged in to join a WG.");
                return;
            }
            wgService.addMitbewohnerByInviteCode(inviteCode, session.userId());
            sessionManager.refreshCurrentUser();
            showSuccessAlert("Success", "Successfully joined the WG!");
            navigateToMainScreen();
        } catch (Exception e) {
            showErrorAlert("Error", "Failed to join WG. Invalid invite code.");
        }
    }

    @FXML
    public void handleLogout() {
        sessionManager.clear();
        loadScene(welcomeText.getScene(), "/core/login.fxml");
    }

    private void navigateToMainScreen() {
        // Refresh done in operations
        loadScene(welcomeText.getScene(), "/core/main_screen.fxml");
        // Use Platform.runLater to ensure FXML is fully loaded
        javafx.application.Platform.runLater(() -> {
            MainScreenController mainScreenController = applicationContext.getBean(MainScreenController.class);
            mainScreenController.initView();
        });
    }
}
