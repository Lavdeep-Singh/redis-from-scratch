import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

  private static final Config config = new Config();

  public static void main(String[] args) {
    createConfig(args);

    System.out.println("System is Up!");

    ServerSocket serverSocket = null;
    int port = getPort();
    try {
      serverSocket = new ServerSocket(port);
      // Since the tester restarts your program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);
      // Wait for connection from client.
      while (true) {
        Socket clientSocket = serverSocket.accept();
        new Thread(() -> {
          try {
            ClientHandler clientHandler = new ClientHandler(clientSocket, config);
            clientHandler.run();
          } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
          }
        }).start();
      }

    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  private static void createConfig(String[] commandLineArguments) {

    for (int i = 0; i < commandLineArguments.length; i++) {
      String arg = commandLineArguments[i];
      if (arg.equalsIgnoreCase("--dir")) {
        config.setConfig("dir", commandLineArguments[++i]);
      }
      if (arg.equalsIgnoreCase("--dbfilename")) {
        config.setConfig("dbfilename", commandLineArguments[++i]);
      }
      if (arg.equalsIgnoreCase("--port")) {
        config.setConfig("port", commandLineArguments[++i]);
      }
    }
  }

  private static int getPort() {
    String portStr = config.getConfig("port");
    if (portStr == null) {
      return 6379;
    }
    return Integer.parseInt(portStr);
  }
}
