package fr.upem.net.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ServerLongSum {

  private static final Logger logger = Logger.getLogger(ServerLongSum.class.getName());
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final int BUFFER_SIZE = 1024;

  private final DatagramChannel dc;
  private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

  public ServerLongSum(int port) throws IOException {
    dc = DatagramChannel.open();
    dc.bind(new InetSocketAddress(port));
    logger.info("ServerBetterUpperCaseUDP started on port " + port);
  }

  public void serve() throws IOException {
    try {
      var map = new HashMap<PackageId, AnswersLog>();

      while (!Thread.interrupted()) {

        buffer.clear();
        var serveur = (InetSocketAddress) dc.receive(buffer);
        logger.info("receive the message");
        buffer.flip();

        if (buffer.remaining() != Byte.BYTES + 4 * Long.BYTES){
          logger.info("Receive package is not conform about the protocol");
          continue;
        }

        var opCode = buffer.get();
        var sessionID = buffer.getLong();
        var idPosOper = buffer.getLong();
        var totalOper = buffer.getLong();
        var opValue = buffer.getLong();

        if (opCode != 1){
          logger.info("The opCode is not 1, so the package is not conform about the protocol");
          continue;
        }

        var packageId = new PackageId(serveur, sessionID);

        var answers = map.computeIfAbsent(packageId, k -> new AnswersLog(totalOper));
        answers.update(idPosOper, opValue);

        buffer.clear();

        buffer.put((byte) 2);
        buffer.putLong(packageId.sessionID());
        buffer.putLong(idPosOper);
        buffer.flip();
        dc.send(buffer, packageId.ip());
        logger.info("send ACK");

        if(answers.isComplete()){
          buffer.clear();
          buffer.put((byte) 3);
          buffer.putLong(packageId.sessionID());
          buffer.putLong(answers.sum());
          buffer.flip();
          dc.send(buffer, packageId.ip());
          logger.info("send RES");
        }
      }
    } finally {
      dc.close();
    }
  }

  public static void usage() {
    System.out.println("Usage : ServerIdUpperCaseUDP port");
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      usage();
      return;
    }

    var port = Integer.parseInt(args[0]);

    if (!(port >= 1024) & port <= 65535) {
      logger.severe("The port number must be between 1024 and 65535");
      return;
    }

    try {
      new ServerLongSum(port).serve();
    } catch (BindException e) {
      logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
      return;
    }
  }

  private static class AnswersLog {

    private final Long[] totalOp;
    private int size = 0;

    private final Object lock = new Object();

    public AnswersLog(long nb) {
      totalOp = new Long[(int)nb];
    }

    public void update(long idPosOper, long opValue){
      synchronized (lock){
        int index = (int) idPosOper;
        if (totalOp[index] == null){
          totalOp[index] = opValue;
          size++;
        }
      }
    }

    public boolean isComplete(){
      synchronized (lock){
        return size == totalOp.length;
      }
    }

    public long sum(){
      synchronized (lock){
        return Arrays.stream(totalOp).reduce(0L, Long::sum);
      }
    }

  }

  private record PackageId(InetSocketAddress ip, long sessionID){}

}