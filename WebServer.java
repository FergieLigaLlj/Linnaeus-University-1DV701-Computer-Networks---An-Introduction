import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The WebServer class that handles all incoming requests.
 * This is the main server class that create and handle the Socket-Threads.
 */

public class WebServer {
  public static void main(String[] args) throws IOException {

    // Argument Validation
    if (args.length != 2) {
      System.out.println("Cannot be ran without parameters/arguments.");
      System.out.println("Utilize the following format: 'java WebServer <port> <path>'\n");
      return;
    }

    // Path Validation
    Path path = pathValidation(args[1]);
    if (path == null) {
      System.out.println("Path is invalid/incorrect or doesn't exist.\n");
      return;
    }

    // Port Validation
    int port = portValidation(args[0]);
    if (port == 0) {
      System.out.println("Port is invalid.");
      System.out.println("Port may only contain numbers and cannot be 0.\n");
      return;
    }

    // Create ServerSocket
    ServerSocket serverSocket = new ServerSocket(port);
    System.out.println("Server started on port: " + port);
    System.out.println("Detailed path: " + path.toAbsolutePath() + "\n");

    // Main loop - Stop using Ctrl-C or Ctrl-D
    while (true) {
      try {
        Socket socket = serverSocket.accept();
        System.out.println("\nAssigned a new client to a separate thread.");
        SocketHandler socketHandler = new SocketHandler(socket, path);
        socketHandler.start();

      } catch (Exception e) {
        serverSocket.close();
        throw new IOException();
      }
    }
  }

  /**
   * Validates the port to ensure that it's a number and not equal to zero.
   *
   * @param port The port.
   * @return The port as an integer.
   */
  private static int portValidation(String port) {
    try {
      return Integer.parseInt(port);
    } catch (NumberFormatException ignored) {
      System.out.println("Port is incorrect.");
      System.out.println("Port may only contain numbers.\n");
      throw new NumberFormatException();
    }
  }

  /**
   * Validates the path to ensure that it exists.
   *
   * @param path The path.
   * @return A Path Object if it exists. Null otherwise.
   */
  private static Path pathValidation(String path) {
    Path pathObject = Path.of(path);
    if (Files.exists(pathObject)) {
      return pathObject;
    } else return null;
  }

}
