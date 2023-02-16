package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;

public class HTTPReader {

  private final Charset ASCII_CHARSET = Charset.forName("ASCII");
  private final SocketChannel sc;
  private final ByteBuffer buffer;

  public HTTPReader(SocketChannel sc, ByteBuffer buffer) {
    this.sc = sc;
    this.buffer = buffer;
  }

  /**
   * @return The ASCII string terminated by CRLF without the CRLF
   *         <p>
   *         The method assume that buffer is in write mode and leaves it in
   *         write mode The method process the data from the buffer and if necessary
   *         will read more data from the socket.
   * @throws IOException HTTPException if the connection is closed before a line
   *                     could be read
   */
  public String readLineCRLF() throws IOException {
    var sb = new StringBuffer();
    while (true){
      buffer.flip();
      byte first;
      byte second;

      while (buffer.hasRemaining()){
        first = buffer.get();
        if (first == '\r' && buffer.hasRemaining() ){
          second = buffer.get();
          if (second == '\n'){
            buffer.compact();
            return sb.toString();
          }
          sb.append((char) first);
          sb.append((char) second);
        } else {
          sb.append((char) first);
        }
      }
      buffer.clear();
      if (sc.read(buffer) == -1) {
        throw new HTTPException("Connection closed");
      }
    }

  }

  /**
   * @return The HTTPHeader object corresponding to the header read
   * @throws IOException HTTPException if the connection is closed before a header
   *                     could be read or if the header is ill-formed
   */
  public HTTPHeader readHeader() throws IOException {
    var response = readLineCRLF();
    var fields = new HashMap<String, String>();
    String headerLine;
    while (true) {
      headerLine = readLineCRLF();
      if (headerLine.isEmpty()) {
        break;
      }
      var header = headerLine.split(":", 2);
      fields.merge(header[0].toLowerCase(), header[1], (a, b) -> a + "; " + b);
    }
    return HTTPHeader.create(response, fields);
  }

  /**
   * @param size
   * @return a ByteBuffer in write mode containing size bytes read on the socket
   *         <p>
   *         The method assume that buffer is in write mode and leaves it in
   *         write mode The method process the data from the buffer and if necessary
   *         will read more data from the socket.
   * @throws IOException HTTPException is the connection is closed before all
   *                     bytes could be read
   */
  public ByteBuffer readBytes(int size) throws IOException {
    var newBuffer = ByteBuffer.allocate(size);

    while (newBuffer.hasRemaining()){
      buffer.flip();
      while (buffer.hasRemaining()){
        var b = buffer.get();
        newBuffer.put(b);
        if (!newBuffer.hasRemaining()){
          buffer.compact();
          return newBuffer;
        }
      }
      buffer.clear();
      if (sc.read(buffer) == -1){
        break;
      }
    }
    buffer.compact();
    return newBuffer;
  }

  /**
   * @return a ByteBuffer in write-mode containing a content read in chunks mode
   * @throws IOException HTTPException if the connection is closed before the end
   *                     of the chunks if chunks are ill-formed
   */

  public ByteBuffer readChunks() throws IOException {
    // TODO
    var size = -1;
    ByteBuffer chunksBuffer = ByteBuffer.allocate(0);
    while (size != 0){
      var line = readLineCRLF();
      size = Integer.parseInt(line, 16);
      var newBuffer = readBytes(size);
      readBytes(2);
      newBuffer.flip();
      chunksBuffer.flip();
      var tmp = ByteBuffer.allocate(chunksBuffer.remaining() + newBuffer.remaining());
      tmp.put(chunksBuffer);
      tmp.put(newBuffer);
      chunksBuffer = tmp;
      newBuffer.flip();
    }
    return chunksBuffer;
  }

  public static void main(String[] args) throws IOException {
    var charsetASCII = Charset.forName("ASCII");
    var request = "GET / HTTP/1.1\r\n" + "Host: www.w3.org\r\n" + "\r\n";
    var sc = SocketChannel.open();
    sc.connect(new InetSocketAddress("www.w3.org", 80));
    sc.write(charsetASCII.encode(request));
    var buffer = ByteBuffer.allocate(50);
    var reader = new HTTPReader(sc, buffer);
    System.out.println(reader.readLineCRLF());
    System.out.println(reader.readLineCRLF());
    System.out.println(reader.readLineCRLF());
    sc.close();

    buffer = ByteBuffer.allocate(50);
    sc = SocketChannel.open();
    sc.connect(new InetSocketAddress("www.w3.org", 80));
    reader = new HTTPReader(sc, buffer);
    sc.write(charsetASCII.encode(request));
    System.out.println(reader.readHeader());
    sc.close();

    buffer = ByteBuffer.allocate(50);
    sc = SocketChannel.open();
    sc.connect(new InetSocketAddress("igm.univ-mlv.fr", 80));
    request = "GET /coursprogreseau/ HTTP/1.1\r\n" + "Host: igm.univ-mlv.fr\r\n" + "\r\n";
    reader = new HTTPReader(sc, buffer);
    sc.write(charsetASCII.encode(request));
    var header = reader.readHeader();
    System.out.println(header);
    var content = reader.readBytes(header.getContentLength());
    content.flip();
    System.out.println(header.getCharset().orElse(Charset.forName("UTF8")).decode(content));
    sc.close();

    buffer = ByteBuffer.allocate(50);
    request = "GET / HTTP/1.1\r\n" + "Host: www.u-pem.fr\r\n" + "\r\n";
    sc = SocketChannel.open();
    sc.connect(new InetSocketAddress("www.u-pem.fr", 80));
    reader = new HTTPReader(sc, buffer);
    sc.write(charsetASCII.encode(request));
    header = reader.readHeader();
    System.out.println(header);
    content = reader.readChunks();
    content.flip();
    System.out.println(header.getCharset().orElse(Charset.forName("UTF8")).decode(content));
    sc.close();
  }
}