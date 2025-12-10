public class TestInvalidMessage {
    public static void main(String[] args) {
        try {
            // malformed data (invalid format)
            byte[] bad = new byte[] {1, 2, 3};

            ChatMessage decoded = MessageDeserializer.deserialize(bad);

            System.out.println("Decoded invalid message: " + decoded);

        } catch (Exception e) {
            System.out.println("Error detected correctly:");
            System.out.println(e.getMessage());
        }
    }
}
