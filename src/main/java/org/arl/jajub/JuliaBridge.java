package org.arl.jajub;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.util.logging.*;

/**
 * Java-Julia bridge.
 * <p>
 * This class allows Java applications to use Julia code to implement
 * some of the functionality. Julia is assumed to be installed and on
 * the system path. Alternatively an environment variable "JULIA_HOME"
 * may be set to aid location of executable (assumed to be "bin/julia"
 * under the JULIA_HOME folder). Julia is invoked as a separate process
 * and data is transferred to/fro over stdin/stdout between Java and
 * Julia.
 * <p>
 * This class is not thread-safe.
 */
public class JuliaBridge {

  private final static int CR = 10;
  private final static long TIMEOUT = 10000;
  private final static long POLL_DELAY = 10;
  private final static String TERMINATOR = "\"__##@@##__\"";

  private final String[] JULIA_EXEC = {
    "bin/julia",
    "bin/julia.exe"
  };

  private final String[] JULIA_ARGS = {
    "-iq",
    "--startup-file=no",
    "-e",
    "using InteractiveUtils; versioninfo();" +
    "__type__(::AbstractArray{T,N}) where T where N = Array{T,N};" +
    "__type__(a) = typeof(a);" +
    "Infinity = Inf;" +
    "println("+TERMINATOR+");"
  };

  protected Logger log = Logger.getLogger(getClass().getName());

  private ProcessBuilder jbuilder;
  private Process julia = null;
  private InputStream inp = null;
  private OutputStream out = null;
  private String ver = null;

  ////// public API

  /**
   * Creates a Java-Julia bridge with default settings.
   */
  public JuliaBridge() {
    List<String> j = new ArrayList<String>();
    j.add(getJuliaExec());
    j.addAll(Arrays.asList(JULIA_ARGS));
    jbuilder = new ProcessBuilder(j);
  }

  /**
   * Creates a Java-Julia bridge with custom Julia command
   * and arguments.
   *
   * @param jcmd custom Julia executable/command (null to use default).
   * @param jargs custom Julia command-line arguments.
   */
  public JuliaBridge(String jcmd, String... jargs) {
    List<String> j = new ArrayList<String>();
    j.add(jcmd == null ? getJuliaExec() : jcmd);
    j.addAll(Arrays.asList(jargs));
    j.addAll(Arrays.asList(JULIA_ARGS));
    jbuilder = new ProcessBuilder(j);
  }

  @Override
  public void finalize() {
    close();
  }

  /**
   * Checks if Julia process is already running.
   */
  public boolean isOpen() {
    return julia != null;
  }

  /**
   * Starts the Julia process.
   *
   * @param timeout timeout in milliseconds for process to start.
   */
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

