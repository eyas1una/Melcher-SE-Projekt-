package com.group_2.ui;

import com.group_2.User;
import com.group_2.WG;
import com.group_2.service.UserService;
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

    private final UserService userService;
    private User currentUser;

    @Autowired
    private ApplicationContext applicationContext;

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

    public MainScreenController(UserService userService) {
        this.userService = userService;
    }

    public void initView(User user) {
        this.currentUser = userService.getUser(user.getId()).orElse(user);
        updateHeader();
        updateStats();
    }

    private void updateHeader() {
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
            dashboardController.initView(currentUser);
        });
    }

    @FXML
    public void navigateToCleaningSchedule() {
        showAlert(Alert.AlertType.INFORMATION, "Coming Soon",
                "The Cleaning Schedule feature is coming soon!");
    }

    @FXML
    public void navigateToShoppingList() {
        showAlert(Alert.AlertType.INFORMATION, "Coming Soon",
                "The Shopping List feature is coming soon!");
    }

    @FXML
    public void navigateToExpenses() {
        showAlert(Alert.AlertType.INFORMATION, "Coming Soon",
                "The Shared Expenses feature is coming soon!");
    }

    @FXML
    public void handleLogout() {
        loadScene(headerUserName.getScene(), "/login.fxml");
    }
}
