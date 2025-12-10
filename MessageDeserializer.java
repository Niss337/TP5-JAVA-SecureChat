import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MessageDeserializer {

    /**
     * Deserialize a ChatMessage from bytes produced by MessageSerializer.
     */
    public static ChatMessage deserialize(byte[] data) throws Exception {

        ByteBuffer buffer = ByteBuffer.wrap(data);

        // 1) Read body length from header
        int bodyLength = buffer.getInt();
        if (bodyLength < 0 || bodyLength > 1_000_000) {
            throw new IllegalArgumentException("Invalid message length: " + bodyLength);
        }

        // 2) Read body bytes
        byte[] bodyBytes = new byte[bodyLength];
        buffer.get(bodyBytes);

        // 3) Convert body to String (UTF-8)
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        // 4) Extract fields from our simple JSON-like string
        MessageType type   = MessageType.valueOf(extractString(body, "type"));
        int version        = Integer.parseInt(extractNumber(body, "version"));
        long timestamp     = Long.parseLong(extractNumber(body, "timestamp"));
        String sender      = extractString(body, "sender");
        String recipient   = extractString(body, "recipient");
        String roomId      = extractString(body, "roomId");
        String content     = extractString(body, "content");

        // 5) Build ChatMessage object
        ChatMessage msg = new ChatMessage(type, sender, recipient, roomId, content, timestamp);
        msg.setVersion(version);

        return msg;
    }

    /**
     * Extract a string value like "key":"value" and correctly handle commas inside the value.
     */
    private static String extractString(String json, String key) {
        int keyPos = json.indexOf("\"" + key + "\"");
        if (keyPos == -1) return "";

        int colon = json.indexOf(":", keyPos);
        if (colon == -1) return "";

        // first quote after colon
        int firstQuote = json.indexOf("\"", colon + 1);
        if (firstQuote == -1) return "";

        // closing quote of the string
        int endQuote = json.indexOf("\"", firstQuote + 1);
        if (endQuote == -1) return "";

        return json.substring(firstQuote + 1, endQuote);
    }

    /**
     * Extract a numeric value like "key":12345 or "key":1. We stop at the next comma or closing brace.
     */
    private static String extractNumber(String json, String key) {
        int keyPos = json.indexOf("\"" + key + "\"");
        if (keyPos == -1) return "0";

        int colon = json.indexOf(":", keyPos);
        if (colon == -1) return "0";

        int start = colon + 1;

        // skip spaces
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        int comma = json.indexOf(",", start);
        int brace = json.indexOf("}", start);

        int end;
        if (comma == -1 && brace == -1) {
            end = json.length();
        } else if (comma == -1) {
            end = brace;
        } else if (brace == -1) {
            end = comma;
        } else {
            end = Math.min(comma, brace);
        }

        String raw = json.substring(start, end).trim();
        return raw;
    }
}
