import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.OutputStream;

public class ClientSession {

    private String username;        // null before LOGIN
    private final SSLSocket socket;
    private final OutputStream out;
    private String currentRoom;     // last joined room

    public ClientSession(SSLSocket socket, OutputStream out) {
        this.socket = socket;
        this.out = out;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public SSLSocket getSocket() {
        return socket;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(String currentRoom) {
        this.currentRoom = currentRoom;
    }

    // Send a ChatMessage to this client
    public synchronized void send(ChatMessage msg) throws IOException {
        byte[] data = MessageSerializer.serialize(msg);
        out.write(data);
        out.flush();
    }
}
