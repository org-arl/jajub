package org.arl.jajub;

import java.util.*;
import java.io.*;
import java.nio.*;

/**
 * Java-Julia bridge.
 *
 * This class is not thread-safe.
 */
public class JuliaBridge {

  protected final static int CR = 10;
  protected final static long TIMEOUT = 10000;
  protected final static long POLL_DELAY = 10;
  protected final static String TERMINATOR = "\"__##@@##__\"";

  protected final String[] JULIA_EXEC = {
    "bin/julia",
    "bin/julia.exe"
  };

  protected final String[] JULIA_ARGS = {
    "-iq",
    "--startup-file=no",
    "-e",
    "using InteractiveUtils; versioninfo(); println("+TERMINATOR+");"
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
      if (s == null) {
        close();
        throw new IOException("Bad Julia process");
      }
      if (s.startsWith("Julia Version ")) ver = s;
      else if (TERMINATOR.contains(s)) break;
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
    openIfNecessary();
    return ver;
  }

  public List<String> exec(String cmd) {
    openIfNecessary();
    List<String> rsp = new ArrayList<String>();
    try {
      flush();
      write(cmd + "; " + TERMINATOR);
      while (true) {
        String s = readline(TIMEOUT);
        if (s.equals(TERMINATOR) || s == null) return rsp;
        rsp.add(s);
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (IOException ex) {
      throw new RuntimeException("JuliaBridge connection broken", ex);
    }
    return rsp;
  }

  public Object get(String varname) {
    List<String> rsp = exec("println(typeof("+varname+"))");
    if (rsp.size() < 1) throw new RuntimeException("Invalid response from Julia REPL");
    String type = rsp.get(0);
    if (type.equals("Nothing")) return null;
    if (type.equals("Missing")) return null;
    try {
      if (type.equals("String")) {
        rsp = exec("println(sizeof("+varname+"))");
        if (rsp.size() < 1) throw new RuntimeException("Invalid response from Julia REPL");
        int n = Integer.parseInt(rsp.get(0));
        write("write(stdout, "+varname+")");
        byte[] buf = new byte[n];
        read(buf, TIMEOUT);
        return new String(buf);
      }
      write("write(stdout, "+varname+")");
      if (type.equals("Int64")) return readNumber(8, false);
      if (type.equals("Int32")) return readNumber(4, false);
      if (type.equals("Int16")) return readNumber(2, false);
      if (type.equals("Int8")) return readNumber(1, false);
      if (type.equals("Float64")) return readNumber(8, true);
      if (type.equals("Float32")) return readNumber(4, true);
      throw new RuntimeException("Unsupported type: "+type);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (IOException ex) {
      throw new RuntimeException("JuliaBridge connection broken", ex);
    }
    return null;
  }

  public Object call(String func, Object... args) {
    StringBuilder sb = new StringBuilder();
    sb.append("_ans_ = ");
    sb.append(func);
    sb.append('(');
    sb.append(')');
    exec(sb.toString());
    return get("_ans_");
  }

  ////// private stuff

  protected void openIfNecessary() {
    if (isOpen()) return;
    try {
      open();
    } catch(IOException ex) {
      throw new RuntimeException("Unable to open JuliaBridge", ex);
    }
  }

  protected Object readNumber(int nbytes, boolean fp) throws IOException, InterruptedException {
    byte[] buf = new byte[nbytes];
    read(buf, TIMEOUT);
    if (nbytes == 1) return buf[0];
    ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.nativeOrder());
    if (fp) {
      switch (nbytes) {
        case 4: return bb.asFloatBuffer().get();
        case 8: return bb.asDoubleBuffer().get();
      }
    } else {
      switch (nbytes) {
        case 2: return bb.asShortBuffer().get();
        case 4: return bb.asIntBuffer().get();
        case 8: return bb.asLongBuffer().get();
      }
    }
    return null;
  }

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
    StringBuilder sb = new StringBuilder();
    do {
      while (inp.available() > 0 && !Thread.interrupted()) {
        int b = inp.read();
        if (b == CR) return sb.toString();
        sb.append((char)b);
      }
      Thread.sleep(POLL_DELAY);
    } while (System.currentTimeMillis() < t);
    if (sb.length() == 0) return null;
    return sb.toString();
  }

}
