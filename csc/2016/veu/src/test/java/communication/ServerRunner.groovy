package communication

class ServerRunner {
  public static int port = 9708

  public static void main(String[] args) {
    System.setProperty("testEnv", "true")
    Server.main(port as String);
  }
}
