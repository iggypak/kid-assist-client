package ru.kidassistant;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class KidAssistantApp extends Application {
    private static final double WINDOW_WIDTH = 450;
    private static final double WINDOW_HEIGHT = 390;

    private final Logger log = ClientLogging.create();

    private ConfigurableApplicationContext context;
    private AiClient aiClient;
    private TrayService trayService;
    private boolean trayAvailable;
    private boolean stopping;
    private boolean serverOnline;
    private boolean waitingForAnswer;
    private int connectionCheckVersion;

    private Stage stage;
    private TextArea questionArea;
    private Label answerLabel;
    private Label statusLabel;
    private Circle statusDot;
    private ProgressIndicator progressIndicator;
    private Button sendButton;
    private Button retryButton;
    private double dragOffsetX;
    private double dragOffsetY;

    @Override
    public void init() {
        context = new SpringApplicationBuilder(SpringConfiguration.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run(getParameters().getRaw().toArray(String[]::new));
        aiClient = context.getBean(AiClient.class);
        trayService = context.getBean(TrayService.class);
    }

    @Override
    public void stop() {
        stopping = true;
        ++connectionCheckVersion;
        if (context != null) {
            context.close();
        }
    }

    @Override
    public void start(Stage mainStage) {
        stage = mainStage;
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.setTitle("Помощник");
        stage.setOnCloseRequest(event -> {
            event.consume();
            closeOrHideWindow();
        });

        Label title = new Label("Помощник");
        title.getStyleClass().add("title");

        statusDot = new Circle(5, Color.GRAY);
        statusLabel = new Label("Подключаемся…");
        statusLabel.getStyleClass().add("status");

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(18, 18);
        progressIndicator.setMinSize(18, 18);
        progressIndicator.setVisible(false);
        progressIndicator.managedProperty().bind(progressIndicator.visibleProperty());

        retryButton = new Button("↻");
        retryButton.getStyleClass().add("icon-button");
        retryButton.setTooltip(new javafx.scene.control.Tooltip("Повторить проверку связи"));
        retryButton.setOnAction(event -> checkConnection());

        Button closeButton = new Button("×");
        closeButton.getStyleClass().add("icon-button");
        closeButton.setTooltip(new javafx.scene.control.Tooltip("Закрыть помощника"));
        closeButton.setOnAction(event -> closeOrHideWindow());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, spacer, progressIndicator, statusDot,
                statusLabel, retryButton, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setOnMousePressed(event -> {
            dragOffsetX = stage.getX() - event.getScreenX();
            dragOffsetY = stage.getY() - event.getScreenY();
        });
        header.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() + dragOffsetX);
            stage.setY(event.getScreenY() + dragOffsetY);
        });

        answerLabel = new Label("Напиши, что ты хочешь сделать на ноутбуке. Я объясню по шагам.");
        answerLabel.getStyleClass().add("answer-text");
        answerLabel.setWrapText(true);
        answerLabel.setAlignment(Pos.TOP_LEFT);
        answerLabel.setMaxWidth(Double.MAX_VALUE);

        ScrollPane answerScroll = new ScrollPane(answerLabel);
        answerScroll.getStyleClass().add("answer-scroll");
        answerScroll.setFitToWidth(true);
        answerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        answerScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(answerScroll, Priority.ALWAYS);

        questionArea = new TextArea();
        questionArea.getStyleClass().add("question");
        questionArea.setPromptText("Например: как открыть Firefox?");
        questionArea.setWrapText(true);
        questionArea.setPrefRowCount(2);
        questionArea.setMinHeight(66);
        questionArea.setMaxHeight(66);
        questionArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume();
                sendQuestion();
            }
        });
        HBox.setHgrow(questionArea, Priority.ALWAYS);

        sendButton = new Button("Отправить");
        sendButton.getStyleClass().add("send-button");
        sendButton.setPrefWidth(104);
        sendButton.setPrefHeight(66);
        sendButton.setOnAction(event -> sendQuestion());

        HBox composer = new HBox(10, questionArea, sendButton);
        composer.setAlignment(Pos.BOTTOM_CENTER);

        VBox root = new VBox(12, header, answerScroll, composer);
        root.getStyleClass().add("assistant-window");
        root.setPadding(new Insets(14));
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMaxX() - WINDOW_WIDTH - 18);
        stage.setY(bounds.getMaxY() - WINDOW_HEIGHT - 18);
        stage.show();
        questionArea.requestFocus();

        installTray(closeButton);
        checkConnection();
    }

    private void installTray(Button closeButton) {
        trayService.install(this::showWindow, this::hideWindow, Platform::exit, () -> {
            if (stopping) {
                return;
            }
            trayAvailable = false;
            showWindow();
            Platform.setImplicitExit(true);
            closeButton.getTooltip().setText("Закрыть помощника");
            log.info("tray=unavailable");
        }).whenCompleteAsync((installed, error) -> {
            if (stopping) {
                return;
            }
            trayAvailable = error == null && Boolean.TRUE.equals(installed);
            Platform.setImplicitExit(!trayAvailable);
            closeButton.getTooltip().setText(trayAvailable ? "Скрыть в трей" : "Закрыть помощника");
            if (error == null) {
                log.info(trayAvailable ? "tray=enabled" : "tray=unavailable");
            } else {
                log.info("tray=unavailable error_type=" + unwrap(error).getClass().getSimpleName());
            }
        }, Platform::runLater);
    }

    private void closeOrHideWindow() {
        if (trayAvailable) {
            hideWindow();
        } else {
            Platform.exit();
        }
    }

    private void hideWindow() {
        if (trayAvailable) {
            stage.hide();
        }
    }

    private void showWindow() {
        if (stopping) {
            return;
        }
        stage.show();
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
        if (!waitingForAnswer) {
            questionArea.requestFocus();
        }
    }

    private void checkConnection() {
        if (waitingForAnswer) {
            return;
        }
        int generation = ++connectionCheckVersion;
        serverOnline = false;
        refreshControls();
        setStatus("Проверка связи…", Color.LIGHTGRAY);
        aiClient.isServerAvailable().whenComplete((available, error) -> Platform.runLater(() -> {
            if (generation != connectionCheckVersion || waitingForAnswer) {
                return;
            }
            serverOnline = error == null && Boolean.TRUE.equals(available);
            if (serverOnline) {
                setStatus("На связи", Color.LIGHTGREEN);
                log.info("connection_check=ok");
            } else {
                setStatus("Нет связи", Color.SALMON);
                log.info("connection_check=failed");
                answerLabel.setText("Не удалось связаться с ИИ-сервисом. Проверь сеть и настройки доступа. Затем нажми ↻.");
            }
            refreshControls();
        }));
    }

    private void sendQuestion() {
        if (waitingForAnswer || !serverOnline || aiClient == null) {
            return;
        }
        String question = questionArea.getText().trim();
        if (question.isBlank()) {
            return;
        }
        if (question.length() > 1200) {
            setStatus("Слишком длинный вопрос", Color.SALMON);
            return;
        }

        waitingForAnswer = true;
        refreshControls();
        setStatus("Думаю…", Color.LIGHTBLUE);
        answerLabel.setText("Ищу понятное объяснение…");
        questionArea.clear();
        long started = System.nanoTime();

        // Never store any conversation history: only this question is sent.
        aiClient.ask(question).whenComplete((answer, error) -> Platform.runLater(() -> {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            waitingForAnswer = false;
            if (error == null) {
                answerLabel.setText(answer);
                setStatus("Готово", Color.LIGHTGREEN);
                log.info("request=ok duration_ms=" + durationMs);
            } else {
                Throwable cause = unwrap(error);
                answerLabel.setText(humanReadableError(cause));
                setStatus("Ошибка", Color.SALMON);
                // Log only exception type and latency, not prompts, generated text or raw HTTP payload.
                log.info("request=failed error_type=" + cause.getClass().getSimpleName()
                        + " duration_ms=" + durationMs);
            }
            refreshControls();
            if (stage.isShowing()) {
                questionArea.requestFocus();
            }
        }));
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String humanReadableError(Throwable error) {
        if (error instanceof ConnectException) {
            return "ИИ-сервис недоступен. Проверь подключение к сети и нажми ↻.";
        }
        if (error instanceof HttpTimeoutException) {
            return "Сервер слишком долго отвечает. Попробуй задать вопрос ещё раз.";
        }
        if (error instanceof AiNotReachableException) {
            return "Не удалось получить ответ: " + error.getMessage();
        }
        return "Не удалось получить ответ. Проверь соединение с сервером и попробуй ещё раз.";
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusDot.setFill(color);
    }

    private void refreshControls() {
        if (sendButton != null) {
            sendButton.setDisable(waitingForAnswer || !serverOnline);
            questionArea.setDisable(waitingForAnswer);
            retryButton.setDisable(waitingForAnswer);
            progressIndicator.setVisible(waitingForAnswer);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
