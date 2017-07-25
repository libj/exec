/* Copyright (c) 2006 lib4j
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

package org.lib4j.exec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.lib4j.io.Streams;
import org.lib4j.lang.Arrays;
import org.lib4j.lang.ClassLoaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Processes {
  private static final Logger logger = LoggerFactory.getLogger(Processes.class);

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

  private static Process fork(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final boolean sync, String ... args) throws IOException {
    args = Arrays.filter(notNullPredicate, args);
    logger.debug(Arrays.toString(args, " "));
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

  public static Process forkAsync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final String ... args) throws IOException {
    return fork(stdin, stdout, stderr, redirectErrorStream, false, args);
  }

  public static Process forkSync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final String ... args) throws IOException, InterruptedException {
    final Process process = fork(stdin, stdout, stderr, redirectErrorStream, true, args);
    process.waitFor();
    return process;
  }

  public static Process forkAsync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, Map<String,String> props, final Class<?> clazz, final String ... args) throws IOException {
    final Process process = forkAsync(stdin, stdout, stderr, redirectErrorStream, createJavaCommand(null, getSystemProperties(), clazz, args));
    return process;
  }

  public static Process forkAsync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final String[] vmArgs, final Map<String,String> props, final Class<?> clazz, final String ... args) throws IOException {
    final Process process = forkAsync(stdin, stdout, stderr, redirectErrorStream, createJavaCommand(vmArgs, getSystemProperties(), clazz, args));
    return process;
  }

  public static Process forkAsync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final String[] vmArgs, final Class<?> clazz, final String ... args) throws IOException {
    return forkAsync(stdin, stdout, stderr, redirectErrorStream, vmArgs, getSystemProperties(), clazz, args);
  }

  public static Process forkAsync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final Class<?> clazz, final String ... args) throws IOException {
    return forkAsync(stdin, stdout, stderr, redirectErrorStream, null, getSystemProperties(), clazz, args);
  }

  public static Process forkSync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final String[] vmArgs, final Map<String,String> props, final Class<?> clazz, final String ... args) throws IOException, InterruptedException {
    final Process process = forkAsync(stdin, stdout, stderr, redirectErrorStream, createJavaCommand(vmArgs, getSystemProperties(), clazz, args));
    process.waitFor();
    return process;
  }

  public static Process forkSync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final Map<String,String> props, final Class<?> clazz, final String ... args) throws IOException, InterruptedException {
    final Process process = forkAsync(stdin, stdout, stderr, redirectErrorStream, createJavaCommand(null, getSystemProperties(), clazz, args));
    process.waitFor();
    return process;
  }

  public static Process forkSync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final String[] vmArgs, final Class<?> clazz, final String ... args) throws IOException, InterruptedException {
    return forkSync(stdin, stdout, stderr, redirectErrorStream, vmArgs, getSystemProperties(), clazz, args);
  }

  public static Process forkSync(final InputStream stdin, final OutputStream stdout, final OutputStream stderr, final boolean redirectErrorStream, final Class<?> clazz, final String ... args) throws IOException, InterruptedException {
    return forkSync(stdin, stdout, stderr, redirectErrorStream, getSystemProperties(), clazz, args);
  }

  private static String[] createJavaCommand(final String[] vmArgs, final Map<String,String> props, final Class<?> clazz, final String ... args) {
    final URL[] classpathURLs = ClassLoaders.getClassPath();
    final StringBuffer classpath = new StringBuffer();
    if (classpathURLs != null && classpathURLs.length != 0)
      for (final URL url : classpathURLs)
        classpath.append(File.pathSeparatorChar).append(url.getPath());

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
    options[++i] = classpath.length() != 0 ? classpath.substring(1) : "";
    options[++i] = clazz.getName();
    System.arraycopy(args, 0, options, ++i, args.length);

    return options;
  }

  @SuppressWarnings("rawtypes")
  private static Map<String,String> getSystemProperties() {
    if (System.getProperties().size() == 0)
      return new HashMap<String,String>(0);

    final Map<String,String> properties = new HashMap<String,String>(7);
    for (final Map.Entry property : System.getProperties().entrySet()) {
      final String key = (String)property.getKey();
      final String value = (String)property.getValue();
      if (value.trim().length() != 0 && !value.contains(" ") && !key.contains(" "))
        properties.put(key, value.trim());
    }

    return properties;
  }

  private static final class PipedProcess extends Process {
    private final Process process;
    private final OutputStream stdin;
    private final InputStream stdout;
    private final InputStream stderr;

    public PipedProcess(final Process process, final OutputStream stdin, InputStream stdout, final InputStream stderr) {
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