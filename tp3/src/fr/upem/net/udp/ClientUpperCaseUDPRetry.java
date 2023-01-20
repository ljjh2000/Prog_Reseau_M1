package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientUpperCaseUDPRetry {

  private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPRetry.class.getName());
  public static final int BUFFER_SIZE = 1024;

  private static void usage() {
    System.out.println("Usage : NetcatUDP host port charset");
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length != 3) {
      usage();
      return;
    }

    var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
    var cs = Charset.forName(args[2]);

    try (var scanner = new Scanner(System.in); var dc = DatagramChannel.open()) {
      dc.bind(null);
      var buffer = ByteBuffer.allocate(BUFFER_SIZE);

      var queue = new ArrayBlockingQueue<String>(10);

      var thread = Thread.ofPlatform().start(() -> {
        while (!Thread.interrupted()) {
          try {
            buffer.clear();
            dc.receive(buffer);
            buffer.flip();
            var msg = cs.decode(buffer).toString();
            queue.put(msg);
          } catch (InterruptedException | ClosedByInterruptException e) {
            logger.info("InterruptedException");
          } catch (IOException e) {
            logger.log(Level.SEVERE, "receive exception", e);
          }

        }
      });

      while (scanner.hasNextLine()) {
        var line = scanner.nextLine();
        var sendBuffer = cs.encode(line);
        dc.send(sendBuffer, server);
        logger.info("The send message was " + line);
        var msg = queue.poll(1, TimeUnit.SECONDS);
        while (msg == null){
          //sendBuffer = cs.encode(line);
          sendBuffer.position(0);
          logger.info("The message is lost, restart send the message " + line);
          dc.send(sendBuffer, server);
          msg = queue.poll(1, TimeUnit.SECONDS);
        }
        logger.info("Received " + msg);
      }

      thread.interrupt();

    }
  }
}