  /**
   * Starts the Julia process.
   */
  public void open() throws IOException {
    try {
      open(TIMEOUT);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Stops a running Julia process.
   */
  public void close() {
    if (!isOpen()) return;
    julia.destroy();
    julia = null;
    inp = null;
    out = null;
    ver = null;
  }

  /**
   * Gets Julia version.
   */
  public String getJuliaVersion() {
    openIfNecessary();
    return ver;
  }

  /**
   * Executes Julia code and returns the output.
   *
   * @param jcode Julia code to run.
   * @param timeout timeout in milliseconds.
   * @return output (stdout + stderr).
   */
  public List<String> exec(String jcode, long timeout) {
    openIfNecessary();
    List<String> rsp = new ArrayList<String>();
    try {
      flush();
      write(jcode + "; " + TERMINATOR);
      while (true) {
        String s = readline(timeout);
        if (s == null || s.equals(TERMINATOR)) return rsp;
        rsp.add(s);
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (IOException ex) {
      throw new RuntimeException("JuliaBridge connection broken", ex);
    }
    return rsp;
  }

  /**
   * Executes Julia code and returns the output.
   *
   * @param jcode Julia code to run.
   * @param timeout timeout in milliseconds.
   * @return output (stdout + stderr).
   */
  public List<String> exec(JuliaExpr jcode, long timeout) {
    return exec(jcode.toString(), timeout);
  }

  /**
   * Executes Julia code and returns the output.
   *
   * @param istream input stream to read Julia code from.
   * @param timeout timeout in milliseconds.
   * @return output (stdout + stderr).
   */
  public List<String> exec(InputStream istream, long timeout) throws IOException {
    StringBuilder sb = new StringBuilder();
    BufferedReader reader = new BufferedReader(new InputStreamReader(istream));
    while (true) {
      String s = reader.readLine();
      if (s == null) break;
      sb.append(s);
      sb.append('\n');
    }
    return exec(sb.toString(), timeout);
  }

  /**
   * Executes Julia code and returns the output.
   *
   * @param jcode Julia code to run
   * @return output (stdout + stderr).
   */
  public List<String> exec(String jcode) {
    return exec(jcode, TIMEOUT);
  }

  /**
   * Executes Julia code and returns the output.
   *
   * @param jcode Julia code to run
   * @return output (stdout + stderr).
   */
  public List<String> exec(JuliaExpr jcode) {
    return exec(jcode.toString(), TIMEOUT);
  }

  /**
   * Executes Julia code and returns the output.
   *
   * @param istream input stream to read Julia code from.
   * @return output (stdout + stderr).
   */
  public List<String> exec(InputStream istream) throws IOException {
    return exec(istream, TIMEOUT);
  }

  /**
   * Sets a variable in the Julia environment.
   *
   * @param varname name of the variable.
   * @param value value to bind to the variable.
   */
  public void set(String varname, Object value) {
    try {
      String s = jexpr(value);
      if (s != null) exec(varname+" = "+s);
      else if (value instanceof LongArray) writeNumeric(varname, ((LongArray)value).data, ((LongArray)value).dims, ((LongArray)value).isComplex);
      else if (value instanceof IntegerArray) writeNumeric(varname, ((IntegerArray)value).data, ((IntegerArray)value).dims, ((IntegerArray)value).isComplex);
      else if (value instanceof ShortArray) writeNumeric(varname, ((ShortArray)value).data, ((ShortArray)value).dims, ((ShortArray)value).isComplex);
      else if (value instanceof ByteArray) writeNumeric(varname, ((ByteArray)value).data, ((ByteArray)value).dims, ((ByteArray)value).isComplex);
      else if (value instanceof DoubleArray) writeNumeric(varname, ((DoubleArray)value).data, ((DoubleArray)value).dims, ((DoubleArray)value).isComplex);
      else if (value instanceof FloatArray) writeNumeric(varname, ((FloatArray)value).data, ((FloatArray)value).dims, ((FloatArray)value).isComplex);
      else if (value instanceof long[]) writeNumeric(varname, (long[])value, new int[] { ((long[])value).length }, false);
      else if (value instanceof int[]) writeNumeric(varname, (int[])value, new int[] { ((int[])value).length }, false);
      else if (value instanceof short[]) writeNumeric(varname, (short[])value, new int[] { ((short[])value).length }, false);
      else if (value instanceof byte[]) writeNumeric(varname, (byte[])value, new int[] { ((byte[])value).length }, false);
      else if (value instanceof double[]) writeNumeric(varname, (double[])value, new int[] { ((double[])value).length }, false);
      else if (value instanceof float[]) writeNumeric(varname, (float[])value, new int[] { ((float[])value).length }, false);
      else throw new RuntimeException("Unsupported type: "+value.getClass().getName());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (IOException ex) {
      throw new RuntimeException("JuliaBridge connection broken", ex);
    }
  }

  /**
   * Gets a variable from the Julia environment.
   *
   * @param varname name of the variable.
   * @return value bound to the variable, null if unavailable.
   */
  public Object get(String varname) {
    List<String> rsp = exec("println(__type__("+varname+"))");
    if (rsp.size() < 1) throw new RuntimeException("Invalid response from Julia REPL");
    String type = rsp.get(0);
    if (type.contains("ERROR: UndefVarError")) return null;
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
      int[] dims = null;
      if (type.startsWith("Array{") && type.endsWith("}")) {
        int p = type.indexOf(',');
        if (p < 0) throw new RuntimeException("Invalid response from Julia REPL");
        int d = Integer.parseInt(type.substring(p+1, type.length()-1));
        dims = new int[d];
        type = type.substring(6, p);
        rsp = exec("println(size("+varname+"))");
        if (rsp.size() < 1) throw new RuntimeException("Invalid response from Julia REPL");
        String s = rsp.get(0);
        int ofs = 1;
        for (int i = 0; i < d; i++) {
          p = s.indexOf(',', ofs);
          if (p < 0) p = s.indexOf(')', ofs);
          if (p < 0) throw new RuntimeException("Invalid response from Julia REPL");
          dims[i] = Integer.parseInt(s.substring(ofs, p).trim());
          ofs = p+1;
        }
      }
      write("write(stdout, "+varname+")");
      if (type.equals("Int64")) return readNumeric(8, false, dims, false);
      if (type.equals("Int32")) return readNumeric(4, false, dims, false);
      if (type.equals("Int16")) return readNumeric(2, false, dims, false);
      if (type.equals("Int8")) return readNumeric(1, false, dims, false);
      if (type.equals("Float64")) return readNumeric(8, true, dims, false);
      if (type.equals("Float32")) return readNumeric(4, true, dims, false);
      if (type.equals("Complex{Int64}")) return readNumeric(8, false, dims, true);
      if (type.equals("Complex{Int32}")) return readNumeric(4, false, dims, true);
      if (type.equals("Complex{Int16}")) return readNumeric(2, false, dims, true);
      if (type.equals("Complex{Int8}")) return readNumeric(1, false, dims, true);
      if (type.equals("Complex{Float64}")) return readNumeric(8, true, dims, true);
      if (type.equals("Complex{Float32}")) return readNumeric(4, true, dims, true);
      throw new RuntimeException("Unsupported type: "+type);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (IOException ex) {
      throw new RuntimeException("JuliaBridge connection broken", ex);
    }
    return null;
  }

  /**
   * Evaluates an expression in Julia.
   *
   * @param jcode expression to evaluate.
   * @return value of the expression.
   */
  public Object eval(String jcode) {
    exec("__ans__ = ( "+jcode+" )");
    Object rv = get("__ans__");
    set("__ans__", null);
    return rv;
  }

  /**
   * Evaluates an expression in Julia.
   *
   * @param jcode expression to evaluate.
   * @return value of the expression.
   */
  public Object eval(JuliaExpr jcode) {
    return eval(jcode.toString());
  }

  /**
   * Calls a Julia function.
   *
   * @param func name of function.
   * @param args arguments to pass to the function.
   * @return return value of the function.
   */
  public Object call(String func, Object... args) {
    List<String> tmp = new ArrayList<String>();
    StringBuilder sb = new StringBuilder();
    sb.append("__ans__ = ");
    sb.append(func);
    sb.append('(');
    for (int i = 0; i < args.length; i++) {
      if (i > 0) sb.append(", ");
      String s = jexpr(args[i]);
      if (s != null) sb.append(s);
      else {
        String name = "__arg_"+i+"__";
        tmp.add(name);
        sb.append(name);
        set(name, args[i]);
      }
    }
    sb.append(')');
    exec(sb.toString());
    for (String name: tmp)
      set(name, null);
    Object rv = get("__ans__");
    set("__ans__", null);
    return rv;
  }

  /**
   * Creates a Julia expression. Convenience method, equivalent to
   * <code>new JuliaExpr(...)</code>.
   *
   * @param jcode Julia expression.
   */
  public static JuliaExpr expr(String jcode) {
    return new JuliaExpr(jcode);
  }

  /**
   * Creates a Julia complex number.
   *
   * @param x real part.
   * @param y imaginary part.
   */
  public static JuliaExpr complex(double x, double y) {
    return new JuliaExpr("Complex{Float64}("+x+" + "+y+"im)");
  }

  /**
   * Creates a Julia complex number.
   *
   * @param x real part.
   * @param y imaginary part.
   */
  public static JuliaExpr complex(float x, float y) {
    return new JuliaExpr("Complex{Float32}("+x+" + "+y+"im)");
  }

  /**
   * Creates a Julia complex number.
   *
   * @param x real part.
   * @param y imaginary part.
   */
  public static JuliaExpr complex(long x, long y) {
    return new JuliaExpr("Complex{Int64}("+x+" + "+y+"im)");
  }

  /**
   * Creates a Julia complex number.
   *
   * @param x real part.
   * @param y imaginary part.
   */
  public static JuliaExpr complex(int x, int y) {
    return new JuliaExpr("Complex{Int32}("+x+" + "+y+"im)");
  }

  ////// private stuff

  private void openIfNecessary() {
    if (isOpen()) return;
    try {
      open();
    } catch(IOException ex) {
      throw new RuntimeException("Unable to open JuliaBridge", ex);
    }
  }

  private String jexpr(Object value) {
    if (value == null) return "nothing";
    if (value instanceof JuliaExpr) return value.toString();
    if (value instanceof String) return "raw\""+((String)value).replace("\"", "\\\"")+"\"";
    if (value instanceof Long) return "Int64("+value+")";
    if (value instanceof Integer) return "Int32("+value+")";
    if (value instanceof Short) return "Int16("+value+")";
    if (value instanceof Byte) return "Int8("+value+")";
    if (value instanceof Double) return "Float64("+value+")";
    if (value instanceof Float) return "Float32("+value+")";
    return null;
  }

  private Object readNumeric(int nbytes, boolean fp) throws IOException, InterruptedException {
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

  private Object readNumeric(int nbytes, boolean fp, int[] dims, boolean cplx) throws IOException, InterruptedException {
    if (dims == null && !cplx) return readNumeric(nbytes, fp);
    int nelem = 1;
    if (dims == null) dims = new int[0];
    for (int i = 0; i < dims.length; i++)
      nelem *= dims[i];
    if (cplx) nelem *= 2;
    byte[] buf = new byte[nbytes * nelem];
    read(buf, TIMEOUT);
    if (nbytes == 1) {
      ByteArray a = new ByteArray();
      a.data = buf;
      a.dims = dims;
      a.isComplex = cplx;
      return a;
    }
    ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.nativeOrder());
    if (fp) {
      switch (nbytes) {
        case 4: {
          float[] data = new float[nelem];
          bb.asFloatBuffer().get(data);
          FloatArray a = new FloatArray();
          a.data = data;
          a.dims = dims;
          a.isComplex = cplx;
          return a;
        }
        case 8: {
          double[] data = new double[nelem];
          bb.asDoubleBuffer().get(data);
          DoubleArray a = new DoubleArray();
          a.data = data;
          a.dims = dims;
          a.isComplex = cplx;
          return a;
        }
      }
    } else {
      switch (nbytes) {
        case 2: {
          short[] data = new short[nelem];
          bb.asShortBuffer().get(data);
          ShortArray a = new ShortArray();
          a.data = data;
          a.dims = dims;
          a.isComplex = cplx;
          return a;
        }
        case 4: {
          int[] data = new int[nelem];
          bb.asIntBuffer().get(data);
          IntegerArray a = new IntegerArray();
          a.data = data;
          a.dims = dims;
          a.isComplex = cplx;
          return a;
        }
        case 8: {
          long[] data = new long[nelem];
          bb.asLongBuffer().get(data);
          LongArray a = new LongArray();
          a.data = data;
          a.dims = dims;
          a.isComplex = cplx;
          return a;
        }
      }
    }
    return null;
  }

  private void checkArrayDims(int len, int[] dims, boolean cplx) {
    int nelem = 1;
    for (int i = 0; i < dims.length; i++)
      nelem *= dims[i];
    if (cplx) nelem *= 2;
    if (nelem != len) throw new RuntimeException("Bad array dimensions");
  }

  private String buildArrayWriter(String varname, String type, int[] dims) {
    StringBuilder sb = new StringBuilder();
    sb.append(varname);
    sb.append(" = Array{");
    sb.append(type);
    sb.append("}(undef");
    for (int i = 0; i < dims.length; i++) {
      sb.append(", ");
      sb.append(dims[i]);
    }
    sb.append("); read!(stdin, ");
    sb.append(varname);
    sb.append("); ");
    sb.append(TERMINATOR);
    return sb.toString();
  }

  private void waitUntilTerminator() throws IOException, InterruptedException {
    while (true) {
      String s = readline(TIMEOUT);
      if (s == null || s.equals(TERMINATOR)) return;
    }
  }

  private void writeNumeric(String varname, long[] data, int[] dims, boolean cplx) throws IOException, InterruptedException {
    checkArrayDims(data.length, dims, cplx);
    write(buildArrayWriter(varname, cplx?"Complex{Int64}":"Int64", dims));
    ByteBuffer bb = ByteBuffer.allocate(data.length * 8);
    bb.order(ByteOrder.nativeOrder());
    for (int i = 0; i < data.length; i++)
      bb.putLong(data[i]);
    write(bb.array());
    waitUntilTerminator();
  }

  private void writeNumeric(String varname, int[] data, int[] dims, boolean cplx) throws IOException, InterruptedException {
    checkArrayDims(data.length, dims, cplx);
    write(buildArrayWriter(varname, cplx?"Complex{Int32}":"Int32", dims));
    ByteBuffer bb = ByteBuffer.allocate(data.length * 4);
    bb.order(ByteOrder.nativeOrder());
    for (int i = 0; i < data.length; i++)
      bb.putInt(data[i]);
    write(bb.array());
    waitUntilTerminator();
  }

  private void writeNumeric(String varname, short[] data, int[] dims, boolean cplx) throws IOException, InterruptedException {
    checkArrayDims(data.length, dims, cplx);
    write(buildArrayWriter(varname, cplx?"Complex{Int16}":"Int16", dims));
    ByteBuffer bb = ByteBuffer.allocate(data.length * 2);
    bb.order(ByteOrder.nativeOrder());
    for (int i = 0; i < data.length; i++)
      bb.putShort(data[i]);
    write(bb.array());
    waitUntilTerminator();
  }

  private void writeNumeric(String varname, byte[] data, int[] dims, boolean cplx) throws IOException, InterruptedException {
    checkArrayDims(data.length, dims, cplx);
    write(buildArrayWriter(varname, cplx?"Complex{Int8}":"Int8", dims));
    write(data);
    waitUntilTerminator();
  }

  private void writeNumeric(String varname, double[] data, int[] dims, boolean cplx) throws IOException, InterruptedException {
    checkArrayDims(data.length, dims, cplx);
    write(buildArrayWriter(varname, cplx?"Complex{Float64}":"Float64", dims));
    ByteBuffer bb = ByteBuffer.allocate(data.length * 8);
    bb.order(ByteOrder.nativeOrder());
    for (int i = 0; i < data.length; i++)
      bb.putDouble(data[i]);
    write(bb.array());
    waitUntilTerminator();
  }

  private void writeNumeric(String varname, float[] data, int[] dims, boolean cplx) throws IOException, InterruptedException {
    checkArrayDims(data.length, dims, cplx);
    write(buildArrayWriter(varname, cplx?"Complex{Float32}":"Float32", dims));
    ByteBuffer bb = ByteBuffer.allocate(data.length * 4);
    bb.order(ByteOrder.nativeOrder());
    for (int i = 0; i < data.length; i++)
      bb.putFloat(data[i]);
    write(bb.array());
    waitUntilTerminator();
  }

  private String getJuliaExec() {
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

  private void write(String s) throws IOException {
    log.finest("> "+s);
    out.write(s.getBytes());
    out.write(CR);
    out.flush();
  }

  private void write(byte[] b) throws IOException {
    log.finest("> ("+(b.length)+" bytes)");
    out.write(b);
    out.write(CR);
    out.flush();
  }

  private void flush() throws IOException {
    while (inp.available() > 0)
      inp.read();
  }

  private int read(byte[] buf, long timeout) throws IOException, InterruptedException {
    long t = System.currentTimeMillis() + timeout;
    int ofs = 0;
    do {
      int n = inp.available();
      while (n > 0 && !Thread.interrupted()) {
        int m = buf.length - ofs;
        ofs += inp.read(buf, ofs, n>m?m:n);
        if (ofs == buf.length) {
          log.finest("< ("+ofs+" bytes)");
          return ofs;
        }
        n = inp.available();
      }
      Thread.sleep(POLL_DELAY);
    } while (System.currentTimeMillis() < t);
    log.finest("< ("+ofs+" bytes)");
    return ofs;
  }

  private String readline(long timeout) throws IOException, InterruptedException {
    long t = System.currentTimeMillis() + timeout;
    StringBuilder sb = new StringBuilder();
    do {
      while (inp.available() > 0 && !Thread.interrupted()) {
        int b = inp.read();
        if (b == CR) {
          String s = sb.toString();
          log.finest("< "+s);
          return s;
        }
        sb.append((char)b);
      }
      Thread.sleep(POLL_DELAY);
    } while (System.currentTimeMillis() < t);
    if (sb.length() == 0) return null;
    String s = sb.toString();
    log.finest("< "+s);
    return s;
  }

}
