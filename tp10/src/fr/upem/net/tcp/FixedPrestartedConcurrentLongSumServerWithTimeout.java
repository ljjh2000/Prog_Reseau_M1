package fr.upem.net.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class FixedPrestartedConcurrentLongSumServerWithTimeout {

  private static final Logger logger = Logger.getLogger(OnDemandConcurrentLongSumServer.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final ServerSocketChannel serverSocketChannel;

  private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

  private final int maxClient;

  private final int timeout;

  private final ThreadData[] threadData;

  public FixedPrestartedConcurrentLongSumServerWithTimeout(int port, int maxClient, int timeout) throws IOException {
    serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.bind(new InetSocketAddress(port));
    logger.info(this.getClass().getName() + " starts on port " + port);
    this.maxClient = maxClient;
    this.timeout = timeout;
    threadData = new ThreadData[maxClient];
    for (var i = 0; i < maxClient; i++){
      threadData[i] = new ThreadData();
    }
  }

  /**
   * Iterative server main loop
   *
   * @throws IOException
   */

  public void launch() {
    logger.info("Server started");

    var checkActive = Thread.ofPlatform().start(() ->{
      while (!Thread.interrupted()){
        Arrays.stream(threadData).forEach(t -> {
          try {
            t.closeIfInactive(timeout);
            Thread.sleep(timeout / 4);
          } catch (InterruptedException e) {
            logger.log(Level.INFO, "Thread checkActive is interrupted", e.getCause());
          }
        });
      }
    });


    var threadClients = new ArrayList<Thread>();

    IntStream.range(0, maxClient).forEach(i ->{
      var threadClient = Thread.ofPlatform().start(() ->{
        while (!Thread.interrupted()) {
          try {
            SocketChannel client = serverSocketChannel.accept();
            threadData[i].setSocketChannel(client);
            try {
              logger.info("Connection accepted from " + client.getRemoteAddress());
              serve(threadData[i]);
            } catch (ClosedByInterruptException ioe) {
              logger.log(Level.INFO, "interrupt while waiting for message", ioe.getCause());
            } catch (AsynchronousCloseException ioe) {
              logger.log(Level.INFO, "socketChannel is closed", ioe.getCause());
            } catch (IOException ioe) {
              logger.log(Level.INFO, "Connection terminated with client by IOException", ioe.getCause());
            } finally {
              threadData[i].close();
            }
          } catch (IOException ioe) {
            logger.log(Level.WARNING, "Serveur is interrupt", ioe.getCause());
            return;
          }
        }
      });
      threadClients.add(threadClient);
    });


    Thread.ofPlatform().daemon().start(() ->{
      try (var scanner = new Scanner(System.in)) {
        while (!Thread.interrupted()){
          while (scanner.hasNextLine()) {

            var l = scanner.nextLine();

            switch (l.toUpperCase()){
              case "INFO" -> {
                logger.info("Il y a " + Arrays.stream(threadData).filter(t-> t.getClient() != null).count() + " client connecté");
              }
              case "SHUTDOWN" -> {
                Arrays.stream(threadData).forEach(ThreadData::close);
              }
              case "SHUTDOWNNOW" -> {
                Arrays.stream(threadData).forEach(ThreadData::close);
                checkActive.interrupt();
                threadClients.forEach(Thread::interrupt);
              }
              default -> {
                logger.info("Not commande for " + l);
              }
            }

          }
        }
      }
    });
  }

  /**
   * Treat the connection sc applying the protocol. All IOException are thrown
   *
   * @param threadData
   * @throws IOException
   */
  private void serve(ThreadData threadData) throws IOException {

    var sc = threadData.client;
    if (sc == null){
      return;
    }
    while (true) {
      buffer.clear();
      buffer.limit(Integer.BYTES);
      threadData.tick();
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
      threadData.tick();
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

  boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      if (sc.read(buffer) == -1) {
        logger.info("Input stream closed");
        return false;
      }
    }
    return true;
  }

  public static void main(String[] args) throws NumberFormatException, IOException {
    var server = new FixedPrestartedConcurrentLongSumServerWithTimeout(Integer.parseInt(args[0]), 10, Integer.parseInt(args[1]));
    server.launch();
  }


  class ThreadData {

    private SocketChannel client = null;

    private long lastTime;

    private final Object lock = new Object();

    void setSocketChannel(SocketChannel client){
      synchronized (lock){
        if (this.client != null){
          logger.info("Thread has a active client");
          return;
        }
        this.client = client;
        lastTime = System.currentTimeMillis();
      }
    }

    void tick() {
      synchronized (lock){
        lastTime = System.currentTimeMillis();
      }
    }

    void closeIfInactive(int timeout) {
      synchronized (lock){
        if (client == null){
          return;
        }
        if (System.currentTimeMillis() - lastTime > timeout){
          close();
        }
      }
    }

    public SocketChannel getClient(){
      synchronized (lock){
        return client;
      }
    }

    void close() {
      synchronized (lock){
        if (client != null) {
          try {
            client.close();
          } catch (IOException e) {
            // Do nothing
          }
        }
      }
    }
  }
}