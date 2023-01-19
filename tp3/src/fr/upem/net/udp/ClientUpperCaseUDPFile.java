package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import static java.nio.file.StandardOpenOption.*;

public class ClientUpperCaseUDPFile {
  private final static Charset UTF8 = StandardCharsets.UTF_8;
  private final static int BUFFER_SIZE = 1024;

  private static void usage() {
    System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length != 5) {
      usage();
      return;
    }

    var inFilename = args[0];
    var outFilename = args[1];
    var timeout = Integer.parseInt(args[2]);
    var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

    // Read all lines of inFilename opened in UTF-8
    var lines = Files.readAllLines(Path.of(inFilename), UTF8);
    var upperCaseLines = new ArrayList<String>();

    // TODO

    // Write upperCaseLines to outFilename in UTF-8
    Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
  }
}