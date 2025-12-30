package com.group_2.ui.core;

import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.ui.finance.TransactionsController;
import com.group_2.util.SessionManager;

import javafx.scene.text.Text;
import javafx.fxml.FXML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Controller for the main screen - the central hub after login.
 * Provides navigation to different functionalities like Cleaning
 * Schedule, Shopping List, etc.
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

    public void initView() {
        sessionManager.refreshCurrentUser();
        updateHeader();
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

            WG wg = currentUser.getWg();
            if (wg != null) {
                headerWgName.setText(wg.name);
            } else {
                headerWgName.setText("No WG");
            }
        }
    }

    @FXML
    public void navigateToCleaningSchedule() {
        loadScene(headerUserName.getScene(), "/cleaning/cleaning_schedule.fxml");
    }

    @FXML
    public void navigateToShoppingList() {
        loadScene(headerUserName.getScene(), "/shopping/shopping_list.fxml");
    }

    @FXML
    public void navigateToExpenses() {
        loadScene(headerUserName.getScene(), "/finance/transactions.fxml");
        javafx.application.Platform.runLater(() -> {
            TransactionsController transactionsController = applicationContext.getBean(TransactionsController.class);
            transactionsController.initView();
        });
    }

    @FXML
    public void navigateToSettings() {
        loadScene(headerUserName.getScene(), "/core/settings.fxml");
        javafx.application.Platform.runLater(() -> {
            SettingsController settingsController = applicationContext.getBean(SettingsController.class);
            settingsController.initView();
        });
    }

    @FXML
    public void navigateToProfile() {
        loadScene(headerUserName.getScene(), "/core/profile.fxml");
        javafx.application.Platform.runLater(() -> {
            ProfileController profileController = applicationContext.getBean(ProfileController.class);
            profileController.initView();
        });
    }
}
