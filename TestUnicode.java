public class TestUnicode {
    public static void main(String[] args) throws Exception {

        String[] tests = {
                "مرحبا بالعالم",
                "Bonjour à tous",
                "こんにちは世界",
                "Hello 😄🔥🚀"
        };

        for (String text : tests) {
            ChatMessage msg = new ChatMessage(
                    MessageType.TEXT_MESSAGE,
                    "user",
                    null,
                    "room1",
                    text,
                    System.currentTimeMillis()
            );

            byte[] data = MessageSerializer.serialize(msg);
            ChatMessage decoded = MessageDeserializer.deserialize(data);

            System.out.println("Original: " + text);
            System.out.println("Decoded : " + decoded.getContent());
            System.out.println("--------------------------------");
        }
    }
}
