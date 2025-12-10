import java.io.*;
import java.security.KeyStore;
import javax.net.ssl.*;

public class SSLTCPServer {

    // Server configuration
    private int port;
    private SSLServerSocket serverSocket;
    private boolean isRunning;

    public SSLTCPServer(int port, String keystorePath, String password) throws Exception {
        this.port = port;

        // Create SSLContext from our JKS keystore
        SSLContext sslContext = createSSLContext(keystorePath, password);

        // Create the SSL server socket
        SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
        this.serverSocket = (SSLServerSocket) factory.createServerSocket(port);
        this.isRunning = true;

        System.out.println("SSL TCP Server started on port " + port);
    }

   
    private SSLContext createSSLContext(String keystorePath, String password) throws Exception {
        // Load the keystore file (server.jks)
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, password.toCharArray());
        }

        // Create KeyManager from the keystore
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        // Create the SSLContext (TLS)
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);

        return context;
    }

    public void launch() {
        System.out.println("Server waiting for SSL connections...");
        while (isRunning) {
            try {
                // Wait for a client
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // Handle client in a separate thread
                new Thread(() -> handleClient(clientSocket)).start();

            } catch (IOException e) {
                if (isRunning) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleClient(SSLSocket clientSocket) {
        try {
            // Perform SSL handshake
            clientSocket.startHandshake();
            System.out.println("SSL handshake OK with " + clientSocket.getInetAddress());

            // Encrypted input/output streams
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(clientSocket.getOutputStream()), true);

            out.println("Welcome to the SSL server. Type 'quit' to exit.");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Received from client: " + line);

                if ("quit".equalsIgnoreCase(line.trim())) {
                    out.println("Goodbye!");
                    break;
                }

                // Echo back the message
                out.println("Echo: " + line);
            }

            clientSocket.close();
            System.out.println("Client disconnected.");

        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    public void shutdown() {
        isRunning = false;
        try {
            serverSocket.close();
            System.out.println("Server shutdown.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java SSLTCPServer <port> <keystorePath> <password>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String keystorePath = args[1];
        String password = args[2];

        try {
            SSLTCPServer server = new SSLTCPServer(port, keystorePath, password);
            server.launch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
