import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Cette classe permet la reception d'un paquet UDP sur le port de reception
 * UDP/DNS. Elle analyse le paquet et extrait le hostname
 * 
 * Il s'agit d'un Thread qui ecoute en permanance pour ne pas affecter le
 * deroulement du programme
 * 
 * @author Max
 *
 */

public class UDPReceiver extends Thread {
	/**
	 * Les champs d'un Packet UDP 
	 * --------------------------
	 * En-tete (12 octects) 
	 * Question : l'adresse demande 
	 * Reponse : l'adresse IP
	 * Autorite :
	 * info sur le serveur d'autorite 
	 * Additionnel : information supplementaire
	 */

	/**
	 * Definition de l'En-tete d'un Packet UDP
	 * --------------------------------------- 
	 * Identifiant Parametres 
	 * QDcount
	 * Ancount
	 * NScount 
	 * ARcount
	 * 
	 * L'identifiant est un entier permettant d'identifier la requete. 
	 * parametres contient les champs suivant : 
	 * 		QR (1 bit) : indique si le message est une question (0) ou une reponse (1). 
	 * 		OPCODE (4 bits) : type de la requete (0000 pour une requete simple). 
	 * 		AA (1 bit) : le serveur qui a fourni la reponse a-t-il autorite sur le domaine? 
	 * 		TC (1 bit) : indique si le message est tronque.
	 *		RD (1 bit) : demande d'une requete recursive. 
	 * 		RA (1 bit) : indique que le serveur peut faire une demande recursive. 
	 *		UNUSED, AD, CD (1 bit chacun) : non utilises. 
	 * 		RCODE (4 bits) : code de retour.
	 *                       0 : OK, 1 : erreur sur le format de la requete,
	 *                       2: probleme du serveur, 3 : nom de domaine non trouve (valide seulement si AA), 
	 *                       4 : requete non supportee, 5 : le serveur refuse de repondre (raisons de s�ecurite ou autres).
	 * QDCount : nombre de questions. 
	 * ANCount, NSCount, ARCount : nombre d�entrees dans les champs �Reponse�, Autorite,  Additionnel.
	 */

	protected final static int BUF_SIZE = 1024;
	protected String SERVER_DNS = null;//serveur de redirection (ip)
	protected int portRedirect = 53; // port  de redirection (par defaut)
	protected int port; // port de r�ception
	private String adrIP = null; //bind ip d'ecoute
	private String DomainName = "none";
	private String DNSFile = null;
	private boolean RedirectionSeulement = false;
	
	
	private class ClientInfo { //quick container
		public String client_ip = null;
		public int client_port = 0;
	};
	private HashMap<Integer, ClientInfo> Clients = new HashMap<>();
	
	private boolean stop = false;

	
	public UDPReceiver() {
	}

	public UDPReceiver(String SERVER_DNS, int Port) {
		this.SERVER_DNS = SERVER_DNS;
		this.port = Port;
	}
	
	
	public void setport(int p) {
		this.port = p;
	}

	public void setRedirectionSeulement(boolean b) {
		this.RedirectionSeulement = b;
	}

	public String gethostNameFromPacket() {
		return DomainName;
	}

	public String getAdrIP() {
		return adrIP;
	}

	public String getSERVER_DNS() {
		return SERVER_DNS;
	}

	public void setSERVER_DNS(String server_dns) {
		this.SERVER_DNS = server_dns;
	}



	public void setDNSFile(String filename) {
		DNSFile = filename;
	}

