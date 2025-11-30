package com.chat.cs6650assignment4.consumerv4;

import com.chat.cs6650assignment4.model.QueueMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class DlqService {

    private final BlockingQueue<QueueMessage> deadLetterQueue = new LinkedBlockingQueue<>(50000);

    public void sendToDlq(QueueMessage message) {
        if (!deadLetterQueue.offer(message)) {
            System.err.println("CRITICAL: DLQ is full! Dropping message: " + message.getMessageId());
        }
    }

    public List<QueueMessage> getAllDlqMessages() {
        List<QueueMessage> messages = new ArrayList<>();
        deadLetterQueue.drainTo(messages);
        return messages;
    }

    public int getDlqSize() {
        return deadLetterQueue.size();
    }
}