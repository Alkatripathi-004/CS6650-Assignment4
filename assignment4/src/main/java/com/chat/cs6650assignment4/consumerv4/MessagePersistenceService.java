package com.chat.cs6650assignment4.consumerv4;

import com.chat.cs6650assignment4.model.QueueMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class MessagePersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(MessagePersistenceService.class);

    private final BlockingQueue<QueueMessage> messageBuffer;
    private final DynamoDBBatchWriter writer;
    private final Executor dbWriterPool;

    private final Thread coordinatorThread;
    private volatile boolean running = true;

    @Value("${chat.db.batch-size:100}")
    private int batchSize;

    @Value("${chat.db.flush-interval-ms:100}")
    private long flushIntervalMs;

    public MessagePersistenceService(DynamoDBBatchWriter writer,
                                     @Qualifier("dbWriterPool") Executor dbWriterPool) {
        this.writer = writer;
        this.dbWriterPool = dbWriterPool;
        this.messageBuffer = new LinkedBlockingQueue<>(50000);
        this.coordinatorThread = new Thread(this::processBufferLoop, "Buffer-Coordinator");
    }

    @PostConstruct
    public void init() {
        coordinatorThread.start();
    }

    public void persistAsync(QueueMessage message) {
        messageBuffer.offer(message);
    }

    private void processBufferLoop() {
        List<QueueMessage> batch = new ArrayList<>(batchSize);
        while (running || !messageBuffer.isEmpty()) {
            try {
                QueueMessage msg = messageBuffer.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    batch.add(msg);
                    messageBuffer.drainTo(batch, batchSize - 1);
                }

                if (!batch.isEmpty()) {
                    List<QueueMessage> batchToProcess = new ArrayList<>(batch);

                    dbWriterPool.execute(() -> writer.writeLogicalBatch(batchToProcess));

                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in coordinator loop", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        try {
            coordinatorThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}