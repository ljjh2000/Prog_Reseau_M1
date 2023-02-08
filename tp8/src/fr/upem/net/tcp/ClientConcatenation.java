package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

public class ClientConcatenation {

  public static final Logger logger = Logger.getLogger(ClientConcatenation.class.getName());

  public static final int BUFFER_SIZE = 1024;

  public static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;

  private static String requestConcatenation(SocketChannel sc, List<String> list) throws IOException {
    var buffer = ByteBuffer.allocate(BUFFER_SIZE);
    buffer.putInt(list.size());
    /*for (var l : list){
      buffer.putInt(l.length());
      buffer.put(UTF8_CHARSET.encode(l));
      buffer.flip();
      sc.write(buffer);
      buffer.clear();
    }

     */

    for (var i = 0; i < list.size();i++){
      while (writeFully(buffer, list.get(i))){
        buffer.flip();
        sc.write(buffer);
        buffer.clear();
      }
    }

    buffer.flip();
    sc.write(buffer);


    var size = ByteBuffer.allocate(Integer.BYTES);
    if (!readFully(sc, size)){
      return null;
    }
    size.flip();
    var msg = ByteBuffer.allocate(size.getInt());
    if (!readFully(sc, msg)){
      return null;
    }
    msg.flip();
    return UTF8_CHARSET.decode(msg).toString();
  }

  static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()){
      if (sc.read(buffer) == -1){
        return false;
      }
    }
    return true;
  }

  static boolean writeFully(ByteBuffer buffer, String chaine) throws IOException {
    if (buffer.remaining() < Integer.BYTES + chaine.length()){
      return true;
    }
    buffer.putInt(chaine.length());
    buffer.put(UTF8_CHARSET.encode(chaine));
    return false;
  }

  public static void main(String[] args) throws IOException {
    var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
    try (var sc = SocketChannel.open(server); var scanner = new Scanner(System.in)) {
      List<String> list = new ArrayList<>();
      while (scanner.hasNextLine()) {
        var line = scanner.nextLine();
        if (line.equals("")){
          var answer = requestConcatenation(sc, list);
          if (answer == null) {
            logger.warning("Connection with server lost.");
            return;
          }
          else {
            System.out.println(String.join(",", list));
          }
          list = new ArrayList<>();
        } else {
          list.add(line);
        }
      }
      logger.info("Everything seems ok");
    }
  }
}