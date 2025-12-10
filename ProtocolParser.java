public class ProtocolParser {

 
    public ChatMessage parse(byte[] data) throws Exception {
        return MessageDeserializer.deserialize(data);
    }
}
