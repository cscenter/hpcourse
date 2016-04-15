package communication

import spock.lang.Specification

class ServerTest extends Specification {
  def 'run'() {
    when:
    System.setProperty("testEnv", "true")
    Server.main(Client.port as String);

    then:
    println 'exit'
  }
}
