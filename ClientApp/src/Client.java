import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {
	public static void main(String[] args) {
		run();
	}

	public static void run() {
		Scanner scanner = new Scanner(System.in);
		while (true) {
			System.out.println("What's the massage?");
			String message = scanner.next();
			if(message.equals("quit")) break;
			
			Socket socket = null;
			BufferedReader reader = null;
			try {
				socket = new Socket("localhost", 25232);
				PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
				printWriter.write(message);
				
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				System.out.println(reader.readLine());
				
				/*socket.close();
				reader.close();*/
			} catch (IOException exception) {
				System.out.println("Couldn't open the socket: " + exception.toString());
			}
		}
		
		scanner.close();
	}
}
