package ua.requestflow;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    private static Stage stage;

    @Override
    public void start(Stage primaryStage) throws Exception {

        stage = primaryStage;

        stage.setTitle("RequestFlow");
        stage.setResizable(false);

        openPage("login.fxml");

        stage.show();
    }

    public static void openPage(String page) throws Exception {

        FXMLLoader loader =
                new FXMLLoader(
                        Main.class.getResource("/" + page)
                );

        Parent root = loader.load();
        addHoverAnimations(root);

        Scene currentScene = stage.getScene();

        if (currentScene == null) {

            Scene scene =
                    new Scene(
                            root,
                            1000,
                            650
                    );

            addStyles(scene);

            stage.setScene(scene);

        } else {

            currentScene.setRoot(root);

            addStyles(currentScene);
        }
    }

    private static void addHoverAnimations(Node node) {
        boolean isInteractive =
                node.getStyleClass().contains("primary-button")
                        || node.getStyleClass().contains("secondary-button")
                        || node.getStyleClass().contains("nav-button");
        boolean isCard = node.getStyleClass().contains("stat-card");

        if (isInteractive || isCard) {
            ScaleTransition scale = new ScaleTransition(Duration.millis(180), node);
            scale.setInterpolator(Interpolator.EASE_BOTH);

            TranslateTransition lift = new TranslateTransition(Duration.millis(180), node);
            lift.setInterpolator(Interpolator.EASE_BOTH);

            double hoverScale = isCard ? 1.015 : 1.025;
            double hoverLift = isCard ? -3 : -2;

            node.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
                scale.stop();
                lift.stop();
                scale.setToX(hoverScale);
                scale.setToY(hoverScale);
                lift.setToY(hoverLift);
                scale.playFromStart();
                lift.playFromStart();
            });

            node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
                scale.stop();
                lift.stop();
                scale.setToX(1);
                scale.setToY(1);
                lift.setToY(0);
                scale.playFromStart();
                lift.playFromStart();
            });
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                addHoverAnimations(child);
            }
        }
    }

    private static void addStyles(Scene scene) {

        String css =
                Main.class
                        .getResource("/styles.css")
                        .toExternalForm();

        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }

    public static Stage getStage() {
        return stage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}