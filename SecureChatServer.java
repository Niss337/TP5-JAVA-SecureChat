import java.io.*;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.*;

public class SecureChatServer {

    private SSLServerSocket serverSocket;
    private boolean running = false;

    private final Map<String, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, ChatRoom> chatRooms = new ConcurrentHashMap<>();
    private final Map<SSLSocket, ClientSession> socketSessions = new ConcurrentHashMap<>();

    private final ProtocolParser messageParser = new ProtocolParser();

    public SecureChatServer(int port, String keystorePath, String password) throws Exception {
        SSLContext context = createSSLContext(keystorePath, password);
        SSLServerSocketFactory ssf = context.getServerSocketFactory();
        serverSocket = (SSLServerSocket) ssf.createServerSocket(port);
    }

    private SSLContext createSSLContext(String keystorePath, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, password.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
        return context;
    }

    public void start() {
        running = true;
        System.out.println("SecureChatServer started. Waiting for SSL clients...");
        while (running) {
            try {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleClient(SSLSocket socket) {
        try {
            socket.startHandshake();
            System.out.println("New client connected: " + socket.getInetAddress());

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            ClientSession session = new ClientSession(socket, out);
            socketSessions.put(socket, session);

            while (true) {
                byte[] messageData = readFramedMessage(in);
                if (messageData == null) {
                    break; // client closed
                }
                handleProtocolMessage(socket, messageData);
            }

        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
        } finally {
            disconnectSession(socket);
        }
    }

    // === TP-style method: handleProtocolMessage(SSLSocket, byte[]) ===
    public void handleProtocolMessage(SSLSocket socket, byte[] messageData) throws Exception {
        ChatMessage msg = messageParser.parse(messageData);
        ClientSession session = socketSessions.get(socket);

        if (session == null) {
            // Should not happen, but just in case
            session = new ClientSession(socket, socket.getOutputStream());
            socketSessions.put(socket, session);
        }

        handleProtocolMessage(session, msg);
    }

    // Internal routing based on MessageType
    private void handleProtocolMessage(ClientSession session, ChatMessage msg) {
        try {
            switch (msg.getType()) {
                case LOGIN_REQUEST:
                    processLogin(msg, session);
                    break;
                case JOIN_ROOM_REQUEST:
                    joinRoom(msg, session);
                    break;
                case TEXT_MESSAGE:
                    broadcastToRoom(msg);
                    break;
                case PRIVATE_MESSAGE:
                    sendPrivateMessage(msg, session);
                    break;
                default:
                    sendError(session, "Unsupported message type: " + msg.getType());
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
        }
    }

    private void processLogin(ChatMessage message, ClientSession session) throws IOException {
        String username = message.getSender();
        if (username == null || username.isEmpty()) {
            sendError(session, "Missing username in LOGIN_REQUEST");
            return;
        }
        if (activeSessions.containsKey(username)) {
            sendError(session, "Username already in use: " + username);
            return;
        }

        session.setUsername(username);
        activeSessions.put(username, session);

        ChatMessage response = new ChatMessage(
                MessageType.LOGIN_RESPONSE,
                "server",
                username,
                null,
                "LOGIN_OK",
                System.currentTimeMillis()
        );
        session.send(response);

        System.out.println("User logged in: " + username);
    }

    private void joinRoom(ChatMessage message, ClientSession session) throws IOException {
        String username = session.getUsername();
        if (username == null) {
            sendError(session, "Must login before joining a room");
            return;
        }

        String roomId = message.getRoomId();
        if (roomId == null || roomId.isEmpty()) {
            sendError(session, "Missing roomId in JOIN_ROOM_REQUEST");
            return;
        }

        ChatRoom room = chatRooms.computeIfAbsent(roomId, ChatRoom::new);
        room.join(session);

        ChatMessage info = new ChatMessage(
                MessageType.TEXT_MESSAGE,
                "server",
                null,
                roomId,
                username + " joined the room.",
                System.currentTimeMillis()
        );
        broadcastToRoom(info);

        System.out.println("User " + username + " joined room " + roomId);
    }

    // broadcastToRoom(TextMessage message) 
    private void broadcastToRoom(ChatMessage message) throws IOException {
        String roomId = message.getRoomId();
        ChatRoom room = chatRooms.get(roomId);
        if (room == null) {
            return;
        }
        for (ClientSession s : room.getParticipants()) {
            s.send(message);
        }
    }
    private void sendPrivateMessage(ChatMessage message, ClientSession fromSession) throws IOException {
        String fromUser = fromSession.getUsername();
        if (fromUser == null) {
            sendError(fromSession, "Must login before sending private messages");
            return;
        }

        String toUser = message.getRecipient();
        if (toUser == null || toUser.isEmpty()) {
            sendError(fromSession, "Missing recipient in PRIVATE_MESSAGE");
            return;
        }

        ClientSession target = activeSessions.get(toUser);
        if (target == null) {
            sendError(fromSession, "User not found: " + toUser);
            return;
        }

        ChatMessage forwarded = new ChatMessage(
                MessageType.PRIVATE_MESSAGE,
                fromUser,
                toUser,
                null,
                message.getContent(),
                System.currentTimeMillis()
        );
        target.send(forwarded);
    }

    private void sendError(ClientSession session, String errorText) throws IOException {
        if (session == null) return;

        ChatMessage err = new ChatMessage(
                MessageType.ERROR_RESPONSE,
                "server",
                null,
                null,
                errorText,
                System.currentTimeMillis()
        );
        session.send(err);
    }

    private byte[] readFramedMessage(InputStream in) throws IOException {
        byte[] header = in.readNBytes(4);
        if (header.length == 0) {
            return null; // client closed
        }
        if (header.length < 4) {
            throw new IOException("Incomplete header");
        }

        int bodyLength = ByteBuffer.wrap(header).getInt();
        if (bodyLength < 0 || bodyLength > 1_000_000) {
            throw new IOException("Invalid body length: " + bodyLength);
        }

        byte[] body = in.readNBytes(bodyLength);
        if (body.length < bodyLength) {
            throw new IOException("Incomplete body");
        }

        return ByteBuffer.allocate(4 + bodyLength)
                .putInt(bodyLength)
                .put(body)
                .array();
    }

    private void disconnectSession(SSLSocket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {}

        ClientSession removed = socketSessions.remove(socket);
        if (removed != null && removed.getUsername() != null) {
            activeSessions.remove(removed.getUsername());
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: java SecureChatServer <port> <keystorePath> <password>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String keystorePath = args[1];
        String password = args[2];

        SecureChatServer server = new SecureChatServer(port, keystorePath, password);
        server.start();
    }
}
