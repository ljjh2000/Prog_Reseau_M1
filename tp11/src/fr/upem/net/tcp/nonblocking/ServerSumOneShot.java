package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.logging.Logger;

public class ServerSumOneShot {

  private static final int BUFFER_SIZE = 2 * Integer.BYTES;
  private static final Logger logger = Logger.getLogger(ServerSumOneShot.class.getName());

  private final ServerSocketChannel serverSocketChannel;
  private final Selector selector;

  private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

  private final ByteBuffer writeBuffer = ByteBuffer.allocate(Integer.BYTES);

  private int sum;

  public ServerSumOneShot(int port) throws IOException {
    serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.bind(new InetSocketAddress(port));
    selector = Selector.open();
  }

  public void launch() throws IOException {
    serverSocketChannel.configureBlocking(false);
    serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    while (!Thread.interrupted()) {
      Helpers.printKeys(selector); // for debug
      System.out.println("Starting select");
      selector.select(this::treatKey);
      System.out.println("Select finished");
    }
  }

  private void treatKey(SelectionKey key) {
    Helpers.printSelectedKey(key); // for debug
    if (key.isValid() && key.isAcceptable()) {
      try {
        doAccept(key);
      } catch (IOException e){
        logger.warning("Il est impossible d'accepter une connection");
      }
    }
    if (key.isValid() && key.isWritable()) {
      try {
        doWrite(key);
      } catch (IOException e){
        // fait rien
      }
    }
    if (key.isValid() && key.isReadable()) {
      try {
        doRead(key);
      } catch (IOException e){
        // fait rien
      }
    }
  }

  private void doAccept(SelectionKey key) throws IOException {

    var sc = serverSocketChannel.accept();
    if (sc == null) {
      // toujours vérifier si la tentative a échoué !
      // auquel cas il faut attendre d'être à nouveau notifié
    } else {
      sc.configureBlocking(false);
      key = sc.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFER_SIZE));
    }
  }

  private void doRead(SelectionKey key) throws IOException {
    var buffer = (ByteBuffer) key.attachment();
    var sc = (SocketChannel) key.channel();

    if (sc.read(buffer) == -1){
      sc.close();
      return;
    }

    if (buffer.remaining() != 0){
      return;
    }
    buffer.flip();
    key.interestOps(SelectionKey.OP_WRITE);
    sum = buffer.getInt() + buffer.getInt();
  }

  private void doWrite(SelectionKey key) throws IOException {
    var sc = (SocketChannel) key.channel();
    var buffer = (ByteBuffer) key.attachment();
    buffer.clear();
    buffer.putInt(sum);
    buffer.flip();
    sc.write(buffer);
    if (buffer.hasRemaining()){
      return;
    }
    sc.close();
  }

  private void silentlyClose(SelectionKey key) {
    var sc = (Channel) key.channel();
    try {
      sc.close();
    } catch (IOException e) {
      // ignore exception
    }
  }

  public static void main(String[] args) throws NumberFormatException, IOException {
    if (args.length != 1) {
      usage();
      return;
    }
    new ServerSumOneShot(Integer.parseInt(args[0])).launch();
  }

  private static void usage() {
    System.out.println("Usage : ServerSumOneShot port");
  }
}