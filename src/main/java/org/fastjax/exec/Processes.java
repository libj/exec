/* Copyright (c) 2006 FastJAX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.fastjax.exec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.fastjax.io.Streams;
import org.fastjax.util.Arrays;

/**
 * Utility class that provides convenience methods to launch child processes. The
 * implementations of the methods in this class guarantee proper management of
 * the stdin, stdout and stderr streams (for sub-processes that are launched
 * both synchronously and asynchronously).
 */
public final class Processes {
  /**
   * Returns the PID of the current running process.
   *
   * @return The PID of the current running process.
   */
  public static int getPID() {
    final String pidAtHost = ManagementFactory.getRuntimeMXBean().getName();
    if (pidAtHost == null)
      return -1;

    try {
      return Integer.parseInt(pidAtHost.substring(0, pidAtHost.indexOf("@")));
    }
    catch (final NumberFormatException e) {
      return -1;
    }
  }

  private static final Predicate<String> notNullPredicate = new Predicate<String>() {
    @Override
    public boolean test(final String value) {
      return value != null;
    }
  };

  @SuppressWarnings("rawtypes")
  private static Map<String,String> getSystemProperties() {
    if (System.getProperties().size() == 0)
      return new HashMap<>(0);

    final Map<String,String> properties = new HashMap<>(7);
    for (final Map.Entry property : System.getProperties().entrySet()) {
      final String key = (String)property.getKey();
      final String value = ((String)property.getValue()).trim();
      if (value.length() != 0 && value.indexOf(' ') == -1 && key.indexOf(' ') == -1)
        properties.put(key, value);
    }

    return properties;
  }

  private static Map<String,String> combineProperties(final Map<String,String> props) {
    if (props == null)
      return getSystemProperties();

    final Map<String,String> all = getSystemProperties();
    all.putAll(props);
    return all;
  }

  /**
   * Fork a process.
   *
   * @param stdin The stdin {@code InputStream}.
   * @param stdout The stdout {@code OutputStream}.
   * @param stderr The stderr {@code OutputStream}.
   * @param redirectErrorStream Whether to redirect the stderr stream to stdout.
   * @param sync Whether the current process will be blocked until the forked
   *          process is finish.
   * @param args Process command arguments.
   * @return The forked process handler.
   * @throws IOException If an I/O error occurs.
   */
  static Process fork(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final boolean sync, String ... args) throws IOException {
    args = Arrays.filter(notNullPredicate, args);
    final Process process = Runtime.getRuntime().exec(args);
    final OutputStream teeStdin = stdin != null ? Streams.teeAsync(process.getOutputStream(), stdin, stdout) : process.getOutputStream();

    InputStream teeStdout = redirectErrorStream ? Streams.merge(process.getInputStream(), process.getErrorStream()) : process.getInputStream();
    if (sync)
      Streams.pipeAsync(teeStdout, stdout);
    else if (stdout != null)
      teeStdout = Streams.teeAsync(teeStdout, stdout);

    InputStream teeStderr = process.getErrorStream();
    if (!redirectErrorStream) {
      if (sync)
        Streams.pipeAsync(process.getErrorStream(), stderr);
      else if (stderr != null)
        teeStderr = Streams.teeAsync(process.getErrorStream(), stderr);
    }

    return new PipedProcess(process, teeStdin, teeStdout, teeStderr);
  }

