
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

/**
 * This is the one and only class utilized for the TFTP Server.
 * IMPORTANT: Update the READDIR/WRITEDIR in case you are using Windows or alike.
 */

public class TFTPServer 
{
	public static final int TFTPPORT = 4970; // Port - Can be changed
	public static final int BUFSIZE = 516;
	public static final String READDIR = "./public/read/"; // Linux
	public static final String WRITEDIR = "./public/write/"; // Linux
	//public static final String READDIR = ".\\public\\read\\"; // Windows
	//public static final String WRITEDIR = ".\\public\\write\\"; // Windows

	// OP codes
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	/**
	 * The main method for retrieving requests and creating threads for handling.
	 *
	 * @param args not utilized
	 */
	public static void main(String[] args) {
		if (args.length > 0) 
		{
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		//Starting the server
		try 
		{
			TFTPServer server= new TFTPServer();
			server.start();
		}
		catch (SocketException e) 
			{e.printStackTrace();}
	}
	
	private void start() throws SocketException 
	{
		byte[] buf = new byte[BUFSIZE]; // 516 bytes
		
		// Create socket
		DatagramSocket socket= new DatagramSocket(null);
		
		// Create local bind point 
		SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n\n", TFTPPORT);

		// Loop to handle client requests 
		while (true) 
		{        
			
			final InetSocketAddress clientAddress = receiveFrom(socket, buf);
			
			// If clientAddress is null, an error occurred in receiveFrom()
			if (clientAddress == null) 
				continue;

			final StringBuffer requestedFile= new StringBuffer();
			final int reqtype = ParseRQ(buf, requestedFile);

			new Thread() 
			{
				public void run() 
				{
					try 
					{
						DatagramSocket sendSocket= new DatagramSocket(0);

						// Connect to client
						sendSocket.connect(clientAddress);

						// Printout
						if (reqtype == OP_RRQ) System.out.print("Read ");
						else if (reqtype == OP_WRQ) System.out.print("Write ");

						else { // Invalid Request
							System.err.println("Error. Not a Read/Write Request.");
							send_ERR(sendSocket, (short) 4, "Invalid Request.");
							return; // Error Code 4 = "Illegal TFTP operation"
						}

						// Printout
						System.out.println("request for " + clientAddress.getAddress()
								+ " using port " + clientAddress.getPort());
								
						// Read request
						if (reqtype == OP_RRQ) {
							requestedFile.insert(0, READDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
						}

						// Write request
						if (reqtype == OP_WRQ) {
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);  
						}
						sendSocket.close();
					} 
					catch (SocketException e) 
						{e.printStackTrace();}
				}
			}.start();
		}
	}
	
	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 *
	 * @param socket (socket to read from)
	 * @param buf (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {

		// DatagramPacket to be Received
		DatagramPacket packet = new DatagramPacket(buf, buf.length);

		try { // Receive Packet
			socket.receive(packet);
		} catch (IOException e) {
			System.err.println("Error. Packet could not be received.");
			return null;
		}
		
		// Get Client Address and Port from the Packet
		return new InetSocketAddress(packet.getAddress(), packet.getPort());
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 * 
	 * @param buf (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
		ByteBuffer bufferWrap = ByteBuffer.wrap(buf);

		// Array Validation
		if (!bufferWrap.hasArray()) return OP_ERR;

		// Opcode - First 2 bytes
		short opcode = bufferWrap.getShort();

		// Length of file name in request
		int length = 0;
		while (bufferWrap.hasRemaining()) {
			byte buffer = bufferWrap.get();
			if (buffer == 0) break;
			length++;
		}

		// Filename
		String fileName = new String(buf, 2,  length); // Offset 2 - Remove Block Number Bytes
		requestedFile.append(fileName);

		// Mode
		String mode = new String(buf, length+3, 5);
		if (mode.equalsIgnoreCase("octet")) return opcode;
		else {
			System.err.println("Error. Incorrect Mode (Not Octet).");
			return OP_ERR;
		}
	}

	/**
	 * Handles RRQ and WRQ requests 
	 * 
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) {

		// Create File Object
		File file = new File(requestedFile);

		// Read Request (RRQ)
		if (opcode == OP_RRQ) readRequest(sendSocket, file);

		// Write Request (WRQ)
		else if (opcode == OP_WRQ) writeRequest(sendSocket, file);
	}

	/**
	 * Handles the Read Requests (RRQ).
	 * Sends file content packets and ensures ACK packets are returned.
	 *
	 * @param sendSocket socket for reading/sending
	 * @param file file requested
	 */
	private void readRequest(DatagramSocket sendSocket, File file) {
		try (FileInputStream inputStream = new FileInputStream(file)) {
			byte[] buffer = new byte[BUFSIZE-4]; // 512 bytes
			short block = 1;
			int fileLength;
			do {

				// Retrieve File Length
				fileLength = inputStream.read(buffer);

				// Error Validation [-1 = File Empty]
				if (fileLength == -1) fileLength = 0;

				// Create Packet
				ByteBuffer byteBuffer = ByteBuffer.allocate(BUFSIZE);	// Allocate 516 bytes
				byteBuffer.putShort((short) OP_DAT);					// First 2 bytes
				byteBuffer.putShort(block);								// The following 2 bytes
				byteBuffer.put(buffer, 0, fileLength);			// File Content

				// Opcode + Block Number at the front of packet --> fileLength + 4 = 516 total bytes
				DatagramPacket packet = new DatagramPacket(byteBuffer.array(), fileLength + 4);

				// Send Block & Receive ACK Packet
				if (send_DATA_receive_ACK(sendSocket, packet, block)) {
					System.out.println("Block #" + block + " was sent successfully!");
					block++;
				} else {
					System.out.println("Connection was lost.");
					send_ERR(sendSocket, (short) 0, "Connection was lost during transfer.");
					// Error Code 0 = "Not defined"
				}

			} while (fileLength == 512); // Run for as long as there's more data to be sent!
			System.out.println("File transfer ended!\n");

		} catch (FileNotFoundException e) {
			System.err.println("Error. File was not found.");
			send_ERR(sendSocket, (short) 1, "File does not exist.");
			// Error Code 1 = "File not found"

		} catch (IOException e) {
			System.err.println("Error. IO Exception in Read Request.");
		}
	}

	/**
	 * Handles the Write Requests (WRQ).
	 * Reads received file content packets and returns ACK packets.
	 *
	 * @param sendSocket socket for reading/sending
	 * @param file file to be received
	 */
	private void writeRequest(DatagramSocket sendSocket, File file) {
		if (file.exists()) { // Error Validation
			send_ERR(sendSocket, (short) 6, "File with that name already exists.");
			return; // Error Code 6 = "File exists"
		}

		try (FileOutputStream outputStream = new FileOutputStream(file)) {
			short block = 0;
			DatagramPacket filePacket;
			ByteBuffer ackBuffer;

			// Send First ACK Packet
			ackBuffer = ByteBuffer.allocate(BUFSIZE);
			ackBuffer.putShort((short) OP_ACK);
			ackBuffer.putShort(block);
			sendSocket.send(new DatagramPacket(ackBuffer.array(), 4));
			block++;
			do {

				// Create ACK Packet
				ackBuffer = ByteBuffer.allocate(BUFSIZE);	// Allocate 516 bytes
				ackBuffer.putShort((short) OP_ACK);			// First 2 bytes
				ackBuffer.putShort(block);					// The following 2 bytes
				DatagramPacket ackPacket = new DatagramPacket(ackBuffer.array(), 4);

				// Send ACK Packet & Receive File Content
				filePacket = receive_DATA_send_ACK(sendSocket, ackPacket, block);

				// Write File Data
				if (filePacket != null) {
					byte[] fileData = filePacket.getData();
					try {

						// Only Write File Data [Skip Opcode & Block Number]
						outputStream.write(fileData, 4, filePacket.getLength() - 4);
						block++;
						System.out.println("Block #" + block + " was received successfully!");

					} catch (IOException e) {
						System.err.println("Error. Could not write file correctly.");
					}

				} else {
					System.out.println("Connection was lost.");
					send_ERR(sendSocket, (short) 0, "Connection was lost during transfer.");
					// Error Code 0 = "Not defined"
				}

				// Run for as long as there's more data to be sent!
			} while (filePacket != null && filePacket.getLength() == 516);
			System.out.println("File writing ended!\n");

		} catch (FileNotFoundException e) {
			System.err.println("Error. File was not found.");
			send_ERR(sendSocket, (short) 1, "File does not exist.");
			// Error Code 1 = "File not found"

		} catch (IOException e) {
			System.err.println("Error. IO Exception in Write Request.");
		}
	}

	/**
	 * Handles the Sending of Data.
	 * Ensured ACK Packets are returned.
	 *
	 * @param socket socket for reading/sending
	 * @param packet packet to be sent
	 * @param block current blocks number
	 * @return true/false if successful or not
	 */
	private boolean send_DATA_receive_ACK(DatagramSocket socket, DatagramPacket packet, short block) {
		byte[] bytes = new byte[BUFSIZE]; // 516 bytes
		DatagramPacket ackPacket = new DatagramPacket(bytes, bytes.length); // Pre-Generated for Return ACK
		try {

			// Send File & Receive ACK
			socket.send(packet);		// Send File Packet
			socket.receive(ackPacket);	// Receive Return ACK Packet

			// Opcode
			ByteBuffer ackBuffer = ByteBuffer.wrap(ackPacket.getData());
			short ackOpcode = ackBuffer.getShort(); // First 2 bytes = Opcode
			if (ackOpcode == OP_ERR) {
				System.err.println("Error. Packet Opcode = 5 (OP_ERR).");
				return false;
			}

			// Block Number
			short ackBlock = ackBuffer.getShort(); // Next 2 bytes = Block Number

			if (ackBlock == block) return true;
			else {
				System.err.println("Error. Block Number Invalid.");
				return false;
			}

		} catch (IOException e) {
			System.err.println("Error. IO Exception while Sending File Packets / Receiving ACK Packets.");
			return false;
		}
	}

	/**
	 * Handles Receiving Data Packets
	 * Returns ACK Packets for each Data Packet received
	 *
	 * @param socket socket for reading/sending
	 * @param ackPacket ACK packet to be returned
	 * @param ackBlock current blocks number to be received
	 * @return
	 */
	private DatagramPacket receive_DATA_send_ACK(DatagramSocket socket, DatagramPacket ackPacket, short ackBlock) {
		byte[] bytes = new byte[BUFSIZE]; // 516 bytes
		DatagramPacket filePacket = new DatagramPacket(bytes, bytes.length);
		try {

			// Receive File Content & Send ACK
			socket.receive(filePacket);	// Receive File Content Packet
			socket.send(ackPacket);		// Send ACK Packet

			// Opcode
			ByteBuffer fileBuffer = ByteBuffer.wrap(filePacket.getData());
			short opcode = fileBuffer.getShort(); // First 2 bytes = Opcode
			if (opcode == OP_ERR) {
				System.err.println("Error. Packet Opcode = 5 (OP_ERR).");
				return null;
			}

			// Block Number
			short block = fileBuffer.getShort(); // Next 2 bytes = Block Number

			if (block == ackBlock) return filePacket;
			else {
				System.err.println("Error. Block Number Invalid.");
				return null;
			}

		} catch (IOException e) {
			System.err.println("Error. IO Exception while Receiving File Packets / Sending ACK Packets.");
			return null;
		}
	}

	/**
	 * Returns/Sends and Error Packet using standard error codes and custom messages.
	 *
	 * @param sendSocket socket for reading/sending
	 * @param errorCode standard error code to be used
	 * @param errorMessage custom error message for better readability
	 */
	private void send_ERR(DatagramSocket sendSocket, Short errorCode, String errorMessage) {
		ByteBuffer errorBuffer = ByteBuffer.allocate(BUFSIZE);	// Allocate 516 bytes
		errorBuffer.putShort((short) OP_ERR);					// First 2 bytes = Error Opcode
		errorBuffer.putShort(errorCode);						// Next 2 bytes = Error Code
		errorBuffer.put(errorMessage.getBytes());				// Error Message as bytes

		int length = errorMessage.getBytes().length - 4;		// Length = Error Message + First 4 bytes
		DatagramPacket errorPacket = new DatagramPacket(errorBuffer.array(), length);

		try {
			sendSocket.send(errorPacket);
		} catch (IOException e) {
			System.err.println("Error. Could not send error packet.");
		}
	}
	
}



