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

public class BoundedOnDemandConcurrentLongSumServer {

  private static final Logger logger = Logger.getLogger(BoundedOnDemandConcurrentLongSumServer.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final ServerSocketChannel serverSocketChannel;

  private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
  private final Semaphore semaphore;

  public BoundedOnDemandConcurrentLongSumServer(int port, int nbThread) throws IOException {
    if (nbThread < 1){
      throw new IllegalArgumentException("nbThread < 1 is not accepted");
    }
    serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.bind(new InetSocketAddress(port));
    logger.info(this.getClass().getName() + " starts on port " + port);
    semaphore = new Semaphore(nbThread);
  }

  /**
   * Iterative server main loop
   *
   * @throws IOException
   */

  public void launch() throws IOException, InterruptedException {
    logger.info("Server started");
    while (!Thread.interrupted()) {
      SocketChannel client = serverSocketChannel.accept();
      semaphore.acquire();
      Thread.ofPlatform().start(() -> {
        try {
          logger.info("Connection accepted from " + client.getRemoteAddress());

          serve(client);
        } catch (IOException ioe) {
          logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
          return;
        } finally {
          silentlyClose(client);
        }
      });
    }
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
        semaphore.release();
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

  public static void main(String[] args) throws NumberFormatException, IOException, InterruptedException {
    var server = new BoundedOnDemandConcurrentLongSumServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
    server.launch();
  }
}