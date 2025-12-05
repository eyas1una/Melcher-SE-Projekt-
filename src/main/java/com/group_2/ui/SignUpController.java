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

/**
 * Controller for handling user signup functionality.
 * Extends the abstract Controller class to inherit common UI utilities.
 */
@Component
public class SignUpController extends Controller {

    private final UserService userService;

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

    public SignUpController(UserService userService) {
        this.userService = userService;
    }

    @FXML
    public void handleSignup() {
        String name = signupNameField.getText();
        String surname = signupSurnameField.getText();
        String email = signupEmailField.getText();
        String password = signupPasswordField.getText();

        try {
            User user = userService.registerUser(name, surname, email, password);
            showAlert(Alert.AlertType.INFORMATION, "Signup Successful", "Account created!");
            // New users never have a WG, so go to no_wg screen
            loadScene(signupNameField.getScene(), "/no_wg.fxml");
            javafx.application.Platform.runLater(() -> {
                NoWgController noWgController = applicationContext.getBean(NoWgController.class);
                noWgController.initView(user);
            });
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Signup Failed", e.getMessage());
        }
    }

    @FXML
    public void showLoginScreen() {
        loadScene(signupNameField.getScene(), "/login.fxml");
    }
}
