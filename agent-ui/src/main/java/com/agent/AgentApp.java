package com.agent;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class AgentApp extends Application {

    @Override
    public void start(Stage stage) {
        AgentUI ui = new AgentUI();
        Scene scene = new Scene(ui.getRoot(), 900, 700);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle("Claude Agent");
        stage.setScene(scene);
        stage.setMinWidth(700);
        stage.setMinHeight(500);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