	public void run() {
		try {
			DatagramSocket serveur = new DatagramSocket(this.port); // *Creation d'un socket UDP
			
			// *Boucle infinie de reception
			while (!this.stop) {
				
				byte[] buff = new byte[1000];
				DatagramPacket paquetRecu = new DatagramPacket(buff,buff.length);
				System.out.println("Serveur DNS  "+serveur.getLocalAddress()+"  en attente sur le port: "+ serveur.getLocalPort());

				// *Reception d'un paquet UDP via le socket
				serveur.receive(paquetRecu);
				System.out.println(buff.toString());
				System.out.println("paquet recu du  "+paquetRecu.getAddress()+"  du port: "+ paquetRecu.getPort());

				StringBuilder domainName = new StringBuilder();
				
				int currentLocation = 12;
				while(true) {
					int numberOfBytesToRead = buff[currentLocation];
					if(numberOfBytesToRead == 0) break;
					for(int i = 1; i <= numberOfBytesToRead; i++) {
						domainName.append((char)buff[currentLocation + i]);
					}
					domainName.append(".");
					currentLocation += numberOfBytesToRead+1;
				}
				
				if(ByteBuffer.allocate(8).putInt(buff[3]).array()[0] == 0) { //request
					
					int requestType = Integer.parseInt(Integer.toBinaryString(buff[currentLocation+1]) + Integer.toBinaryString(buff[2+currentLocation]), 2);
					int classType = Integer.parseInt(Integer.toBinaryString(buff[currentLocation+3]) + Integer.toBinaryString(buff[4+currentLocation]), 2);
					
					if(requestType == 1 && classType == 1) {
						ClientInfo clientInfo = new ClientInfo();
						clientInfo.client_ip = paquetRecu.getAddress().getHostAddress();
						clientInfo.client_port = paquetRecu.getPort();
						Clients.put(Integer.parseInt(Integer.toBinaryString(buff[0]) + Integer.toBinaryString(buff[1]), 2), clientInfo);
						
						if(this.RedirectionSeulement) {
							UDPSender sender = new UDPSender(SERVER_DNS, this.port, serveur);
							sender.SendPacketNow(paquetRecu);
						} else {
							QueryFinder queryFinder = new QueryFinder(DNSFile);
							List<String> ipAdresses = queryFinder.StartResearch(domainName.toString());
							if(ipAdresses.isEmpty()) {
								UDPSender sender = new UDPSender(SERVER_DNS, this.port, serveur);
								sender.SendPacketNow(paquetRecu);
							} else {
								byte[] data = UDPAnswerPacketCreator.getInstance().CreateAnswerPacket(buff, ipAdresses);
								DatagramPacket datagramPacket = new DatagramPacket(data, data.length);
								serveur.send(datagramPacket);
							}
						}
					}
				} else {
					
					if(Integer.toBinaryString(buff[3]).endsWith("0000")) {
						// no error of type (no such name)
						List<String> ipAddresses = new ArrayList<String>();
						int requestType = Integer.parseInt(Integer.toBinaryString(buff[currentLocation+1]) + Integer.toBinaryString(buff[2+currentLocation]), 2);
						int classType = Integer.parseInt(Integer.toBinaryString(buff[currentLocation+3]) + Integer.toBinaryString(buff[4+currentLocation]), 2);
						
						if(requestType == 1 && classType == 1) {

							
							int numberOfResponses = Integer.parseInt(Integer.toBinaryString(buff[6]) + Integer.toBinaryString(buff[7]), 2);
							
							for(int i = 0; i < numberOfResponses; i++) {
								 requestType = Integer.parseInt(Integer.toBinaryString(buff[currentLocation+7+(i*16)]) + Integer.toBinaryString(buff[8+currentLocation+(i*16)]), 2);
								 classType = Integer.parseInt(Integer.toBinaryString(buff[currentLocation+9+(i*16)]) + Integer.toBinaryString(buff[10+currentLocation+(i*16)]), 2);
								 
								 if(requestType != 1 || classType != 1) {
									 throw new Exception("Problem (shouldn't happen)");
								 }
								 
								 int dataLength = Integer.parseInt(Integer.toBinaryString(buff[currentLocation+15+(i*16)]) + Integer.toBinaryString(buff[16+currentLocation+(i*16)]), 2);
								 if(dataLength != 4) {
									 throw new Exception("Problem (shouldn't happen)");
								 }
								 
								 StringBuilder ipAddress = new StringBuilder();
								 
								 for(int j = 0; j < 4; j++) {
									 int value = (buff[currentLocation+17+j+(i*16)] >= 0) ? buff[currentLocation+17+j+(i*16)] : buff[currentLocation+17+j+(i*16)] + 256;
									 ipAddress.append(value);
									 ipAddress.append(".");
								 }
								 ipAddresses.add(ipAddress.toString().substring(0, ipAddress.toString().length()-1));
							}
							
							AnswerRecorder answerRecorder = new AnswerRecorder(DNSFile);
							for(String ipAdresse : ipAddresses) {
								answerRecorder.StartRecord(domainName.toString(), ipAdresse);
							}
							ClientInfo clientInfo = Clients.get(Integer.parseInt(Integer.toBinaryString(buff[0]) + Integer.toBinaryString(buff[1]), 2));

							UDPSender sender = new UDPSender(clientInfo.client_ip, clientInfo.client_port, serveur);
							sender.SendPacketNow(new DatagramPacket(buff, buff.length));
						}
					}
				}
			}
			serveur.close(); //closing server
		} catch (Exception e) {
			System.err.println("Probl�me � l'ex�cution :");
			e.printStackTrace(System.err);
		}
	}
}
