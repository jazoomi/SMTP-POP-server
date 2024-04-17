package ca.yorku.eecs3214.mail.net;

import ca.yorku.eecs3214.mail.mailbox.MailMessage;
import ca.yorku.eecs3214.mail.mailbox.Mailbox;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class MyPOPServer extends Thread {

    private final Socket socket;
    private final BufferedReader socketIn;
    private final PrintWriter socketOut;

    // TODO Additional properties, if needed
    private boolean isAuthenticated = false;
    private String currentUser = null;
    private Mailbox currentMailbox = null;

    /**
     * Initializes an object responsible for a connection to an individual client.
     *
     * @param socket The socket associated to the accepted connection.
     * @throws IOException If there is an error attempting to retrieve the socket's
     *                     information.
     */
    public MyPOPServer(Socket socket) throws IOException {
        this.socket = socket;
        this.socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.socketOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Handles the communication with an individual client. Must send the
     * initial welcome message, and then repeatedly read requests, process the
     * individual operation, and return a response, according to the POP3
     * protocol. Empty request lines should be ignored. Only returns if the
     * connection is terminated or if the QUIT command is issued. Must close the
     * socket connection before returning.
     */
    @Override
    public void run() {
        try {
            socketOut.println("+OK POP3 server ready");
            String line;
            while ((line = socketIn.readLine()) != null) {
                // Ignore empty request lines
                if (line.trim().isEmpty()) continue;

                // Split the command and arguments for easier handling
                String[] commandParts = line.split(" ", 2);
                String command = commandParts[0].toUpperCase();

                switch (command) {
                    case "USER":
                        handleUser(commandParts.length > 1 ? commandParts[1] : "");
                        break;
                    case "PASS":
                        handlePass(commandParts.length > 1 ? commandParts[1] : "");
                        break;
                    case "STAT":
                        handleStat();
                        break;
                    case "LIST":
                        handleList(commandParts.length > 1 ? commandParts[1] : "");
                        break;
                    case "RETR":
                        handleRetr(commandParts.length > 1 ? commandParts[1] : "");
                        break;
                    case "DELE":
                        handleDele(commandParts.length > 1 ? commandParts[1] : "");
                        break;
                    case "RSET":
                        handleRset();
                        break;
                    case "NOOP":
                        handleNoop();
                        break;
                    case "QUIT":
                        handleQuit();
                        return; // Exit the loop to close the connection
                    default:
                        socketOut.println("-ERR Unknown command");
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error in client's connection handling: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing the socket: " + e.getMessage());
            }
        }
    }


    private void handleUser(String line) {
        // Split the command to get the username
        if (line.equals("")) {
            socketOut.println("-ERR Missing username");
            return;
        }
        currentUser = line;
        // According to RFC, always respond positively here, but don't authenticate yet
        socketOut.println("+OK User name accepted, password required");
    }

    private void handlePass(String line) {
        if (currentUser == null) {
            socketOut.println("-ERR Send USER command first");
            return;
        }
        
        if (line.equals("")) {
            socketOut.println("-ERR Missing password");
            return;
        }
        // Check if user and password are correct. For simplicity, assume these methods exist.
        if (Mailbox.isValidUser(currentUser)) {
        	
            isAuthenticated = true;
            // Initialize mailbox
            currentMailbox = new Mailbox(currentUser);
            try {
            	currentMailbox.loadMessages(line);
            }
            catch(Mailbox.MailboxNotAuthenticatedException e) {
            	socketOut.println("-ERR");
            	return;
            }
            socketOut.println("+OK Mailbox locked and ready");
        } else {
            socketOut.println("-ERR Invalid username or password");
        }
    }

    private void handleQuit() {
    	if (this.isAuthenticated) {
    		this.currentMailbox.deleteMessagesTaggedForDeletion();
    	}
    	socketOut.println("+OK POP3 server signing off");
    }


    private void handleStat() throws IOException {
        if (!isAuthenticated) {
            socketOut.println("-ERR Authenticate first");
            return;
        }
        int messageCount = currentMailbox.size(false); // false to exclude deleted messages
        long totalSize = currentMailbox.getTotalUndeletedFileSize(false);
        socketOut.println("+OK " + messageCount + " " + totalSize);
    }

    private void handleList(String line) throws IOException {
        if (!isAuthenticated) {
            socketOut.println("-ERR Authenticate first");
            return;
        }
        if (line.equals("")) {
            // List all messages
            int messageCount = currentMailbox.size(false); // Exclude deleted messages
            long totalSize = currentMailbox.getTotalUndeletedFileSize(false);
            socketOut.println("+OK " + messageCount + " messages (" + totalSize + " octets)");
            int i =1;
            for(MailMessage message: currentMailbox) {
            	if(!message.isDeleted()) {
            		socketOut.println(i + " " + message.getFileSize());
            		
            	}
            	i++;
            }

            socketOut.println(".");
        } else if (!line.equals("")) {
            // List specific message
            try {
                int msgNumber = Integer.parseInt(line);
                MailMessage message = currentMailbox.getMailMessage(msgNumber);
                if (message.isDeleted()) {
                    socketOut.println("-ERR Message " + msgNumber + " deleted");
                } else {
                    socketOut.println("+OK " + msgNumber + " " + message.getFileSize());
                }
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                socketOut.println("-ERR Invalid message number");
            }
        } else {
            socketOut.println("-ERR Too many arguments");
        }
    }

    private void handleRetr(String line) throws IOException {
        if (!isAuthenticated) {

            socketOut.println("-ERR Authenticate first");
            return;
        }
        if (line.equals("")) {
            socketOut.println("-ERR Invalid RETR usage");
            return;
        }
        try {
            int msgNumber = Integer.parseInt(line);
            if (msgNumber <=0 ) {
                socketOut.println("-ERR Message " + msgNumber + " deleted");
                return;
            }
            MailMessage message = currentMailbox.getMailMessage(msgNumber);
            if (message.isDeleted()) {
                socketOut.println("-ERR Message " + msgNumber + " deleted");
                return;
            }
            socketOut.println("+OK " + message.getFileSize() + " octets");
            try (BufferedReader reader = new BufferedReader(new FileReader(message.getFile()))) {
                String msgLine;
                while ((msgLine = reader.readLine()) != null) {
                    socketOut.println(msgLine);
                }
                socketOut.println(".");
            }
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            socketOut.println("-ERR Invalid message number");
        }
    }

    private void handleDele(String line) throws IOException {
        if (!isAuthenticated) {
            socketOut.println("-ERR Authenticate first");
            return;
        }
        if (line.equals("")) {
            socketOut.println("-ERR Invalid DELE usage");
            return;
        }
        try {
            int msgNumber = Integer.parseInt(line);
            MailMessage message = currentMailbox.getMailMessage(msgNumber);
            if (message.isDeleted()) {
                socketOut.println("-ERR Message " + msgNumber + " already deleted");
            } else {
                message.tagForDeletion();
                socketOut.println("+OK Message " + msgNumber + " marked for deletion");
            }
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            socketOut.println("-ERR Invalid message number");
        }
    }

    private void handleRset() throws IOException {
        if (!isAuthenticated) {
            socketOut.println("-ERR Authenticate first");
            return;
        }
        for (MailMessage message: currentMailbox) {
        	if (message.isDeleted()) {
        		message.undelete();
        	}
        	
        }
        
        socketOut.println("+OK");
    }

    private void handleNoop() throws IOException {
        socketOut.println("+OK");
    }

    /**
     * Main process for the POP3 server. Handles the argument parsing and
     * creates a listening server socket. Repeatedly accepts new connections
     * from individual clients, creating a new server instance that handles
     * communication with that client in a separate thread.
     *
     * @param args The command-line arguments.
     * @throws IOException In case of an exception creating the server socket or
     *                     accepting new connections.
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            throw new RuntimeException(
                    "This application must be executed with exactly one argument, the listening port.");
        }

        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]))) {
            serverSocket.setReuseAddress(true);

            System.out.println("Waiting for connections on port " + serverSocket.getLocalPort() + "...");
            // noinspection InfiniteLoopStatement
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted a connection from " + socket.getRemoteSocketAddress());
                try {
                    MyPOPServer handler = new MyPOPServer(socket);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("Error setting up an individual client's handler.");
                    e.printStackTrace();
                }
            }
        }
    }
}