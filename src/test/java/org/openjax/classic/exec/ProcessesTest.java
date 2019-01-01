/* Copyright (c) 2016 OpenJAX
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

package org.openjax.classic.exec;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.Test;
import org.openjax.classic.lang.OperatingSystem;

public class ProcessesTest {
  public static void test(final boolean sync, final boolean redirectErrorStream) throws InterruptedException, IOException {
    if (!OperatingSystem.get().isWindows()) {
      final int exitValue = (int)(Math.random() * 10);
      final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
      final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
      final long start = System.currentTimeMillis();
      final Process process = Processes.fork(null, new PrintStream(stdout), new PrintStream(stderr), redirectErrorStream, sync, null, null, "sh", "-c", "sleep 1 && echo stdout && >&2 echo stderr && exit " + exitValue);
      if (sync)
        process.waitFor();
      else
        Thread.sleep(1300);

      assertEquals(exitValue, process.exitValue());
      final long duration = System.currentTimeMillis() - start;
      assertTrue("Should be ~1000ms, but was: " + duration, 999 < duration && duration < 2000);
      assertEquals(redirectErrorStream ? "stdout\nstderr\n" : "stdout\n", new String(stdout.toByteArray()));
      assertEquals(redirectErrorStream ? "" : "stderr\n", new String(stderr.toByteArray()));
    }
  }

  @Test
  public void testGetPID() {
    assertTrue(Processes.getPID() != -1);
  }

  @Test
  public void testSync() throws InterruptedException, IOException {
    test(true, false);
  }

  @Test
  public void testSyncRedirectError() throws InterruptedException, IOException {
    test(true, true);
  }

  @Test
  public void testAsync() throws InterruptedException, IOException {
    test(false, false);
  }

  @Test
  public void testAsyncRedirectError() throws InterruptedException, IOException {
    test(false, true);
  }
}