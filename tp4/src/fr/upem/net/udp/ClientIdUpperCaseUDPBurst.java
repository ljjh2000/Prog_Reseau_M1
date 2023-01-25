package fr.upem.net.udp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPBurst {

  private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final int BUFFER_SIZE = 1024;
  private final List<String> lines;
  private final int nbLines;
  private final String[] upperCaseLines; //
  private final int timeout;
  private final String outFilename;
  private final InetSocketAddress serverAddress;
  private final DatagramChannel dc;
  private final AnswersLog answersLog;         // Thread-safe structure keeping track of missing responses

  public static void usage() {
    System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
  }

  public ClientIdUpperCaseUDPBurst(List<String> lines,int timeout,InetSocketAddress serverAddress,String outFilename) throws IOException {
    this.lines = lines;
    this.nbLines = lines.size();
    this.timeout = timeout;
    this.outFilename = outFilename;
    this.serverAddress = serverAddress;
    this.dc = DatagramChannel.open();
    dc.bind(null);
    this.upperCaseLines = new String[nbLines];
    this.answersLog = new AnswersLog(nbLines); // TODO
  }

  private void senderThreadRun() {

    // TODO : body of the sender thread

    var sendBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    while (!Thread.interrupted()){
      for(var i = 0; i < lines.size(); i++){

        if (!answersLog.isPresent(i)){
          sendBuffer.clear();
          var msgBuffer = UTF8.encode(lines.get(i));
          if (msgBuffer.remaining() + Integer.BYTES > BUFFER_SIZE){
            logger.info("The message is " + lines.get(i) + " so big");
            continue;
          }
          sendBuffer.putLong(i);
          sendBuffer.put(msgBuffer);
          sendBuffer.flip();
          try {
            Thread.sleep(timeout);
            dc.send(sendBuffer, serverAddress);
          } catch (InterruptedException e) {
            logger.info("InterruptedException");
            return;
          } catch (IOException e) {
            logger.severe("IOException on thread senderThreadRun " + e);
            return;
          }
          logger.info("Send the message " + lines.get(i));
        }

      }

    }


  }

  public void launch() throws IOException {
    Thread senderThread = Thread.ofPlatform().start(this::senderThreadRun);

    // TODO : body of the receiver thread

    var receiveBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    while (!answersLog.isComplete()){
      try {
        receiveBuffer.clear();
        dc.receive(receiveBuffer);
        receiveBuffer.flip();
        if (receiveBuffer.remaining() != 0){
          var id = receiveBuffer.getLong();
          var msg = UTF8.decode(receiveBuffer).toString();
          answersLog.set((int) id);
          upperCaseLines[(int) id] = msg;
        }


      } catch (AsynchronousCloseException e) {
        return;
      } catch (IOException e) {
        logger.severe("IOException in listenerThreadRun methode " + e);
        return;
      }
    }


    senderThread.interrupt();


    Files.write(Paths.get(outFilename),Arrays.asList(upperCaseLines), UTF8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);

  }

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length !=5) {
      usage();
      return;
    }

    String inFilename = args[0];
    String outFilename = args[1];
    int timeout = Integer.valueOf(args[2]);
    String host=args[3];
    int port = Integer.valueOf(args[4]);
    InetSocketAddress serverAddress = new InetSocketAddress(host,port);

    //Read all lines of inFilename opened in UTF-8
    List<String> lines= Files.readAllLines(Paths.get(inFilename),UTF8);
    //Create client with the parameters and launch it
    ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines,timeout,serverAddress,outFilename);
    client.launch();

  }

  private static class AnswersLog {
    private final BitSet bitSet;
    private final int capacity;

    private final Object lock = new Object();

    public AnswersLog(int nb) {
      this.bitSet = new BitSet(nb);
      this.capacity = nb;
    }

    public boolean isPresent(int index){
      synchronized (lock){
        return bitSet.get(index);
      }
    }

    public boolean isComplete(){
      synchronized (lock) {
        return bitSet.cardinality() == capacity;
      }
    }

    public void set(int index){
      synchronized (lock){
        bitSet.set(index);
      }
    }
  }
}

