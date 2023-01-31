package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoMultiPort {
  private static final Logger logger = Logger.getLogger(ServerEchoMultiPort.class.getName());

  private static final int BUFFER_SIZE = 1024;

  private final Selector selector;


  private static final class Context{
    InetSocketAddress inetSocketAddress;

    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
  }


  public ServerEchoMultiPort(int fistPort, int secondPort) throws IOException {
    selector = Selector.open();
    for (var port = fistPort; port < secondPort; port++){
      var dc = DatagramChannel.open();
      dc.bind(new InetSocketAddress(port));
      dc.configureBlocking(false);
      dc.register(selector, SelectionKey.OP_READ, new Context());
    }
  }

  public void serve() throws IOException {
    logger.info("ServerEchoMultiPort started");
    while (!Thread.interrupted()) {
      try {
        selector.select(this::treatKey);
      } catch (UncheckedIOException tunneled) {
        throw tunneled.getCause();
      }
    }
  }

  private void treatKey(SelectionKey key) {
    try {
      if (key.isValid() && key.isWritable()) {
        doWrite(key);
      }
      if (key.isValid() && key.isReadable()) {
        doRead(key);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

  }

  private void doRead(SelectionKey key) throws IOException {
    var context = (Context) key.attachment();
    var buffer = context.buffer;
    var dc = (DatagramChannel) key.channel();
    buffer.clear();
    context.inetSocketAddress = (InetSocketAddress) dc.receive(buffer);
    if (context.inetSocketAddress == null){
      logger.warning("On ne peut pas lire");
      return;
    }
    buffer.flip();
    key.interestOps(SelectionKey.OP_WRITE);
  }

  private void doWrite(SelectionKey key) throws IOException {
    var context = (Context) key.attachment();
    var buffer = context.buffer;
    var dc = (DatagramChannel) key.channel();
    dc.send(buffer, context.inetSocketAddress);
    if (buffer.hasRemaining()){
      logger.warning("On n'a pas envoyer de packet");
      return;
    }
    key.interestOps(SelectionKey.OP_READ);
  }

  public static void usage() {
    System.out.println("Usage : ServerEcho port");
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      usage();
      return;
    }
    new ServerEchoMultiPort(Integer.parseInt(args[0]), Integer.parseInt(args[1])).serve();
  }
}
