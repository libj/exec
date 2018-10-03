# Exec

**API for external process execution and stream management**

## Introduction

Java comes with a standard way to execute subprocesses, provided by the `Process` interface, as well as `ProcessBuilder`.
Despite the availability of these standard interfaces, they fall short when delicate stream management is necessary.
The standard interfaces do not provide stream teeing or piping mechanisms, which are necessary when attempting to programatically read or write from subprocess streams.
When using the standard interfaces, an attempt to programatically read or write from the streams of a subprocess commonly results in a deadlock.

To solve these problems, this library was developed to provide a simple and direct way to execute subprocesses that allow programatically reading or writing from the subprocess streams.

## Usage

This module provides a utility class named `Processes` that can be used to launch subprocesses.

### Synchronous Subprocess

To launch a synchronous subprocess, `Processes` provides 2 method named `forkSync(...)`.
Subprocesses launched synchronously will block the executing thread until completed.

* **General subprocess**

  The following example launches a general subprocess, directing the std streams to the appropriate PrintWriter.

  ```java
  int exitValue = Processes.forkSync(System.in, System.out, System.err, // "stdin", "stdout", "stderr"
                                     false, null, null,                 // "redirectErrorStream", "envp", "dir"
                                     "sh", "-c", "sleep 5 && echo Foo && >&2 echo Bar && exit 1");
  assertEquals(1, exitValue);
  ```

* **Java subprocess**

  The following example launches a general subprocess, directing the std streams to the appropriate PrintWriter.

  ```java
  int exitValue = Processes.forkSync(System.in, System.out, System.err,        // "stdin", "stdout", "stderr"
                                     false, null, null,                        // "redirectErrorStream", "envp", "dir"
                                     ClassLoaders.getClassPath(), null, props, // "classpath", "vmArgs", "props"
                                     MyApp.class, "arg1", "arg2", "arg3");     // "MainClass", "args..."
  assertEquals(1, exitValue);
  ```

### Asynchronous Subprocess

To launch an asynchronous subprocess, `Processes` provides 2 method named `forkAsync(...)`.
Subprocesses launched asynchronously will not block the executing thread.

* **General subprocess**

  The following example launches a general subprocess, directing the std streams to the appropriate PrintWriter.

  ```java
  Process process = Processes.forkAsync(System.in, System.out, System.err, // "stdin", "stdout", "stderr"
                                        false, null, null,                 // "redirectErrorStream", "envp", "dir"
                                        "sh", "-c", "sleep 5 && echo Foo && >&2 echo Bar && exit 1");
  ```

* **Java subprocess**

  The following example launches a general subprocess, directing the std streams to the appropriate PrintWriter.

  ```java
  Process process = Processes.forkAsync(System.in, System.out, System.err,        // "stdin", "stdout", "stderr"
                                        false, null, null,                        // "redirectErrorStream", "envp", "dir"
                                        ClassLoaders.getClassPath(), null, props, // "classpath", "vmArgs", "props"
                                        MyApp.class, "arg1", "arg2", "arg3");     // "MainClass", "args..."
  ```

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

### License

This project is licensed under the MIT License - see the [LICENSE.txt](LICENSE.txt) file for details.