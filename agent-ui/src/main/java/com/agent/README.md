# Claude Agent 🤖

A JavaFX desktop application that connects to the Anthropic API and runs an AI agent capable of reading files, writing code, and executing shell commands — all from a clean chat interface.

## What it does

You type a prompt, and Claude autonomously decides which tools to use to complete the task:

- 📄 **Read** — reads any file in your working directory
- ✏️ **Write** — creates or overwrites files
- ⚡ **Bash** — runs shell commands

The agent loops until the task is fully complete, showing you exactly which tools it used along the way.

## Tech Stack

- **Java 21**
- **JavaFX 21** — desktop UI
- **Anthropic API** — claude-opus-4-6 model
- **Jackson** — JSON parsing
- **Maven** — dependency management

## Requirements

- Java 21+
- Maven
- An Anthropic API key — get one at [console.anthropic.com](https://console.anthropic.com)

## Setup

### 1. Clone the repo
```bash
git clone https://github.com/ahaliasis/agent-ui.git
cd agent-ui
```

### 2. Set your API key

In IntelliJ: **Run → Edit Configurations → Environment Variables**

Add:
ANTHROPIC_API_KEY=sk-ant-your-key-here
Or set it as a system environment variable on your machine.

### 3. Run
```bash
mvn javafx:run
```

## How to use

1. Open the app
2. Click **Browse** to select your working directory (the folder Claude will work in)
3. Type a prompt and press **Send** or **Ctrl+Enter**

## Example prompts
Read README.md and summarize it

Create a file called hello.py that prints Hello World, then run it

Read all Java files in this project and find any bugs

List all files in the current directory

## Project Structure
agent-ui/
├── src/main/java/com/agent/
│   ├── AgentApp.java      # JavaFX entry point
│   ├── AgentUI.java       # Chat interface
│   └── AgentEngine.java   # Agent loop + Anthropic API calls
├── src/main/resources/
│   └── styles.css         # Dark theme styling
└── pom.xml
## How it works

The core is an **agent loop**:

1. User sends a prompt
2. Claude responds — either with a final answer or a tool call
3. If tool call → your code executes it and sends the result back
4. Loop repeats until Claude gives a final answer

This is the same architecture used by tools like Claude Code.

## Built with

This project was built as a learning exercise following the [CodeCrafters](https://codecrafters.io) "Build your own Claude Code" challenge.

## License

MIT