  /**
   * Fork a non-blocking process.
   *
   * @param stdin The stdin {@code InputStream}.
   * @param stdout The stdout {@code OutputStream}.
   * @param stderr The stderr {@code OutputStream}.
   * @param redirectErrorStream Whether to redirect the stderr stream to stdout.
   * @param args Process command arguments.
   * @return The forked process handler.
   * @throws IOException If an I/O error occurs.
   */
  public static Process forkAsync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final String ... args) throws IOException {
    return fork(stdin, stdout, stderr, redirectErrorStream, false, args);
  }

  /**
   * Fork a non-blocking Java process.
   *
   * @param stdin The stdin {@code InputStream}.
   * @param stdout The stdout {@code OutputStream}.
   * @param stderr The stderr {@code OutputStream}.
   * @param redirectErrorStream Whether to redirect the stderr stream to stdout.
   * @param classpath Classpath URLs.
   * @param vmArgs JavaVM arguments for the forked Java process.
   * @param props Map of name=value properties for the forked Java process.
   * @param mainClass The class with the {@code main(String[])} method to launch.
   * @param args Process command arguments.
   * @return The forked process handler.
   * @throws IOException If an I/O error occurs.
   */
  public static Process forkAsync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final URL[] classpath, final String[] vmArgs, final Map<String,String> props, final Class<?> mainClass, final String ... args) throws IOException {
    return forkAsync(stdin, stdout, stderr, redirectErrorStream, createJavaCommand(classpath, vmArgs, combineProperties(props), mainClass, args));
  }

  /**
   * Fork a blocking process.
   *
   * @param stdin The stdin {@code InputStream}.
   * @param stdout The stdout {@code OutputStream}.
   * @param stderr The stderr {@code OutputStream}.
   * @param redirectErrorStream Whether to redirect the stderr stream to stdout.
   * @param args Process command arguments.
   * @return The exit value of the process represented by this Process object.
   *         By convention, the value 0 indicates normal termination.
   * @throws IOException If an I/O error occurs.
   * @throws InterruptedException If the current thread is interrupted by
   *           another thread while it is waiting, then the wait is ended and an
   *           {@link InterruptedException} is thrown.
   */
  public static int forkSync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final String ... args) throws InterruptedException, IOException {
    final Process process = fork(stdin, stdout, stderr, redirectErrorStream, true, args);
    return process.waitFor();
  }

  /**
   * Fork a blocking Java process.
   *
   * @param stdin The stdin {@code InputStream}.
   * @param stdout The stdout {@code OutputStream}.
   * @param stderr The stderr {@code OutputStream}.
   * @param redirectErrorStream Whether to redirect the stderr stream to stdout.
   * @param classpath Classpath URLs.
   * @param vmArgs JavaVM arguments for the forked Java process.
   * @param props Map of name=value properties for the forked Java process.
   * @param mainClass The class with the {@code main(String[])} method to launch.
   * @param args Process command arguments.
   * @return The exit value of the process represented by this Process object.
   *         By convention, the value 0 indicates normal termination.
   * @throws IOException If an I/O error occurs.
   * @throws InterruptedException If the current thread is interrupted by
   *           another thread while it is waiting, then the wait is ended and an
   *           {@link InterruptedException} is thrown.
   */
  public static int forkSync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final URL[] classpath, final String[] vmArgs, final Map<String,String> props, final Class<?> mainClass, final String ... args) throws InterruptedException, IOException {
    final Process process = forkAsync(stdin, stdout, stderr, redirectErrorStream, createJavaCommand(classpath, vmArgs, combineProperties(props), mainClass, args));
    return process.waitFor();
  }

  private static String[] createJavaCommand(final URL[] classpath, final String[] vmArgs, final Map<String,String> props, final Class<?> mainClass, final String ... args) {
    final StringBuilder cp = new StringBuilder();
    if (classpath != null && classpath.length != 0) {
      for (int i = 0; i < classpath.length; ++i) {
        if (i > 0)
          cp.append(File.pathSeparatorChar);

        cp.append(classpath[i].getPath());
      }
    }

    final String[] options = new String[(args != null ? args.length : 0) + (vmArgs != null ? vmArgs.length : 0) + (props != null ? props.size() : 0) + 4];
    int i = -1;
    options[++i] = "java";
    if (vmArgs != null && vmArgs.length != 0)
      for (final String vmArg : vmArgs)
        options[++i] = vmArg;

    if (props != null && props.size() != 0)
      for (final Map.Entry<String,String> property : props.entrySet())
        options[++i] = "-D" + property.getKey() + "=" + property.getValue();

    options[++i] = "-cp";
    options[++i] = cp.toString();
    options[++i] = mainClass.getName();
    System.arraycopy(args, 0, options, ++i, args.length);

    return options;
  }

  private static final class PipedProcess extends Process {
    private final Process process;
    private final OutputStream stdin;
    private final InputStream stdout;
    private final InputStream stderr;

    public PipedProcess(final Process process, final OutputStream stdin, final InputStream stdout, final InputStream stderr) {
      this.process = process;
      this.stdin = stdin;
      this.stdout = stdout;
      this.stderr = stderr;
    }

    @Override
    public OutputStream getOutputStream() {
      return stdin;
    }

    @Override
    public InputStream getInputStream() {
      return stdout;
    }

    @Override
    public InputStream getErrorStream() {
      return stderr;
    }

    @Override
    public int waitFor() throws InterruptedException {
      return process.waitFor();
    }

    @Override
    public int exitValue() {
      return process.exitValue();
    }

    @Override
    public void destroy() {
      process.destroy();
    }
  }

  private Processes() {
  }
}