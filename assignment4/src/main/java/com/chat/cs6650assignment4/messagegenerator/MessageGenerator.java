package com.chat.cs6650assignment4.messagegenerator;

import com.chat.cs6650assignment4.model.ChatMessage;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class MessageGenerator implements Runnable {

    private final BlockingQueue<ChatMessage> messageQueue;
    private final long totalMessagesToGenerate;
    private final int numConsumers;
    private final ChatMessage poisonPill;
    private final Random random = new Random();

    private static final List<String> PREDEFINED_MESSAGES = List.of(
            "Hello world!", "How are you?", "This is a test.", "Let's connect.", "Great work!",
            "Any updates on the project?", "See you in the meeting.", "Just checking in.",
            "I'll get back to you shortly.", "Thanks for the help!", "That's interesting.",
            "Could you explain that again?", "I agree completely.", "Let's brainstorm some ideas.",
            "What a beautiful day!", "Almost done with my task.", "Need a coffee break.",
            "The system performance is looking good.", "Deployment was successful.",
            "Can you review my code?", "Pushing the latest changes now.", "Working from home today.",
            "What's for lunch?", "Let's schedule a call.", "The new feature is live.",
            "Found a bug, creating a ticket.", "This is awesome!", "Good morning team!",
            "Have a great evening!", "Let's wrap this up.", "I'm facing a blocker.",
            "Who can help me with this?", "The documentation is very clear.", "Let's follow up tomorrow.",
            "Great suggestion!", "I'll take ownership of that task.", "Is the server down?",
            "Just finished the report.", "Sending the files now.", "Let's celebrate this win!",
            "What are our goals for this week?", "Our customers will love this.", "Focus on the user experience.",
            "Another day, another line of code.", "This is why we test.", "The logs show no errors.",
            "Let's check the metrics.", "Stay positive and keep coding.", "Collaboration is key."
    );

    public MessageGenerator(BlockingQueue<ChatMessage> messageQueue, long totalMessagesToGenerate, int numConsumers, ChatMessage poisonPill) {
        this.messageQueue = messageQueue;
        this.totalMessagesToGenerate = totalMessagesToGenerate;
        this.numConsumers = numConsumers;
        this.poisonPill = poisonPill;
    }

    @Override
    public void run() {
        System.out.println("[Generator] Starting generation...");
        try {
            for (long i = 0; i < totalMessagesToGenerate; i++) {
                messageQueue.put(generateRandomMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Generator] Generation was interrupted.");
        } finally {
            System.out.println("[Generator] Finished generation. Adding poison pills to the queue...");
            try {
                for (int i = 0; i < numConsumers; i++) {
                    messageQueue.put(poisonPill);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[Generator] All poison pills added. Generator shutting down.");
        }
    }

    private ChatMessage generateRandomMessage() {
        ChatMessage msg = new ChatMessage();
        long userId = 1 + random.nextInt(100000);
        msg.setUserId(String.valueOf(userId));

        msg.setUsername("user" + userId);

        msg.setMessage(PREDEFINED_MESSAGES.get(random.nextInt(PREDEFINED_MESSAGES.size())));

        msg.setTimestamp(Instant.now().toString());

        int typeRoll = random.nextInt(100);
        if (typeRoll < 90) {
            msg.setMessageType(ChatMessage.MessageType.TEXT);
        } else if (typeRoll < 95) {
            msg.setMessageType(ChatMessage.MessageType.JOIN);
        } else {
            msg.setMessageType(ChatMessage.MessageType.LEAVE);
        }

        return msg;
    }
}