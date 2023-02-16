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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientIdUpperCaseUDPOneByOne {

  private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final int BUFFER_SIZE = 1024;

  private record Response(long id, String message) {
  };

  private final String inFilename;
  private final String outFilename;
  private final long timeout;
  private final InetSocketAddress server;
  private final DatagramChannel dc;
  private final SynchronousQueue<Response> queue = new SynchronousQueue<>();

  public static void usage() {
    System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
  }

  public ClientIdUpperCaseUDPOneByOne(String inFilename, String outFilename, long timeout, InetSocketAddress server)
          throws IOException {
    this.inFilename = Objects.requireNonNull(inFilename);
    this.outFilename = Objects.requireNonNull(outFilename);
    this.timeout = timeout;
    this.server = server;
    this.dc = DatagramChannel.open();
    dc.bind(null);
  }

  private void listenerThreadRun() {
    var receiveBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    while (!Thread.interrupted()){

      try {
        receiveBuffer.clear();
        dc.receive(receiveBuffer);
        receiveBuffer.flip();
        var id = receiveBuffer.getLong();
        var msg = UTF8.decode(receiveBuffer).toString();

        var response = new Response(id, msg);
        queue.put(response);
      } catch (InterruptedException | AsynchronousCloseException e) {
        return;
      } catch (IOException e) {
        logger.severe("IOException in listenerThreadRun methode " + e);
        return;
      }
    }
  }

  public void launch() throws IOException, InterruptedException {
    try {

      var listenerThread = Thread.ofPlatform().start(this::listenerThreadRun);

      // Read all lines of inFilename opened in UTF-8
      var lines = Files.readAllLines(Path.of(inFilename), UTF8);

      var upperCaseLines = new ArrayList<String>();

      var sendBuffer = ByteBuffer.allocate(BUFFER_SIZE);
      for(var i = 0; i < lines.size(); i++){
        sendBuffer.clear();
        var msgBuffer = UTF8.encode(lines.get(i));
        if (msgBuffer.remaining() + Integer.BYTES > BUFFER_SIZE){
          logger.info("The message is " + lines.get(i) + " so big");
          continue;
        }
        sendBuffer.putLong(i);
        sendBuffer.put(msgBuffer);
        sendBuffer.flip();
        dc.send(sendBuffer, server);
        var lastSendTime = System.currentTimeMillis();
        logger.info("Send the message " + lines.get(i));

        var response = queue.poll(timeout - System.currentTimeMillis() - lastSendTime, TimeUnit.MILLISECONDS);
        while (response == null || response.id() != i){
          if (System.currentTimeMillis() - lastSendTime >= timeout){
            sendBuffer.flip();
            dc.send(sendBuffer, server);
            lastSendTime = System.currentTimeMillis();
            logger.info("Resend the message " + lines.get(i));
          }
          response = queue.poll(timeout - System.currentTimeMillis() - lastSendTime, TimeUnit.MILLISECONDS);
        }

        logger.info("response is " + response.message());

        upperCaseLines.add(response.message());
      }

      listenerThread.interrupt();
      Files.write(Paths.get(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
    } finally {
      dc.close();
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length != 5) {
      usage();
      return;
    }

    var inFilename = args[0];
    var outFilename = args[1];
    var timeout = Long.parseLong(args[2]);
    var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

    // Create client with the parameters and launch it
    new ClientIdUpperCaseUDPOneByOne(inFilename, outFilename, timeout, server).launch();
  }
}