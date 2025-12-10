import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MessageSerializer {

    /**
     * Serialize a ChatMessage into bytes.
     * Format: [bodyLength:int][bodyBytes...]
     * bodyBytes is a simple JSON-like UTF-8 string.
     */
    public static byte[] serialize(ChatMessage msg) {

        String body = "{"
                + "\"type\":\"" + msg.getType().name() + "\","
                + "\"version\":" + msg.getVersion() + ","
                + "\"timestamp\":" + msg.getTimestamp() + ","
                + "\"sender\":\"" + safe(msg.getSender()) + "\","
                + "\"recipient\":\"" + safe(msg.getRecipient()) + "\","
                + "\"roomId\":\"" + safe(msg.getRoomId()) + "\","
                + "\"content\":\"" + safe(msg.getContent()) + "\""
                + "}";

        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(4 + bodyBytes.length);
        buffer.putInt(bodyBytes.length);  // header = length of body
        buffer.put(bodyBytes);            // body

        return buffer.array();
    }

    // Avoid nulls and dangerous quotes
    private static String safe(String s) {
        if (s == null) return "";
        // we keep commas, only replace double quotes to avoid breaking the format
        return s.replace("\"", "'");
    }
}
