# Claude Agent UI

A JavaFX desktop app that connects to the Anthropic API and runs your AI agent with Read, Write, and Bash tools.

## Requirements
- Java 21+
- Maven
- An Anthropic API key (get one at https://console.anthropic.com)

## How to Run

### 1. Install Java 21
Download from: https://adoptium.net

### 2. Install Maven
Download from: https://maven.apache.org/download.cgi
Or on Mac: `brew install maven`
Or on Ubuntu: `sudo apt install maven`

### 3. Run the app
```bash
cd agent-ui
mvn javafx:run
```

That's it! Maven will download all dependencies automatically.

## How to Use

1. **Enter your API key** — paste your `sk-ant-...` key in the top field
2. **Set working directory** — click Browse to select the folder your agent will work in
3. **Type your prompt** — ask Claude to read files, write code, run commands
4. **Send** — press the Send button or Ctrl+Enter

## Example prompts
- "Read README.md and summarize it"
- "Create a file called hello.py that prints Hello World, then run it"
- "List all files in the current directory"
- "Read main.py and fix any bugs you find"

## Tools available
- **Read** — reads any file in your working directory
- **Write** — creates or overwrites files
- **Bash** — runs shell commands in your working directory
