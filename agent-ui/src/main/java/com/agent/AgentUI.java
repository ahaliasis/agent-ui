package com.agent;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class AgentUI {

    private final BorderPane root = new BorderPane();
    private final VBox chatBox = new VBox(12);
    private final ScrollPane scrollPane = new ScrollPane(chatBox);
    private final TextArea inputArea = new TextArea();
    private final TextField apiKeyField = new TextField();
    private final TextField workingDirField = new TextField();
    private final Button sendButton = new Button("Send");
    private final Button clearButton = new Button("Clear");
    private boolean isRunning = false;

    public AgentUI() {
        buildUI();
    }

    private void buildUI() {
        root.getStyleClass().add("root");

        // ── Top bar ──────────────────────────────────────────
        VBox topBar = new VBox(8);
        topBar.getStyleClass().add("top-bar");
        topBar.setPadding(new Insets(16, 20, 12, 20));

        Label titleLabel = new Label("Claude Agent");
        titleLabel.getStyleClass().add("title-label");

        HBox settingsRow = new HBox(12);
        settingsRow.setAlignment(Pos.CENTER_LEFT);



        // Working Directory
        VBox dirBox = new VBox(4);
        Label dirLabel = new Label("Working Directory");
        dirLabel.getStyleClass().add("field-label");
        HBox dirRow = new HBox(6);
        workingDirField.setPromptText("/path/to/your/project");
        workingDirField.getStyleClass().add("settings-field");
        workingDirField.setPrefWidth(260);
        workingDirField.setText(System.getProperty("user.home"));
        Button browseBtn = new Button("Browse");
        browseBtn.getStyleClass().add("browse-btn");
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Working Directory");
            File dir = dc.showDialog(root.getScene().getWindow());
            if (dir != null) workingDirField.setText(dir.getAbsolutePath());
        });
        dirRow.getChildren().addAll(workingDirField, browseBtn);
        dirBox.getChildren().addAll(dirLabel, dirRow);

        settingsRow.getChildren().addAll(dirBox);
        topBar.getChildren().addAll(titleLabel, settingsRow);

        // ── Chat area ─────────────────────────────────────────
        chatBox.setPadding(new Insets(16));
        chatBox.setFillWidth(true);
        scrollPane.getStyleClass().add("chat-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Welcome message
        addSystemMessage("👋 Welcome! Enter your Anthropic API key, set a working directory, and start chatting.");

        // ── Bottom input area ──────────────────────────────────
        VBox bottomBar = new VBox(8);
        bottomBar.getStyleClass().add("bottom-bar");
        bottomBar.setPadding(new Insets(12, 20, 16, 20));

        inputArea.setPromptText("Ask Claude to read files, write code, run commands...");
        inputArea.getStyleClass().add("input-area");
        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);

        // Send on Ctrl+Enter
        inputArea.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode().toString().equals("ENTER")) {
                handleSend();
            }
        });

        HBox buttonRow = new HBox(8);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        Label hint = new Label("Ctrl+Enter to send");
        hint.getStyleClass().add("hint-label");
        HBox hintBox = new HBox(hint);
        hintBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(hintBox, Priority.ALWAYS);

        sendButton.getStyleClass().add("send-btn");
        sendButton.setPrefWidth(100);
        sendButton.setOnAction(e -> handleSend());

        clearButton.getStyleClass().add("clear-btn");
        clearButton.setOnAction(e -> {
            chatBox.getChildren().clear();
            addSystemMessage("Chat cleared. Ready for a new conversation.");
        });

        buttonRow.getChildren().addAll(hintBox, clearButton, sendButton);
        bottomBar.getChildren().addAll(inputArea, buttonRow);

        root.setTop(topBar);
        root.setCenter(scrollPane);
        root.setBottom(bottomBar);
    }

    private void handleSend() {
        if (isRunning) return;

        String prompt = inputArea.getText().trim();
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        String workingDir = workingDirField.getText().trim();

        if (prompt.isEmpty()) return;

        if (apiKey == null || apiKey.isEmpty()) {
            addSystemMessage("⚠️ ANTHROPIC_API_KEY environment variable is not set.");
            return;
        }

        if (workingDir.isEmpty()) {
            addSystemMessage("⚠️ Please set a working directory.");
            return;
        }

        inputArea.clear();
        addUserMessage(prompt);
        isRunning = true;
        sendButton.setText("...");
        sendButton.setDisable(true);

        // Placeholder for assistant response
        VBox assistantBubble = createAssistantBubble();
        TextFlow textFlow = (TextFlow) assistantBubble.lookup(".assistant-text");
        chatBox.getChildren().add(assistantBubble);
        scrollToBottom();

        AgentEngine engine = new AgentEngine(
                // onChunk - not used in non-streaming mode
                chunk -> {},

                // onToolUse
                toolName -> Platform.runLater(() -> {
                    addToolBadge(assistantBubble, toolName);
                    scrollToBottom();
                }),

                // onComplete
                finalText -> Platform.runLater(() -> {
                    if (textFlow != null) {
                        Text text = new Text(finalText);
                        text.getStyleClass().add("assistant-message-text");
                        textFlow.getChildren().add(text);
                    }
                    isRunning = false;
                    sendButton.setText("Send");
                    sendButton.setDisable(false);
                    scrollToBottom();
                }),

                // onError
                error -> Platform.runLater(() -> {
                    if (textFlow != null) {
                        Text text = new Text("❌ " + error);
                        text.getStyleClass().add("error-text");
                        textFlow.getChildren().add(text);
                    }
                    isRunning = false;
                    sendButton.setText("Send");
                    sendButton.setDisable(false);
                    scrollToBottom();
                })
        );

        engine.run(apiKey, prompt, workingDir);
    }

    // ── Message builders ───────────────────────────────────────

    private void addUserMessage(String text) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_RIGHT);

        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("user-bubble");
        bubble.setMaxWidth(600);

        Label timeLabel = new Label(currentTime());
        timeLabel.getStyleClass().add("time-label");

        Text messageText = new Text(text);
        messageText.getStyleClass().add("user-message-text");
        messageText.setWrappingWidth(560);

        TextFlow tf = new TextFlow(messageText);
        bubble.getChildren().addAll(tf, timeLabel);
        wrapper.getChildren().add(bubble);
        chatBox.getChildren().add(wrapper);
        scrollToBottom();
    }

    private VBox createAssistantBubble() {
        VBox bubble = new VBox(8);
        bubble.getStyleClass().add("assistant-bubble");
        bubble.setMaxWidth(680);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("✦");
        icon.getStyleClass().add("assistant-icon");
        Label name = new Label("Claude");
        name.getStyleClass().add("assistant-name");
        Label time = new Label(currentTime());
        time.getStyleClass().add("time-label");
        header.getChildren().addAll(icon, name, time);

        TextFlow textFlow = new TextFlow();
        textFlow.getStyleClass().add("assistant-text");
        textFlow.setPrefWidth(640);

        // Thinking indicator
        Label thinking = new Label("Thinking...");
        thinking.getStyleClass().add("thinking-label");
        textFlow.getChildren().add(thinking);

        bubble.getChildren().addAll(header, textFlow);

        // Remove thinking indicator when real content arrives
        textFlow.getChildren().addListener((javafx.collections.ListChangeListener<Node>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    Platform.runLater(() -> {
                        textFlow.getChildren().remove(thinking);
                    });
                }
            }
        });

        return bubble;
    }

    private void addToolBadge(VBox bubble, String toolName) {
        String emoji = switch (toolName) {
            case "Read" -> "📄";
            case "Write" -> "✏️";
            case "Bash" -> "⚡";
            default -> "🔧";
        };
        Label badge = new Label(emoji + " " + toolName);
        badge.getStyleClass().add("tool-badge");
        bubble.getChildren().add(bubble.getChildren().size() - 1, badge);
    }

    private void addSystemMessage(String text) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER);
        Label label = new Label(text);
        label.getStyleClass().add("system-message");
        label.setWrapText(true);
        wrapper.getChildren().add(label);
        chatBox.getChildren().add(wrapper);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private String currentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public BorderPane getRoot() {
        return root;
    }
}
