## Accessing Application Metrics

This application uses the **Spring Boot Actuator** framework to expose key health and performance metrics over HTTP.

### Prerequisites

1.  The Spring Boot application must be running.
2.  You need command-line access (SSH) to the EC2 instance where the application is running.

### Instructions

All commands are to be run from the terminal of the EC2 instance hosting the application.

#### 1. List All Available Metrics

To see a list of all metric names that the application is tracking, query the main metrics endpoint. This is useful for discovering what can be monitored.

```bash
curl -s http://localhost:8080/actuator/metrics
```


#### 2. View a Specific Metric's Value

To get the detailed value of a specific metric, append its name to the metrics URL.

**A. To Check a Counter (e.g., Total Messages Processed):**

This metric shows the total number of messages successfully processed by the consumer since the application started.

```bash
curl -s http://localhost:8080/actuator/metrics/chat.messages.processed
```

**Example Output:**
```json
{
  "name": "chat.messages.processed",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 500000.0
    }
  ]
}
```

**Other available counters:**
*   `chat.messages.duplicates`: Total duplicate messages detected and ignored.
*   `chat.messages.failed`: Total messages that failed processing and were re-queued.

#### B. To Check a Timer (e.g., Message Processing Latency):**

This metric provides statistics on how long the `onMessage` method takes to execute, which is a key indicator of consumer performance.

```bash
curl -s http://localhost:8080/actuator/metrics/chat.message.processing.time
```

**Example Output:**
```json
{
  "name": "chat.message.processing.time",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 500000.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 150.12345
    },
    {
      "statistic": "MAX",
      "value": 0.09876
    }
  ],
}
```
#### C. Analytics queries

##### 1. Configuration
Open `src/main/java/com/chat/cs6650assignment3/client/PerformanceClient.java`.

Update the **Load Balancer URLs** to match your current AWS deployment:

```java
private static final String SERVER_WS_URL = "ws://YOUR-ALB-DNS-NAME.us-east-1.elb.amazonaws.com/chat";
private static final String SERVER_HTTP_URL = "http://YOUR-ALB-DNS-NAME.us-east-1.elb.amazonaws.com";
```

**Optional Tuning:**
*   `TOTAL_MESSAGES`: Set to `200000` for a full stress test, or `100` for a quick connectivity check.
*   `NUM_THREADS`: Set to `256` for high load.

##### 2. How to Run

**Option A: IntelliJ IDEA**
1.  Navigate to `com.chat.cs6650assignment3.client.PerformanceClient`.
2.  Right-click the file and select **Run 'PerformanceClient.main()'**.

**Option B: Terminal (Maven)**
```bash
mvn clean compile exec:java -Dexec.mainClass="com.chat.cs6650assignment3.client.PerformanceClient"
```

##### 3. Analyzing the Output

The client will first run the Load Test. **Wait for it to finish.**
Once complete, it enters **Phase 2** automatically.

**Look for the JSON Output in your console:**

```json
[Query 1] Fetching System Analytics Stats...
Response:
{
  "top_active_rooms" : [ { "1" : 1125 }, ... ],
  "total_messages_in_window" : 18957,
  "throughput_msg_per_sec" : "2932.25",
  "unique_active_users" : 17282
}

[Query 2] Fetching History for Room 1...
>> Messages found in Room 1: 1000

[Query 3] Fetching History for User ID '1'...
>> Messages found for User 1: 5
```

### 2. How to Collect DDB Metrics


#### Step A: Open the Dashboard
1.  Log in to AWS Console.
2.  Go to **DynamoDB**.
3.  Click **Tables** on the left sidebar -> Select `ChatMessages`.
4.  Click the **Monitor** tab in the middle of the screen.
5.  Click **"View in CloudWatch"**

#### Step B: Capture the Specific Graphs

**1. For "Queries Per Second" -> Capture `Consumed Write Capacity`**
*   **Look for Graph:** **ConsumedWriteCapacityUnits** (Sum).
*   **Interpretation:** Hover over the peak. If it says `1,500`, you are doing ~1,500 writes/second (assuming items < 1KB).

**2. For "Lock Wait Time" -> Capture `Throttled Requests`**
*   **Look for Graph:** **ThrottledRequests** (or **WriteThrottleEvents**).

**3. For "Disk I/O" -> Capture `Latency`**
*   **Look for Graph:** **SuccessfulRequestLatency** (Average) -> Select "PutItem".
*   **Interpretation:** This measures the physical time taken to commit data to disk.