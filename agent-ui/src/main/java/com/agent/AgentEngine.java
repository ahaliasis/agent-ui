package com.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AgentEngine {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-opus-4-6";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Called on every new piece of text (for streaming updates to UI)
    private Consumer<String> onChunk;
    // Called when a tool is being used
    private Consumer<String> onToolUse;
    // Called when done
    private Consumer<String> onComplete;
    // Called on error
    private Consumer<String> onError;

    public AgentEngine(Consumer<String> onChunk, Consumer<String> onToolUse,
                       Consumer<String> onComplete, Consumer<String> onError) {
        this.onChunk = onChunk;
        this.onToolUse = onToolUse;
        this.onComplete = onComplete;
        this.onError = onError;
    }

    public void run(String apiKey, String prompt, String workingDir) {
        new Thread(() -> {
            try {
                runAgentLoop(apiKey, prompt, workingDir);
            } catch (Exception e) {
                onError.accept("Error: " + e.getMessage());
            }
        }).start();
    }

    private void runAgentLoop(String apiKey, String prompt, String workingDir) throws Exception {

        // Build tools
        ArrayNode tools = mapper.createArrayNode();

        // Read tool
        ObjectNode readTool = mapper.createObjectNode();
        readTool.put("name", "Read");
        readTool.put("description", "Read and return the contents of a file");
        ObjectNode readInput = mapper.createObjectNode();
        readInput.put("type", "object");
        ObjectNode readProps = mapper.createObjectNode();
        ObjectNode filePathProp = mapper.createObjectNode();
        filePathProp.put("type", "string");
        filePathProp.put("description", "The path to the file to read");
        readProps.set("file_path", filePathProp);
        readInput.set("properties", readProps);
        ArrayNode readRequired = mapper.createArrayNode();
        readRequired.add("file_path");
        readInput.set("required", readRequired);
        readTool.set("input_schema", readInput);
        tools.add(readTool);

        // Write tool
        ObjectNode writeTool = mapper.createObjectNode();
        writeTool.put("name", "Write");
        writeTool.put("description", "Write content to a file");
        ObjectNode writeInput = mapper.createObjectNode();
        writeInput.put("type", "object");
        ObjectNode writeProps = mapper.createObjectNode();
        ObjectNode writeFileProp = mapper.createObjectNode();
        writeFileProp.put("type", "string");
        writeFileProp.put("description", "The path of the file to write to");
        ObjectNode contentProp = mapper.createObjectNode();
        contentProp.put("type", "string");
        contentProp.put("description", "The content to write to the file");
        writeProps.set("file_path", writeFileProp);
        writeProps.set("content", contentProp);
        writeInput.set("properties", writeProps);
        ArrayNode writeRequired = mapper.createArrayNode();
        writeRequired.add("file_path");
        writeRequired.add("content");
        writeInput.set("required", writeRequired);
        writeTool.set("input_schema", writeInput);
        tools.add(writeTool);

        // Bash tool
        ObjectNode bashTool = mapper.createObjectNode();
        bashTool.put("name", "Bash");
        bashTool.put("description", "Execute a shell command");
        ObjectNode bashInput = mapper.createObjectNode();
        bashInput.put("type", "object");
        ObjectNode bashProps = mapper.createObjectNode();
        ObjectNode commandProp = mapper.createObjectNode();
        commandProp.put("type", "string");
        commandProp.put("description", "The shell command to execute");
        bashProps.set("command", commandProp);
        bashInput.set("properties", bashProps);
        ArrayNode bashRequired = mapper.createArrayNode();
        bashRequired.add("command");
        bashInput.set("required", bashRequired);
        bashTool.set("input_schema", bashInput);
        tools.add(bashTool);

        // Initialize messages
        List<ObjectNode> messages = new ArrayList<>();
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        // Agent loop
        while (true) {
            // Build request body
            ObjectNode body = mapper.createObjectNode();
            body.put("model", MODEL);
            body.put("max_tokens", 4096);
            body.set("tools", tools);

            ArrayNode messagesArray = mapper.createArrayNode();
            for (ObjectNode m : messages) {
                messagesArray.add(m);
            }
            body.set("messages", messagesArray);

            // Send HTTP request
            String requestBody = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ANTHROPIC_API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                onError.accept("API Error " + response.statusCode() + ": " + response.body());
                return;
            }

            JsonNode responseJson = mapper.readTree(response.body());
            JsonNode contentBlocks = responseJson.get("content");
            String stopReason = responseJson.get("stop_reason").asText();

            // Add assistant response to messages
            ObjectNode assistantMsg = mapper.createObjectNode();
            assistantMsg.put("role", "assistant");
            assistantMsg.set("content", contentBlocks);
            messages.add(assistantMsg);

            // If stop_reason is "end_turn" — final answer
            if ("end_turn".equals(stopReason)) {
                // Find the text block
                StringBuilder finalText = new StringBuilder();
                for (JsonNode block : contentBlocks) {
                    if ("text".equals(block.get("type").asText())) {
                        finalText.append(block.get("text").asText());
                    }
                }
                onComplete.accept(finalText.toString());
                return;
            }

            // Otherwise process tool calls
            if ("tool_use".equals(stopReason)) {
                ArrayNode toolResults = mapper.createArrayNode();

                for (JsonNode block : contentBlocks) {
                    if (!"tool_use".equals(block.get("type").asText())) continue;

                    String toolName = block.get("name").asText();
                    String toolId = block.get("id").asText();
                    JsonNode input = block.get("input");

                    onToolUse.accept(toolName);

                    String toolResult;

                    switch (toolName) {
                        case "Read" -> {
                            String filePath = resolveFilePath(workingDir, input.get("file_path").asText());
                            try {
                                toolResult = Files.readString(Paths.get(filePath));
                            } catch (Exception e) {
                                toolResult = "Error reading file: " + e.getMessage();
                            }
                        }
                        case "Write" -> {
                            String filePath = resolveFilePath(workingDir, input.get("file_path").asText());
                            String content = input.get("content").asText();
                            try {
                                Files.writeString(Paths.get(filePath), content);
                                toolResult = "File written successfully";
                            } catch (Exception e) {
                                toolResult = "Error writing file: " + e.getMessage();
                            }
                        }
                        case "Bash" -> {
                            String command = input.get("command").asText();
                            try {
                                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                                pb.directory(Paths.get(workingDir).toFile());
                                pb.redirectErrorStream(false);
                                Process process = pb.start();
                                String stdout = new String(process.getInputStream().readAllBytes());
                                String stderr = new String(process.getErrorStream().readAllBytes());
                                process.waitFor();
                                toolResult = stdout.isEmpty() && stderr.isEmpty()
                                        ? "Command executed successfully"
                                        : stdout + (stderr.isEmpty() ? "" : "\nSTDERR: " + stderr);
                            } catch (Exception e) {
                                toolResult = "Error executing command: " + e.getMessage();
                            }
                        }
                        default -> toolResult = "Unknown tool: " + toolName;
                    }

                    // Build tool result block
                    ObjectNode resultBlock = mapper.createObjectNode();
                    resultBlock.put("type", "tool_result");
                    resultBlock.put("tool_use_id", toolId);
                    resultBlock.put("content", toolResult);
                    toolResults.add(resultBlock);
                }

                // Add tool results as user message
                ObjectNode toolResultMsg = mapper.createObjectNode();
                toolResultMsg.put("role", "user");
                toolResultMsg.set("content", toolResults);
                messages.add(toolResultMsg);
            }
        }
    }

    private String resolveFilePath(String workingDir, String filePath) {
        if (Paths.get(filePath).isAbsolute()) return filePath;
        return Paths.get(workingDir, filePath).toString();
    }
}
