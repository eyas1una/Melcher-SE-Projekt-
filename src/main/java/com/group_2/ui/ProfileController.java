package com.group_2.ui;

import com.group_2.service.UserService;
import com.group_2.service.WGService;
import com.group_2.util.SessionManager;
import com.model.User;
import com.model.WG;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Controller for the profile view.
 * Shows user information and provides logout/leave WG functionality.
 */
@Component
public class ProfileController extends Controller {

    private final SessionManager sessionManager;
    private final WGService wgService;
    private final UserService userService;

    @Autowired
    private ApplicationContext applicationContext;

    @FXML
    private Text avatarInitial;
    @FXML
    private Text userNameText;
    @FXML
    private Text userEmailText;
    @FXML
    private Text nameDisplayText;
    @FXML
    private Text emailDisplayText;
    @FXML
    private Text wgStatusText;
    @FXML
    private Text roleText;

    @Autowired
    public ProfileController(SessionManager sessionManager, WGService wgService, UserService userService) {
        this.sessionManager = sessionManager;
        this.wgService = wgService;
        this.userService = userService;
    }

    public void initView() {
        sessionManager.refreshCurrentUser();
        updateProfileInfo();
    }

    private void updateProfileInfo() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            String fullName = currentUser.getName() +
                    (currentUser.getSurname() != null ? " " + currentUser.getSurname() : "");
            userNameText.setText(fullName);
            nameDisplayText.setText(fullName);

            String email = currentUser.getEmail() != null ? currentUser.getEmail() : "No email";
            userEmailText.setText(email);
            emailDisplayText.setText(email);

            String initial = currentUser.getName() != null && !currentUser.getName().isEmpty()
                    ? currentUser.getName().substring(0, 1).toUpperCase()
                    : "?";
            avatarInitial.setText(initial);

            WG wg = currentUser.getWg();
            if (wg != null) {
                wgStatusText.setText("Member of " + wg.name);
                boolean isAdmin = wg.admin != null && wg.admin.getId().equals(currentUser.getId());
                roleText.setText(isAdmin ? "Admin" : "Member");
            } else {
                wgStatusText.setText("No WG");
                roleText.setText("-");
            }
        }
    }

    @FXML
    public void handleEditName() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Edit Name");
        dialog.setHeaderText("Update your name");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        TextField firstNameField = new TextField(currentUser.getName());
        firstNameField.setPromptText("First Name");
        firstNameField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        TextField lastNameField = new TextField(currentUser.getSurname() != null ? currentUser.getSurname() : "");
        lastNameField.setPromptText("Last Name");
        lastNameField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        content.getChildren().addAll(
                new Text("First Name:"), firstNameField,
                new Text("Last Name:"), lastNameField);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new String[] { firstNameField.getText().trim(), lastNameField.getText().trim() };
            }
            return null;
        });

        dialog.showAndWait().ifPresent(names -> {
            if (names[0].isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Invalid Name", "First name cannot be empty.");
                return;
            }
            try {
                userService.updateUser(currentUser.getId(), names[0], names[1].isEmpty() ? null : names[1],
                        currentUser.getEmail());
                sessionManager.refreshCurrentUser();
                updateProfileInfo();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Name updated successfully!");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to update name: " + e.getMessage());
            }
        });
    }

    @FXML
    public void handleEditEmail() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null)
            return;

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Edit Email");
        dialog.setHeaderText("Update your email address");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        TextField emailField = new TextField(currentUser.getEmail() != null ? currentUser.getEmail() : "");
        emailField.setPromptText("Email address");
        emailField.setStyle(
                "-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        content.getChildren().addAll(new Text("Email:"), emailField);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return emailField.getText().trim();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(email -> {
            if (email.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Invalid Email", "Email cannot be empty.");
                return;
            }
            if (!email.contains("@")) {
                showAlert(Alert.AlertType.WARNING, "Invalid Email", "Please enter a valid email address.");
                return;
            }
            try {
                userService.updateUser(currentUser.getId(), currentUser.getName(), currentUser.getSurname(), email);
                sessionManager.refreshCurrentUser();
                updateProfileInfo();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Email updated successfully!");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to update email: " + e.getMessage());
            }
        });
    }

    @FXML
    public void handleLogout() {
        sessionManager.clear();
        loadScene(avatarInitial.getScene(), "/login.fxml");
    }

    @FXML
    public void handleLeaveWG() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null || currentUser.getWg() == null) {
            showAlert(Alert.AlertType.WARNING, "No WG", "You are not a member of any WG.");
            return;
        }

        WG wg = currentUser.getWg();
        boolean isAdmin = wg.admin != null && wg.admin.getId().equals(currentUser.getId());

        if (isAdmin) {
            showAlert(Alert.AlertType.WARNING, "Cannot Leave",
                    "As the admin, you cannot leave the WG. Please transfer admin rights first or delete the WG.");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Leave WG");
        confirmDialog.setHeaderText("Are you sure you want to leave " + wg.name + "?");
        confirmDialog.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                wgService.removeMitbewohner(wg.getId(), currentUser.getId());
                sessionManager.refreshCurrentUser();
                showAlert(Alert.AlertType.INFORMATION, "Success", "You have left the WG.");
                loadScene(avatarInitial.getScene(), "/no_wg.fxml");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to leave WG: " + e.getMessage());
            }
        }
    }

    @FXML
    public void backToHome() {
        loadScene(avatarInitial.getScene(), "/main_screen.fxml");
        javafx.application.Platform.runLater(() -> {
            MainScreenController mainController = applicationContext.getBean(MainScreenController.class);
            mainController.initView();
        });
    }
}
