package com.group_2.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEvent;

@Component
public class StageInitializer implements ApplicationListener<StageInitializer.StageReadyEvent> {

    private final SpringFXMLLoader fxmlLoader;

    public StageInitializer(SpringFXMLLoader fxmlLoader) {
        this.fxmlLoader = fxmlLoader;
    }

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        try {
            Stage stage = event.getStage();
            Parent root = fxmlLoader.load("/login.fxml");
            stage.setScene(new Scene(root));
            stage.setTitle("Melcher SE Projekt - Login");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class StageReadyEvent extends ApplicationEvent {
        public StageReadyEvent(Stage stage) {
            super(stage);
        }

        public Stage getStage() {
            return (Stage) getSource();
        }
    }
}
