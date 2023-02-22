package fr.upem.net.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class FixedPrestartedLongSumServer {

  private static final Logger logger = Logger.getLogger(OnDemandConcurrentLongSumServer.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final ServerSocketChannel serverSocketChannel;

  private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

  private final int maxClient;

  public FixedPrestartedLongSumServer(int port, int maxClient) throws IOException {
    serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.bind(new InetSocketAddress(port));
    logger.info(this.getClass().getName() + " starts on port " + port);
    this.maxClient = maxClient;
  }

  /**
   * Iterative server main loop
   *
   * @throws IOException
   */

  public void launch() {
    logger.info("Server started");
    IntStream.range(0, maxClient).forEach(i ->{
      Thread.ofPlatform().start(() ->{
        while (!Thread.interrupted()) {
          try {
            SocketChannel client = serverSocketChannel.accept();
            try {
              logger.info("Connection accepted from " + client.getRemoteAddress());
              serve(client);
            } catch (IOException ioe) {
              logger.log(Level.WARNING, "Connection terminated with client by IOException", ioe.getCause());
            } finally {
              silentlyClose(client);
            }
          } catch (IOException ioe) {
            logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
            return;
          }
        }
      });

    });
  }

  /**
   * Treat the connection sc applying the protocol. All IOException are thrown
   *
   * @param sc
   * @throws IOException
   */
  private void serve(SocketChannel sc) throws IOException {

    while (true) {
      buffer.clear();
      buffer.limit(Integer.BYTES);
      if (!readFully(sc, buffer)){
        return;
      }
      buffer.flip();
      var nb = buffer.getInt();
      if (nb < 0){
        return;
      }
      buffer.clear();
      buffer.limit(nb * Long.BYTES);
      if (!readFully(sc, buffer)){
        return;
      }
      buffer.flip();
      long sum = 0L;
      while (buffer.hasRemaining()){
        sum += buffer.getLong();
      }

      logger.info("send " + sum);

      buffer.clear();
      buffer.putLong(sum);
      buffer.flip();

      sc.write(buffer);
    }
  }

  /**
   * Close a SocketChannel while ignoring IOExecption
   *
   * @param sc
   */

  private void silentlyClose(Closeable sc) {
    if (sc != null) {
      try {
        sc.close();
      } catch (IOException e) {
        // Do nothing
      }
    }
  }

  static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      if (sc.read(buffer) == -1) {
        logger.info("Input stream closed");
        return false;
      }
    }
    return true;
  }

  public static void main(String[] args) throws NumberFormatException, IOException {
    var server = new FixedPrestartedLongSumServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
    server.launch();
  }
}