package org.arl.jajub;

import java.util.*;
import java.io.*;

/**
 * Java-Julia bridge.
 *
 * This class is not thread-safe.
 */
public class JuliaBridge {

  protected final static int CR = 10;
  protected final static long TIMEOUT = 10000;
  protected final static long POLL_DELAY = 10;

  protected final String[] JULIA_EXEC = {
    "bin/julia",
    "bin/julia.exe"
  };

  protected final String[] JULIA_ARGS = {
    "-iq",
    "--startup-file=no",
    "-e",
    "using InteractiveUtils; versioninfo()"
  };

  protected ProcessBuilder jbuilder;
  protected Process julia = null;
  protected InputStream inp = null;
  protected OutputStream out = null;
  protected String ver = null;

  ////// public API

  public JuliaBridge() {
    List<String> j = new ArrayList<String>();
    j.add(getJuliaExec());
    j.addAll(Arrays.asList(JULIA_ARGS));
    jbuilder = new ProcessBuilder(j);
  }

  public JuliaBridge(String... jargs) {
    List<String> j = new ArrayList<String>();
    j.add(getJuliaExec());
    j.addAll(Arrays.asList(jargs));
    j.addAll(Arrays.asList(JULIA_ARGS));
    jbuilder = new ProcessBuilder(j);
  }

  public JuliaBridge(String jcmd, String... jargs) {
    List<String> j = new ArrayList<String>();
    j.add(jcmd);
    j.addAll(Arrays.asList(jargs));
    j.addAll(Arrays.asList(JULIA_ARGS));
    jbuilder = new ProcessBuilder(j);
  }

  public boolean isOpen() {
    return julia != null;
  }

  public void open(long timeout) throws IOException, InterruptedException {
    if (isOpen()) return;
    jbuilder.redirectErrorStream(true);
    julia = jbuilder.start();
    inp = julia.getInputStream();
    out = julia.getOutputStream();
    while (true) {
      String s = readline(timeout);
      if (s.length() == 0) {
        close();
        throw new IOException("Bad Julia process");
      }
      if (s.startsWith("Julia Version ")) {
        ver = s;
        flush();
        return;
      }
    }
  }

  public void open() throws IOException {
    try {
      open(TIMEOUT);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  public void close() {
    if (!isOpen()) return;
    julia.destroy();
    julia = null;
    inp = null;
    out = null;
    ver = null;
  }

  public String getJuliaVersion() {
    if (!isOpen()) throw new IllegalStateException("JuliaBridge not open");
    return ver;
  }

  ////// private stuff

  protected String getJuliaExec() {
    String jhome = System.getenv("JULIA_HOME");
    if (jhome != null) {
      try {
        for (String name: JULIA_EXEC) {
          File f = new File(jhome, name);
          if (f.canExecute()) return f.getCanonicalPath();
        }
      } catch (IOException ex) {
        // do nothing
      }
    }
    return "julia";
  }

  protected void write(String s) throws IOException {
    out.write(s.getBytes());
    out.write(CR);
    out.flush();
  }

  protected void write(byte[] b) throws IOException {
    out.write(b);
    out.write(CR);
    out.flush();
  }

  protected void flush() throws IOException {
    while (inp.available() > 0)
      inp.read();
  }

  protected int read(byte[] buf, long timeout) throws IOException, InterruptedException {
    long t = System.currentTimeMillis() + timeout;
    int ofs = 0;
    do {
      int n = inp.available();
      while (n > 0 && !Thread.interrupted()) {
        int m = buf.length - ofs;
        ofs += inp.read(buf, ofs, n>m?m:n);
        if (ofs == buf.length) return ofs;
        n = inp.available();
      }
      Thread.sleep(POLL_DELAY);
    } while (System.currentTimeMillis() < t);
    return ofs;
  }

  protected String readline(long timeout) throws IOException, InterruptedException {
    long t = System.currentTimeMillis() + timeout;
    StringBuffer sb = new StringBuffer();
    do {
      while (inp.available() > 0 && !Thread.interrupted()) {
        int b = inp.read();
        if (b == CR) return sb.toString();
        sb.append((char)b);
      }
      Thread.sleep(POLL_DELAY);
    } while (System.currentTimeMillis() < t);
    return sb.toString();
  }

}
