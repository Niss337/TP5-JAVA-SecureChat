import java.io.*;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

public class SSLClient {

    private SSLSocket socket;
    private String host;
    private int port;
    private boolean trustAllCerts;

    private String username;
    private String currentRoom;

    public SSLClient(String host, int port, boolean trustAllCerts) {
        this.host = host;
        this.port = port;
        this.trustAllCerts = trustAllCerts;
    }

    private SSLContext createSSLContext() throws Exception {
        if (!trustAllCerts) {
            // Production-like: use default trust store
            return SSLContext.getDefault();
        } else {
            // Test mode: accept all certificates (self-signed)
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());
            return sc;
        }
    }

    public void connect() throws Exception {
        SSLContext context = createSSLContext();
        SSLSocketFactory factory = context.getSocketFactory();
        socket = (SSLSocket) factory.createSocket(host, port);

        System.out.println("Connecting to " + host + ":" + port + " ...");
        socket.startHandshake();
        System.out.println("SSL handshake OK.");

        // Start background thread to read server messages
        new Thread(this::readLoop).start();
    }

    private void readLoop() {
        try {
            InputStream in = socket.getInputStream();

            while (!socket.isClosed()) {
                ChatMessage msg = readMessage(in);
                if (msg == null) {
                    break;
                }
                handleIncoming(msg);
            }

        } catch (IOException e) {
            System.out.println("Connection closed: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error in readLoop: " + e.getMessage());
        }
    }

    private ChatMessage readMessage(InputStream in) throws Exception {
        byte[] header = in.readNBytes(4);
        if (header.length == 0) {
            return null; // server closed
        }
        if (header.length < 4) {
            throw new IOException("Incomplete header from server");
        }

        int bodyLength = ByteBuffer.wrap(header).getInt();
        if (bodyLength < 0 || bodyLength > 1_000_000) {
            throw new IOException("Invalid body length from server: " + bodyLength);
        }

        byte[] body = in.readNBytes(bodyLength);
        if (body.length < bodyLength) {
            throw new IOException("Incomplete body from server");
        }

        byte[] full = ByteBuffer.allocate(4 + bodyLength)
                .putInt(bodyLength)
                .put(body)
                .array();

        return MessageDeserializer.deserialize(full);
    }

    private void handleIncoming(ChatMessage msg) {
        switch (msg.getType()) {
            case LOGIN_RESPONSE:
                System.out.println("[SERVER] Login response: " + msg.getContent());
                break;
            case TEXT_MESSAGE:
                if (msg.getRoomId() != null) {
                    System.out.println("[" + msg.getRoomId() + "] " + msg.getSender() + ": " + msg.getContent());
                } else {
                    System.out.println(msg.getSender() + ": " + msg.getContent());
                }
                break;
            case PRIVATE_MESSAGE:
                System.out.println("[PM from " + msg.getSender() + "] " + msg.getContent());
                break;
            case ERROR_RESPONSE:
                System.out.println("[ERROR] " + msg.getContent());
                break;
            default:
                System.out.println("[INFO] " + msg.getType() + " " + msg.getContent());
        }
    }

    private void send(ChatMessage msg) throws IOException {
        byte[] data = MessageSerializer.serialize(msg);
        OutputStream out = socket.getOutputStream();
        out.write(data);
        out.flush();
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java SSLClient <host> <port>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        SSLClient client = new SSLClient(host, port, true); // true = test mode (trust all)

        try {
            client.connect();

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("Commands:");
            System.out.println("  /login <username>");
            System.out.println("  /join <room>");
            System.out.println("  /msg <user> <message>");
            System.out.println("  text without / = message to current room");
            System.out.println("  /quit");

            while (true) {
                String line = console.readLine();
                if (line == null) break;

                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("/quit")) {
                    client.disconnect();
                    break;
                }

                // /login <username>
                if (line.startsWith("/login ")) {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length < 2) {
                        System.out.println("Usage: /login <username>");
                        continue;
                    }
                    client.username = parts[1];

                    ChatMessage login = new ChatMessage(
                            MessageType.LOGIN_REQUEST,
                            client.username,
                            null,
                            null,
                            null,
                            System.currentTimeMillis()
                    );
                    client.send(login);
                    continue;
                }

                // /join <room>
                if (line.startsWith("/join ")) {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length < 2) {
                        System.out.println("Usage: /join <room>");
                        continue;
                    }
                    client.currentRoom = parts[1];

                    ChatMessage join = new ChatMessage(
                            MessageType.JOIN_ROOM_REQUEST,
                            client.username,
                            null,
                            client.currentRoom,
                            null,
                            System.currentTimeMillis()
                    );
                    client.send(join);
                    continue;
                }

                // /msg <user> <message>
                if (line.startsWith("/msg ")) {
                    String[] parts = line.split("\\s+", 3);
                    if (parts.length < 3) {
                        System.out.println("Usage: /msg <username> <message>");
                        continue;
                    }
                    String target = parts[1];
                    String content = parts[2];

                    ChatMessage pm = new ChatMessage(
                            MessageType.PRIVATE_MESSAGE,
                            client.username,
                            target,
                            null,
                            content,
                            System.currentTimeMillis()
                    );
                    client.send(pm);
                    continue;
                }

                // Otherwise: room message
                if (client.currentRoom == null) {
                    System.out.println("You must /join a room before sending room messages.");
                    continue;
                }

                ChatMessage textMsg = new ChatMessage(
                        MessageType.TEXT_MESSAGE,
                        client.username,
                        null,
                        client.currentRoom,
                        line,
                        System.currentTimeMillis()
                );
                client.send(textMsg);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            client.disconnect();
        }
    }
}
