import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChatRoom {

    private final String name;
    private final Set<ClientSession> participants = new CopyOnWriteArraySet<>();

    public ChatRoom(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void join(ClientSession session) {
        participants.add(session);
        session.setCurrentRoom(name);
    }

    public void leave(ClientSession session) {
        participants.remove(session);
    }

    public Set<ClientSession> getParticipants() {
        return participants;
    }
}
