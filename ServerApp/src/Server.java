import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
	
	private int m_port;
	public Server(int port) {
		m_port = port;
	}
	
	public void run() {
		ServerSocket serverSocket = null;
		try {
		    serverSocket = new ServerSocket(m_port);
			while(true) {
				System.out.println("Waiting for a connection...");
				ServerInstance serverInstance = new ServerInstance(serverSocket.accept());
		        Thread thread = new Thread(serverInstance);
		        thread.start();
			}
			
		} catch (IOException e) {
			System.out.println("Couldn't open the server socket");
		}
	}
	public class ServerInstance implements Runnable {

	    private Socket m_socket;

	    public ServerInstance(Socket socket) {
	         m_socket = socket;
	    }

	    public void run() {
	    	String message = null;
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(m_socket.getInputStream()));
				PrintWriter printWriter = new PrintWriter(m_socket.getOutputStream(), true);
				message = reader.readLine();
				
				System.out.println("message: " + message);
				System.out.println("Client IP: " + m_socket.getRemoteSocketAddress().toString());
				System.out.println("Port number: " + m_socket.getPort());
				printWriter.write(message.toUpperCase());
				
				System.out.println();
				System.out.println();
				
				printWriter.close();
				reader.close();
			} catch (IOException exception) {
				System.out.println("Problem with socket: " + exception.toString());
			}
			
	    }
	}
}
