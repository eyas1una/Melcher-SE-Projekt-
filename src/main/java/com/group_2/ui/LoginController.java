package com.group_2.ui;

import com.group_2.User;
import com.group_2.service.UserService;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.fxml.FXML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Controller for handling user login functionality.
 * Extends the abstract Controller class to inherit common UI utilities.
 */
@Component
public class LoginController extends Controller {

    private final UserService userService;

    @Autowired
    private ApplicationContext applicationContext;

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    @FXML
    public void handleLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();

        Optional<User> user = userService.authenticate(email, password);
        if (user.isPresent()) {
            navigateAfterAuth(user.get());
        } else {
            showAlert(Alert.AlertType.ERROR, "Login Failed", "Invalid email or password.");
        }
    }

    @FXML
    public void showSignupScreen() {
        loadScene(emailField.getScene(), "/signup.fxml");
    }

    private void navigateAfterAuth(User user) {
        // Check if user has a WG
        if (user.getWg() != null) {
            // User has a WG - go to main screen
            loadScene(emailField.getScene(), "/main_screen.fxml");
            javafx.application.Platform.runLater(() -> {
                MainScreenController mainScreenController = applicationContext.getBean(MainScreenController.class);
                mainScreenController.initView(user);
            });
        } else {
            // User has no WG - go to no_wg screen
            loadScene(emailField.getScene(), "/no_wg.fxml");
            javafx.application.Platform.runLater(() -> {
                NoWgController noWgController = applicationContext.getBean(NoWgController.class);
                noWgController.initView(user);
            });
        }
    }
}
