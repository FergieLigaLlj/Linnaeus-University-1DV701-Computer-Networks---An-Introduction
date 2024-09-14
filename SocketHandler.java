import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * The SocketHandler utilizes threading to run and handle multiple sockets at once.
 */

public class SocketHandler extends Thread {
    private final Socket socket;
    private final Path path;

    private enum LoginStatus {
        LOGGED_IN, LOGGED_OUT, INTERNAL_SERVER_ERROR
    }

    /**
     * Basic constructor that stores the socket created.
     *
     * @param socket The socket.
     */
    public SocketHandler(Socket socket, Path path) {
        this.socket = socket;
        this.path = path;
    }

    /**
     * The main method for handling all GET and POST requests.
     * This method is run using the start() method.
     */
    @Override
    public void run() {
        try {

            // Retrieve reader & writer
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintStream writer = new PrintStream(socket.getOutputStream(), true);

            // Parse filename from GET request
            String requestString = reader.readLine();
            int start = requestString.indexOf("/") + 1;
            int end = requestString.lastIndexOf(" ");
            String[] parsedStrings = requestString.substring(start, end).split("/");

            // Neat printout
            String[] requestData = requestString.split(" ");
            System.out.println(reader.readLine() + ", Method: " + requestData[0]
                + ", Path: " + requestData[1] + ", Version: " + requestData[2]);

            // POST Request check
            LoginStatus loginStatus = LoginStatus.LOGGED_OUT;
            if (requestData[0].equals("POST")) {
                loginStatus = postRequest(reader);
                switch (loginStatus) {

                    case LOGGED_OUT:
                        // 401 Unauthorised
                        unauthorisedResponse(writer);
                        System.out.println("Invalid Login Details!");

                        // Close stream & socket
                        reader.close();
                        writer.close();
                        socket.close();
                        return;

                    case INTERNAL_SERVER_ERROR:
                        // 500 Internal Server Error
                        internalServerErrorResponse(writer);
                        System.out.println("Login Database File Not Found!");

                        // Close stream & socket
                        reader.close();
                        writer.close();
                        socket.close();
                        return;

                    case LOGGED_IN:
                        System.out.println("Login Successful!");
                }
            }


            // Hierarchy check
            String parsedString = "";
            if (parsedStrings.length == 1) {
                parsedString = parsedStrings[parsedStrings.length - 1];
            } else {
                if (new File(parsedStrings[1]).exists()) {
                    parsedString = parsedStrings[parsedStrings.length - 1];
                } else {
                    for (String s : parsedStrings) {
                        parsedString += s + "/";
                    }
                    parsedString = parsedString.substring(0, parsedString.length() - 1);
                }
            }

            // Default to index.html on empty/directory input
            String fileName;
            boolean isDirectory = false;
            if (parsedString.isBlank()) {
                parsedString = "index.html";
                fileName = path + "/index.html";
            } else {
                fileName = path + "/" + parsedString;
                if (new File(fileName).isDirectory()) {
                    isDirectory = true;
                    fileName += "/index.html";
                    System.out.println("Requested item is a directory! (Defaulting to index.html within folder)");
                }
            }

            // Check if file exists
            File file = new File(fileName);
            if (file.exists()) {
                System.out.println("Server request file exists!");

                // Make sure loggedInPage.html cannot be accessed directly without logging in.
                if ((fileName.equals("loggedInPage.html") || fileName.equals("memes/loggedInPage.html"))
                        && !loginStatus.equals(LoginStatus.LOGGED_IN)) {
                    unauthorisedResponse(writer);

                    // Close stream & socket
                    reader.close();
                    writer.close();
                    socket.close();
                    return;
                }

                // Retrieve mimetype
                String mimeType;
                if (isDirectory) {
                    mimeType = "html";
                } else {
                    String[] splitUpString = parsedString.split("\\.");
                    mimeType = splitUpString[splitUpString.length - 1]; // Last index in the list
                }

                // Return Content-Type depending on mimeType
                // Switch Case could be a good option if more mimeTypes are added
                if (mimeType.equals("png")) {
                    writer.println("HTTP/1.0 200 OK");
                    System.out.print("Response: 200 OK, Version: HTTP/1.0, Date: " + LocalDate.now());
                    writer.println("Content-Type: image/png");
                    System.out.print(", Content-Type: image/png");
                } else if (mimeType.equals("html") || mimeType.equals("htm")) {
                    writer.println("HTTP/1.0 200 OK");
                    System.out.print("Response: 200 OK, Version: HTTP/1.0, Date: " + LocalDate.now());
                    writer.println("Content-Type: text/html");
                    System.out.print(", Content-Type: text/html");
                } else {
                    // 500 Internal Server Error
                    internalServerErrorResponse(writer);
                    System.out.println("WebServer cannot handle mime-type: " + mimeType);

                    // Close stream & socket
                    reader.close();
                    writer.close();
                    socket.close();
                    return;
                }

                // Return Content-Length
                writer.println("Content-Length: " + file.length());
                System.out.println(", Content-Length: " + file.length());
                writer.println();

                // Write bytes using FileInputStream
                FileInputStream fileInputStream = new FileInputStream(file);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    writer.write(buffer, 0, bytesRead);
                }
                fileInputStream.close();

            } else notFoundResponse(writer); // 404 Not Found

            // Close stream & socket
            reader.close();
            writer.close();
            socket.close();

        } catch (IOException ignored) {}
    }

    /**
     * Method for handling the login page's POST request.
     *
     * @param reader The reader that handles the POST request.
     * @return The LoginStatus depending on details provided.
     * @throws IOException Error Exception.
     */
    private LoginStatus postRequest(BufferedReader reader) throws IOException {

        // POST Request Headers
        String line;
        int contentLength = 0;
        while ((line = reader.readLine() ) != null && !line.isEmpty()) {
            if (line.toLowerCase().contains("content-length: ")) {
                contentLength = Integer.parseInt(line.substring(16));
            }
        }

        // Get Request Body utilizing Content-Length
        StringBuilder requestBody = new StringBuilder();
        for (int i = 0; i < contentLength; i++) {
            requestBody.append((char) reader.read());
        }

        // Parse Body for Username and Password
        String[] userData = requestBody.toString().split("&");
        String username = userData[0].split("=")[1];
        String password = userData[1].split("=")[1];

        // Update LoginStatus
        return loginValidation(username, password);
    }

    /**
     * Simple login validation.
     * Throws Internal Server Error - FileNotFoundException if login file is missing.
     *
     * @param username The username.
     * @param password The password.
     * @return The LoginStatus after validation.
     */
    private LoginStatus loginValidation(String username, String password) {
        File file = new File("logins.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line : reader.lines().collect(Collectors.toList())) {
                String[] data = line.split(":");
                if (username.equals(data[0]) && password.equals(data[1])) {
                    return LoginStatus.LOGGED_IN;
                }
            }
        } catch (IOException ignored) {
            return LoginStatus.INTERNAL_SERVER_ERROR;
        }
        return LoginStatus.LOGGED_OUT;
    }

    /**
     * 401 Unauthorised HTML Response
     *
     * @param writer The PrintStream writer.
     * @throws IOException Error Exception.
     */
    private void unauthorisedResponse(PrintStream writer) throws IOException {
        writer.println("HTTP/1.0 401 Unauthorised");
        writer.println("Content-Type: text/html");
        writer.println();
        byte[] htmlResponse = "<h1>401 Unauthorised</h1>".getBytes();
        writer.write(htmlResponse);
        System.out.println("Login Credentials are Invalid!");
        System.out.println("Response: 401 Unauthorised, Version: HTTP/1.0, Date: " + LocalDate.now()
                + ", Content-Type: text/html, Content-Length: " + htmlResponse.length);
    }

    /**
     * 404 Not Found HTML Response
     *
     * @param writer The PrintStream writer.
     * @throws IOException Error Exception.
     */
    private void notFoundResponse(PrintStream writer) throws IOException {
        writer.println("HTTP/1.0 404 Not Found");
        writer.println("Content-Type: text/html");
        writer.println();
        byte[] htmlResponse = "<h1>404 Not Found</h1>".getBytes();
        writer.write(htmlResponse);
        System.out.println("Server request file does not exist!");
        System.out.println("Response: 404 Not Found, Version: HTTP/1.0, Date: " + LocalDate.now()
                + ", Content-Type: text/html, Content-Length: " + htmlResponse.length);
    }

    /**
     * 500 Internal Server Error HTML Response
     *
     * @param writer The PrintStream writer.
     * @throws IOException Error Exception.
     */
    private void internalServerErrorResponse(PrintStream writer) throws IOException {
        writer.println("HTTP/1.0 Internal Server Error");
        writer.println("Content-Type: text/html");
        writer.println();
        byte[] htmlResponse = "<h1>Internal Server Error</h1>".getBytes();
        writer.write(htmlResponse);
        System.out.println("An Internal Server Error have occurred!");
        System.out.println("Response: Internal Server Error, Version: HTTP/1.0, Date: " + LocalDate.now()
                + ", Content-Type: text/html, Content-Length: " + htmlResponse.length);
    }
}
