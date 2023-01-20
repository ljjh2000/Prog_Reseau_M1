package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientUpperCaseUDPFile {
  private final static Charset UTF8 = StandardCharsets.UTF_8;
  private final static int BUFFER_SIZE = 1024;

  private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPFile.class.getName());

  private static void usage() {
    System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length != 5) {
      usage();
      return;
    }

    var inFilename = args[0];
    var outFilename = args[1];
    var timeout = Integer.parseInt(args[2]);
    var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

    // Read all lines of inFilename opened in UTF-8
    var lines = Files.readAllLines(Path.of(inFilename), UTF8);
    var upperCaseLines = new ArrayList<String>();

    try (var dc = DatagramChannel.open()){
      dc.bind(null);

      var queue = new ArrayBlockingQueue<String>(10);
      var receiveBuffer = ByteBuffer.allocate(BUFFER_SIZE);

      var thread = Thread.ofPlatform().start(() -> {
        while (!Thread.interrupted()) {
          try {
            receiveBuffer.clear();
            dc.receive(receiveBuffer);
            receiveBuffer.flip();
            var msg = UTF8.decode(receiveBuffer).toString();
            queue.put(msg);
          } catch (AsynchronousCloseException e){
            logger.info("AsynchronousCloseException");
            return;
          } catch (InterruptedException e) {
            logger.info("InterruptedException");
            return;
          }  catch (IOException e) {
            logger.log(Level.SEVERE, "receive exception", e);
          }

        }
      });

      for (var line : lines){
        var sendBuffer = UTF8.encode(line);
        dc.send(sendBuffer, server);
        logger.info("The send message was " + line);
        var msg = queue.poll(timeout, TimeUnit.MILLISECONDS);
        while (msg == null){
          sendBuffer.position(0);
          logger.info("The message is lost, restart send the message " + line);
          dc.send(sendBuffer, server);
          msg = queue.poll(timeout, TimeUnit.MILLISECONDS);
        }
        upperCaseLines.add(msg);
      }

      thread.interrupt();

    }


    // Write upperCaseLines to outFilename in UTF-8
    Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
  }
}