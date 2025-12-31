package com.group_2.ui.core;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;

import com.group_2.util.SpringFXMLLoader;

import java.io.IOException;
import java.util.Optional;

/**
 * Abstract base controller class for handling JavaFX scene management and
 * dialogs. Provides centralized alert/dialog creation with consistent styling.
 */
public abstract class Controller {

    @Autowired
    protected SpringFXMLLoader fxmlLoader;

    // ========== Window Utilities ==========

    /**
     * Gets the owner window from any scene element. Used to properly parent dialogs
     * for fullscreen compatibility.
     */
    protected Window getOwnerWindow(Node node) {
        if (node != null && node.getScene() != null) {
            return node.getScene().getWindow();
        }
        return null;
    }

    /**
     * Configures a dialog with the owner window for proper fullscreen behavior.
     */
    protected void configureDialogOwner(Dialog<?> dialog, Window owner) {
        if (owner != null) {
            dialog.initOwner(owner);
        }
    }

    /**
     * Applies consistent styling to any Dialog (including custom dialogs).
     * Call this after creating a dialog to apply the application's visual theme.
     */
    protected void styleDialog(Dialog<?> dialog) {
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("styled-dialog");
        try {
            String stylesheet = getClass().getResource("/css/styles.css").toExternalForm();
            if (!dialogPane.getStylesheets().contains(stylesheet)) {
                dialogPane.getStylesheets().add(stylesheet);
            }
        } catch (Exception e) {
            // Stylesheet not found, continue without custom styling
        }
    }

    // ========== Scene Navigation ==========

    /**
     * Loads and displays a new JavaFX scene using Spring's FXML loader.
     */
    protected void loadScene(javafx.scene.Scene currentScene, String fxmlPath) {
        try {
            Parent root = fxmlLoader.load(fxmlPath);
            currentScene.setRoot(root);
        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Error loading page",
                    "Could not load the next page. Try again or close the application.\n" + e.getMessage());
        }
    }

    // ========== Typed Alert Methods ==========

    /**
     * Shows a success/information alert.
     */
    protected void showSuccessAlert(String title, String message) {
        showSuccessAlert(title, message, null);
    }

    /**
     * Shows a success/information alert with owner window.
     */
    protected void showSuccessAlert(String title, String message, Window owner) {
        Alert alert = createStyledAlert(Alert.AlertType.INFORMATION, owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an error alert.
     */
    protected void showErrorAlert(String title, String message) {
        showErrorAlert(title, message, null);
    }

    /**
     * Shows an error alert with owner window.
     */
    protected void showErrorAlert(String title, String message, Window owner) {
        Alert alert = createStyledAlert(Alert.AlertType.ERROR, owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows a warning alert.
     */
    protected void showWarningAlert(String title, String message) {
        showWarningAlert(title, message, null);
    }

    /**
     * Shows a warning alert with owner window.
     */
    protected void showWarningAlert(String title, String message, Window owner) {
        Alert alert = createStyledAlert(Alert.AlertType.WARNING, owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows a confirmation dialog and returns true if confirmed.
     */
    protected boolean showConfirmDialog(String title, String header, String message) {
        return showConfirmDialog(title, header, message, null);
    }

    /**
     * Shows a confirmation dialog with owner and returns true if confirmed.
     */
    protected boolean showConfirmDialog(String title, String header, String message, Window owner) {
        Alert alert = createStyledAlert(Alert.AlertType.CONFIRMATION, owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Shows a confirmation dialog with custom button labels. Returns the chosen
     * ButtonType.
     */
    protected Optional<ButtonType> showConfirmDialogWithButtons(String title, String header, String message,
            Window owner, ButtonType... buttons) {
        Alert alert = createStyledAlert(Alert.AlertType.CONFIRMATION, owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        if (buttons != null && buttons.length > 0) {
            alert.getButtonTypes().setAll(buttons);
        }
        return alert.showAndWait();
    }

    // ========== Legacy Methods (for backward compatibility) ==========

    /**
     * @deprecated Use {@link #showErrorAlert(String, String)} instead.
     */
    @Deprecated
    protected void showError(String title, String header, String content) {
        showError(title, header, content, null);
    }

    /**
     * @deprecated Use {@link #showErrorAlert(String, String, Window)} instead.
     */
    @Deprecated
    protected void showError(String title, String header, String content, Window owner) {
        Alert alert = createStyledAlert(Alert.AlertType.ERROR, owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * @deprecated Use typed methods like {@link #showSuccessAlert},
     *             {@link #showErrorAlert}, etc.
     */
    @Deprecated
    protected void showAlert(Alert.AlertType type, String title, String content) {
        showAlert(type, title, content, null);
    }

    /**
     * @deprecated Use typed methods like {@link #showSuccessAlert},
     *             {@link #showErrorAlert}, etc.
     */
    @Deprecated
    protected void showAlert(Alert.AlertType type, String title, String content, Window owner) {
        Alert alert = createStyledAlert(type, owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * @deprecated Use {@link #showConfirmDialog} instead.
     */
    @Deprecated
    protected void showConfirmation(String title, String header, String content) {
        showConfirmation(title, header, content, null);
    }

    /**
     * @deprecated Use {@link #showConfirmDialog} instead.
     */
    @Deprecated
    protected void showConfirmation(String title, String header, String content, Window owner) {
        ButtonType correctButton = new ButtonType("Korrigieren");
        ButtonType cancelButton = new ButtonType("Prozess abbrechen");

        Alert alert = createStyledAlert(Alert.AlertType.CONFIRMATION, owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getButtonTypes().setAll(correctButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == cancelButton) {
                Platform.exit();
            }
        });
    }

    // ========== Private Helpers ==========

    /**
     * Creates a styled alert with consistent appearance.
     */
    private Alert createStyledAlert(Alert.AlertType type, Window owner) {
        Alert alert = new Alert(type);
        configureDialogOwner(alert, owner);

        // Apply CSS styling
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("styled-alert");

        // Add type-specific style class
        switch (type) {
            case ERROR -> dialogPane.getStyleClass().add("alert-error");
            case INFORMATION -> dialogPane.getStyleClass().add("alert-success");
            case WARNING -> dialogPane.getStyleClass().add("alert-warning");
            case CONFIRMATION -> dialogPane.getStyleClass().add("alert-confirm");
            default -> {
            }
        }

        // Try to load application stylesheet
        try {
            String stylesheet = getClass().getResource("/css/styles.css").toExternalForm();
            dialogPane.getStylesheets().add(stylesheet);
        } catch (Exception e) {
            // Stylesheet not found, continue without custom styling
        }

        return alert;
    }
}
