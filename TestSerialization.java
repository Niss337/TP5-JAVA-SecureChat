public class TestSerialization {
    public static void main(String[] args) throws Exception {

        ChatMessage original = new ChatMessage(
                MessageType.TEXT_MESSAGE,
                "alice",
                null,
                "room1",
                "Hello, world!",
                System.currentTimeMillis()
        );

        byte[] data = MessageSerializer.serialize(original);
        ChatMessage decoded = MessageDeserializer.deserialize(data);

        System.out.println("Original type:   " + original.getType());
        System.out.println("Decoded type:    " + decoded.getType());
        System.out.println();

        System.out.println("Original sender: " + original.getSender());
        System.out.println("Decoded sender:  " + decoded.getSender());
        System.out.println();

        System.out.println("Original room:   " + original.getRoomId());
        System.out.println("Decoded room:    " + decoded.getRoomId());
        System.out.println();

        System.out.println("Original content: " + original.getContent());
        System.out.println("Decoded content:  " + decoded.getContent());
    }
}
