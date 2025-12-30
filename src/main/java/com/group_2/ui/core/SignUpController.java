package com.group_2.ui.core;

import com.group_2.model.User;
import com.group_2.service.core.UserService;
import com.group_2.util.SessionManager;

import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.fxml.FXML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Controller for handling user signup functionality. Extends the abstract
 * Controller class to inherit common UI utilities.
 */
@Component
public class SignUpController extends Controller {

    private final UserService userService;
    private final SessionManager sessionManager;

    @Autowired
    private ApplicationContext applicationContext;

    @FXML
    private TextField signupNameField;
    @FXML
    private TextField signupSurnameField;
    @FXML
    private TextField signupEmailField;
    @FXML
    private PasswordField signupPasswordField;

    public SignUpController(UserService userService, SessionManager sessionManager) {
        this.userService = userService;
        this.sessionManager = sessionManager;
    }

    @FXML
    public void handleSignup() {
        String name = signupNameField.getText().trim();
        String surname = signupSurnameField.getText().trim();
        String email = signupEmailField.getText().trim();
        String password = signupPasswordField.getText();

        // Validate that all fields are filled
        if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showErrorAlert("Validation Error", "Please fill in all fields.");
            return;
        }

        try {
            User user = userService.registerUser(name, surname, email, password);
            sessionManager.setCurrentUser(user); // Set as current user in session
            showSuccessAlert("Signup Successful", "Account created!");
            // New users never have a WG, so go to no_wg screen
            loadScene(signupNameField.getScene(), "/core/no_wg.fxml");
            javafx.application.Platform.runLater(() -> {
                NoWgController noWgController = applicationContext.getBean(NoWgController.class);
                noWgController.initView();
            });
        } catch (Exception e) {
            showErrorAlert("Signup Failed", e.getMessage());
        }
    }

    @FXML
    public void showLoginScreen() {
        loadScene(signupNameField.getScene(), "/core/login.fxml");
    }
}
