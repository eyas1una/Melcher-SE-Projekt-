package com.group_2.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SpringFXMLLoader {

    private final ApplicationContext context;

    public SpringFXMLLoader(ApplicationContext context) {
        this.context = context;
    }

    public Parent load(String fxmlPath) throws IOException {
        // Prepend /fxml if the path doesn't already start with it
        String fullPath = fxmlPath.startsWith("/fxml") ? fxmlPath : "/fxml" + fxmlPath;
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fullPath));
        loader.setControllerFactory(context::getBean);
        return loader.load();
    }
}
