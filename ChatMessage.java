public class ChatMessage {

    private MessageType type;
    private String sender;
    private String recipient;
    private String roomId;
    private String content;
    private long timestamp;
    private int version = 1;

    public ChatMessage(MessageType type, String sender, String recipient,
                       String roomId, String content, long timestamp) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.roomId = roomId;
        this.content = content;
        this.timestamp = timestamp;
    }

    public MessageType getType() { return type; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getRoomId() { return roomId; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public int getVersion() { return version; }
    public void setVersion(int version) {
        this.version = version;
    }

}
