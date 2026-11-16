# SearchIT Agent — CLI-Driven AI Chat Execution

An AI-powered chat interface that transforms natural-language instructions into real command-line executions. Instead of simply generating text, SearchIT Agent interprets user requests, executes a web-crawling CLI through a separate Java process, and presents structured results inside a chat interface.

## Overview

SearchIT Agent demonstrates how an AI chat application can move beyond basic prompt-and-response interactions.

A user can describe a task in plain English, such as:

> Crawl https://example.com and give me a summary and JSON.

The backend processes the request, invokes a dedicated crawler CLI using a child JVM, collects the execution results, and returns a structured response to the frontend.

The browser acts only as the presentation layer. Actual execution happens on the backend.

## Key Features

- **Natural-language task input:** Describe crawling tasks through a conversational interface.
- **CLI-driven execution:** Execute crawling operations using a dedicated Java CLI process.
- **Child JVM architecture:** Run the crawler independently from the main Spring Boot application.
- **Website crawling:** Fetch pages and discover same-domain links using Jsoup.
- **Metadata extraction:** Extract page titles, descriptions, headings, word counts, and text excerpts.
- **Structured JSON output:** Generate machine-readable results for further processing.
- **Execution monitoring:** Display execution status, logs, exit codes, and duration.
- **Error handling:** Distinguish successful, partial, failed, cancelled, and timeout states.
- **Robots.txt awareness:** Check basic disallow rules before crawling.
- **Frontend safety:** Render untrusted execution results as text rather than injecting HTML.

## Architecture

```text
                 USER
                  |
                  v
          SearchIT Chat UI
              (Browser)
                  |
                  | POST /api/agent/chat
                  v
           AgentController
                  |
                  v
           CliRunnerService
                  |
                  v
           Child JVM Process
                  |
                  v
              CrawlerCli
                  |
             Jsoup Fetching
                  |
           HTML Extraction
                  |
             result.json
                  |
                  v
          Execution Response
                  |
                  v
             Chat UI
```

The frontend does not directly communicate with the crawler or database. It sends a request to the backend and displays the execution response.

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21 |
| Framework | Spring Boot 4.1.2 |
| HTML Parsing | Jsoup 1.18.3 |
| Build Tool | Maven |
| AI Integration | Groq API |
| Frontend | HTML, CSS, JavaScript |
| Execution | Java ProcessBuilder / Child JVM |
| Output | JSON |

## Project Structure

```text
searchit-agent/
│
├── src/
│   └── main/
│       ├── java/
│       │   └── com/searchengine/
│       │       ├── SearchitAgentApplication.java
│       │       │
│       │       ├── controller/
│       │       │   └── AgentController.java
│       │       │
│       │       ├── service/
│       │       │   ├── CliRunnerService.java
│       │       │   └── LlmService.java
│       │       │
│       │       ├── cli/
│       │       │   └── CrawlerCli.java
│       │       │
│       │       └── dto/
│       │           ├── AgentRequest.java
│       │           └── AgentResponse.java
│       │
│       └── resources/
│           ├── application.properties
│           └── static/
│               └── index.html
│
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .gitignore
└── README.md
```

## Getting Started

### Prerequisites

- Java 21 or later
- Maven (or the included Maven Wrapper)
- Git
- Groq API key for AI functionality

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/searchit-agent.git
cd searchit-agent
```

### 2. Configure environment variables

Set your Groq API key as an environment variable.

Windows PowerShell:

```powershell
$env:GROQ_API_KEY="your_groq_api_key"
```

Do not commit API keys or secret credentials.

### 3. Run the application

Windows:

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run
```

### 4. Open the application

Visit:

```text
http://localhost:8080
```

## Example Task

Enter the following instruction into the chat interface:

```text
Crawl https://example.com and give me a summary and JSON.
```

The application executes the crawling workflow and returns an execution response containing:

- Execution status
- Summary
- Extracted page data
- Structured JSON (when requested)
- Execution logs
- Exit code
- Execution duration

## Crawler Output

Each successfully fetched page contains:

```json
{
  "url": "https://example.com/",
  "title": "Example Domain",
  "description": "",
  "headings": [],
  "wordCount": 20,
  "textExcerpt": "Example Domain..."
}
```

The complete output also includes the starting URL, number of pages crawled, failures, and the pages array.

## Execution Status

| Status | Meaning |
|---|---|
| Success | Execution completed successfully |
| Partial | Some pages were fetched, but failures occurred |
| Failed | Execution failed |
| Network | Backend returned no valid response |
| Timeout | Client-side execution wait limit was reached |
| Cancelled | User requested cancellation |

## Security Considerations

- Validate user-provided URLs before crawling.
- Respect basic robots.txt disallow rules.
- Restrict crawling to the starting domain.
- Avoid exposing API keys and credentials.
- Render external content safely using text-based DOM APIs.
- Apply execution timeouts and page limits.

The current crawler performs initial private-address checks. Redirect and subsequent destination validation should be strengthened before exposing the service publicly. A separate JVM provides process separation, but is not by itself a complete operating-system security sandbox.

## Future Improvements

- More advanced multi-step agent planning.
- Stronger URL and redirect validation.
- Improved cancellation and process lifecycle management.
- Persistent execution history.
- Additional CLI tools beyond web crawling.
- More structured AI-generated reports.
- Advanced task monitoring and observability.

## Project Goal

To demonstrate how AI chat interfaces can become execution environments where natural-language instructions trigger real tools, processes, and structured outputs.

**SearchIT Agent — From conversation to execution.**
