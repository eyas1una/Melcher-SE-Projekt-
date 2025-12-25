package com.group_2.ui;

import com.group_2.util.SessionManager;
import com.model.User;
import com.model.WG;

import javafx.scene.control.Alert;
import javafx.scene.text.Text;
import javafx.fxml.FXML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Controller for the main screen - the central hub after login.
 * Provides navigation to different functionalities like Dashboard, Cleaning
 * Schedule, etc.
 */
@Component
public class MainScreenController extends Controller {

    private final SessionManager sessionManager;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    public MainScreenController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    // Header elements
    @FXML
    private Text headerUserName;
    @FXML
    private Text headerWgName;
    @FXML
    private Text headerAvatar;

    // Content elements
    @FXML
    private Text welcomeText;
    @FXML
    private Text subtitleText;
    @FXML
    private Text memberCountText;
    @FXML
    private Text roomCountText;
    @FXML
    private Text inviteCodeText;

    public void initView() {
        sessionManager.refreshCurrentUser();
        updateHeader();
        updateStats();
    }

    private void updateHeader() {
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            String fullName = currentUser.getName() +
                    (currentUser.getSurname() != null ? " " + currentUser.getSurname() : "");
            headerUserName.setText(fullName);

            String initial = currentUser.getName() != null && !currentUser.getName().isEmpty()
                    ? currentUser.getName().substring(0, 1).toUpperCase()
                    : "?";
            headerAvatar.setText(initial);

            welcomeText.setText("Welcome back, " + currentUser.getName() + "!");

            WG wg = currentUser.getWg();
            if (wg != null) {
                headerWgName.setText(wg.name);
            } else {
                headerWgName.setText("No WG");
            }
        }
    }

    private void updateStats() {
        User currentUser = sessionManager.getCurrentUser();
        WG wg = currentUser != null ? currentUser.getWg() : null;
        if (wg != null) {
            int memberCount = wg.mitbewohner != null ? wg.mitbewohner.size() : 0;
            int roomCount = wg.rooms != null ? wg.rooms.size() : 0;

            memberCountText.setText(String.valueOf(memberCount));
            roomCountText.setText(String.valueOf(roomCount));
            inviteCodeText.setText(wg.getInviteCode());
        } else {
            memberCountText.setText("0");
            roomCountText.setText("0");
            inviteCodeText.setText("-");
        }
    }

    @FXML
    public void navigateToDashboard() {
        loadScene(headerUserName.getScene(), "/dashboard.fxml");
        javafx.application.Platform.runLater(() -> {
            DashboardController dashboardController = applicationContext.getBean(DashboardController.class);
            dashboardController.initView();
        });
    }

    @FXML
    public void navigateToCleaningSchedule() {
        loadScene(headerUserName.getScene(), "/cleaning_schedule.fxml");
    }

    @FXML
    public void navigateToShoppingList() {
        loadScene(headerUserName.getScene(), "/shopping_list.fxml");
    }

    @FXML
    public void navigateToExpenses() {
        showAlert(Alert.AlertType.INFORMATION, "Coming Soon",
                "The Shared Expenses feature is coming soon!");
    }

    @FXML
    public void handleLogout() {
        sessionManager.clear();
        loadScene(headerUserName.getScene(), "/login.fxml");
    }
}
