package ca.yorku.eecs3214.mail.net;

import ca.yorku.eecs3214.mail.mailbox.MailWriter;
import ca.yorku.eecs3214.mail.mailbox.Mailbox;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MySMTPServer extends Thread {

    private final Socket socket;
    private final BufferedReader socketIn;
    private final PrintWriter socketOut;

    // TODO Additional properties, if needed
    Mailbox mail;
    boolean flag = false;
    List<Mailbox> recipients = new ArrayList<Mailbox>();
    String emailAddress;
    MailWriter write;
    /**
     * Initializes an object responsible for a connection to an individual client.
     *
     * @param socket The socket associated to the accepted connection.
     * @throws IOException If there is an error attempting to retrieve the socket's information.
     */
    public MySMTPServer(Socket socket) throws IOException {
        this.socket = socket;
        this.socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.socketOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Handles the communication with an individual client. Must send the initial welcome message, and then repeatedly
     * read requests, process the individual operation, and return a response, according to the SMTP protocol. Empty
     * request lines should be ignored. Only returns if the connection is terminated or if the QUIT command is issued.
     * Must close the socket connection before returning.
     */
    @Override
    public void run() {
        try (this.socket) {
        	        socketOut.println("220 Welcome to MySMTPServer");
        	        while (true) {
        	            String userInput = socketIn.readLine();
        	            String[] input = userInput.split(" ");
        	            System.out.println(input);
        	            if (userInput.equalsIgnoreCase("NOOP")) {
        	                socketOut.println("250 OK");
        	            } else if (userInput.equalsIgnoreCase("QUIT")) {
        	                socketOut.println("221 Bye");
        	                break; // Exit the loop to close the connection
        	            } else if (input[0].equalsIgnoreCase("VRFY")){
        	            	
        	            	
        	            	if(input.length == 1) { //invalid arguments
        	            		socketOut.println("501");
        	            	}
        	            	else if (Mailbox.isValidUser(input[1])) { //valid
        	            		socketOut.println("250");
        	            	}
        	            	else { //user not found
        	            		socketOut.println("550");
        	            	}
        	            	
        	            	
        	            } else if (input[0].equalsIgnoreCase("EHLO") ||  input[0].equalsIgnoreCase("HELO")) {
        	            	socketOut.println("250 " + MySMTPServer.getHostName());
        	            	flag = true;
        	            	
        	            }else if (input[0].equalsIgnoreCase("MAIL")) {
        	            	
        	            	    if (!flag) { // EHLO or HELO not called
        	            	        socketOut.println("503 Bad sequence of commands");
        	            	    }
        	            	    else if (input.length < 2 || !input[1].toUpperCase().startsWith("FROM:<") || !input[1].endsWith(">")) {
        	            	        socketOut.println("501 Syntax error in parameters or arguments");
        	            	    } else {
        	            	        // Extract the email address from the command
        	            	        emailAddress = input[1].substring(6, input[1].length() - 1); // Removes FROM:< at the start and > at the end
        	            	        socketOut.println("250 OK");
        	            	        // Reset or prepare for new message handling if necessary
        	            	        this.recipients.clear(); // Assuming you want to reset the recipients list for each new MAIL command
        	            	    }
        	            	

        	            	
        	            }
        	            else if (input[0].equalsIgnoreCase("RCPT")){
        	            	
        	            	
        	            	if (!flag || this.emailAddress == null) { // EHLO or HELO not called
        	                    socketOut.println("503 Bad sequence of commands");
        	                } else if (input.length < 2 || !input[1].toUpperCase().startsWith("TO:<") || !input[1].endsWith(">")) {
        	                    socketOut.println("501 Syntax error in parameters or arguments");
        	                } else {
        	                    // Extract the email address from the command
        	                    String emailAddress = input[1].substring(4, input[1].length() - 1); // Removes TO:< at the start and > at the end
        	                    if (Mailbox.isValidUser(emailAddress)) {
        	                    	
        	                    	Mailbox recipientMailbox = new Mailbox(emailAddress);
        	                    	recipients.add(recipientMailbox); // Add to your list of recipient Mailbox objects
        	                    	socketOut.println("250 OK");
        	                        
        	                    	
        	                    } else {
        	                        socketOut.println("550 No such user here");
        	                    }
        	                }
        	            	
        	            }else if (input[0].equalsIgnoreCase("DATA")) {
        	            	
        	            	if(recipients.isEmpty()) {
        	            		socketOut.println("503");
        	            		continue;
        	            	}
        	            	
        	                socketOut.println("354 End data with <CR><LF>.<CR><LF>");
        	                String emailData = new String();
        	                String line;
        	                while (!(line = socketIn.readLine()).equals(".")) {
        	                    emailData += "" + line + "\r\n";
        	                    
        	                }
        	                
        	                // Now we have the email content in emailData
        	                try (MailWriter writer = new MailWriter(recipients)) {
        	                    writer.write(emailData.toString().toCharArray(), 0, emailData.length());
        	                    // No need to manually flush here because close() will trigger flush()
        	                } catch (IOException e) {
        	                    e.printStackTrace();
        	                    socketOut.println("451 Requested action aborted: local error in processing");
        	                    return;
        	                }
        	                
        	                socketOut.println("250 OK: Message received");
        	                
        	            }else if (input[0].equalsIgnoreCase("RSET")){
        	            	
        	            	this.mail = null;
        	            	this.recipients.clear();
        	            	this.emailAddress = null;
        	            	socketOut.println("250");
        	            	
        	            }

        	            else {
        	                // Handle other commands here
        	                socketOut.println("502 Command not implemented");
        	            }
        	        }
        	    } catch (IOException e) {
                    System.err.println("Error in client's connection handling.");
                    e.printStackTrace();
                } finally {
        	        try {
        	            if (socket != null && !socket.isClosed()) {
        	                socket.close(); // Ensure the socket is closed
        	                this.write.flush();
        	            }
        	        } catch (IOException e) {
        	            System.err.println("Error closing the socket.");
        	            e.printStackTrace();
        	        }
        	    }
        	}
		
         
    

    /**
     * Retrieves the name of the current host. Used in the response of commands like HELO and EHLO.
     * @return A string corresponding to the name of the current host.
     */
    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            try (BufferedReader reader = Runtime.getRuntime().exec(new String[] {"hostname"}).inputReader()) {
                return reader.readLine();
            } catch (IOException ex) {
                return "unknown_host";
            }
        }
    }

    /**
     * Main process for the SMTP server. Handles the argument parsing and creates a listening server socket. Repeatedly
     * accepts new connections from individual clients, creating a new server instance that handles communication with
     * that client in a separate thread.
     *
     * @param args The command-line arguments.
     * @throws IOException In case of an exception creating the server socket or accepting new connections.
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            throw new RuntimeException("This application must be executed with exactly one argument, the listening port.");
        }

        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]))) {
            serverSocket.setReuseAddress(true);
            System.out.println("Waiting for connections on port " + serverSocket.getLocalPort() + "...");
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted a connection from " + socket.getRemoteSocketAddress());
                try {
                    MySMTPServer handler = new MySMTPServer(socket);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("Error setting up an individual client's handler.");
                    e.printStackTrace();
                }
            }
        }
    }
}