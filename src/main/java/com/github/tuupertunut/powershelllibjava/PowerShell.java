/*
 * The MIT License
 *
 * Copyright 2018 Tuupertunut.
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.tuupertunut.powershelllibjava;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PowerShell session that can be used to execute PowerShell commands. An
 * instance of this class can be created with {@link #open()} or
 * {@link #open(java.lang.String)}. Instances should always be closed with
 * {@link #close()} to free resources.
 *
 * @author Tuupertunut
 */
public class PowerShell implements Closeable {

    private static final String DEFAULT_WIN_EXECUTABLE = "powershell";
    private static final String DEFAULT_CORE_EXECUTABLE = "pwsh";

    /* This string marks the end of command output. It should be as unique as
     * possible. This library assumes that the string never occurs as a
     * substring in any powershell command output. If that happens, behavior is
     * undefined. */
    private static final String END_OF_COMMAND = "end-of-command-8Nb77LFv";

    private final Process psSession;
    private final BufferedReader commandOutput;
    private final BufferedReader commandErrorOutput;
    private final AsyncReaderRecorder outputRecorder;
    private final AsyncReaderRecorder errorOutputRecorder;
    private final ExecutorService executor;
    private final PrintWriter commandInput;

    private boolean closed;

    private PowerShell(String psExecutable) throws IOException {

        psSession = createProcessBuilder(psExecutable).start();

        commandOutput = new BufferedReader(new InputStreamReader(psSession.getInputStream(), StandardCharsets.UTF_8));
        commandErrorOutput = new BufferedReader(new InputStreamReader(psSession.getErrorStream(), StandardCharsets.UTF_8));

        outputRecorder = new AsyncReaderRecorder(commandOutput);
        errorOutputRecorder = new AsyncReaderRecorder(commandErrorOutput);
        executor = Executors.newCachedThreadPool();
        executor.execute(outputRecorder);
        executor.execute(errorOutputRecorder);

        commandInput = new PrintWriter(new BufferedWriter(new OutputStreamWriter(psSession.getOutputStream(), StandardCharsets.UTF_8)), true);

        closed = false;
    }

    private static ProcessBuilder createProcessBuilder(String psExecutable) {

        /* Windows needs some extra configuration to understand UTF-8. */
        if (isWindows()) {

            /* cmd /c chcp 65001 : Set console codepage to UTF-8, so that input
             * and output streams of the console will be interpreted as UTF-8.
             *
             * > NUL : Discard any output from the codepage change command.
             *
             * & *psExecutable* : If codepage change was successful, start
             * powershell.
             *
             * -ExecutionPolicy Bypass : Disable any prompts about unsigned
             * scripts, because there is no way to answer prompts.
             *
             * -NoExit : Keep the session open after executing the first
             * command.
             *
             * -Command - : Read commands from standard input stream of the
             * process. */
            return new ProcessBuilder("cmd", "/c", "chcp", "65001", ">", "NUL", "&", psExecutable, "-ExecutionPolicy", "Bypass", "-NoExit", "-Command", "-");
        } else {
            return new ProcessBuilder(psExecutable, "-ExecutionPolicy", "Bypass", "-NoExit", "-Command", "-");
        }
    }

    /* Apache commons says this is a valid way to detect Windows.
     * https://github.com/apache/commons-lang/blob/LANG_3_7/src/main/java/org/apache/commons/lang3/SystemUtils.java */
    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private static String getDefaultExecutable() {
        if (isWindows()) {
            return DEFAULT_WIN_EXECUTABLE;
        } else {
            return DEFAULT_CORE_EXECUTABLE;
        }
    }

    /**
     * Opens a new PowerShell session with default executable. On Windows, the
     * default executable is "powershell" from Windows PowerShell, and on other
     * platforms it is "pwsh" from PowerShell Core.
     *
     * @return a new PowerShell session.
     * @throws IOException if an IOException occurred on process creation.
     */
    public static PowerShell open() throws IOException {
        return new PowerShell(getDefaultExecutable());
    }

    /**
     * Opens a new PowerShell session with the provided executable.
     *
     * @param customExecutable the PowerShell executable. Can be an executable
     * name like "pwsh" or a path to the executable file.
     * @return a new PowerShell session.
     * @throws IOException if an IOException occurred on process creation.
     */
    public static PowerShell open(String customExecutable) throws IOException {
        return new PowerShell(customExecutable);
    }

    /**
     * Closes this PowerShell session and frees all resources associated with
     * it.
     */
    @Override
    public void close() {
        closed = true;
        if (commandInput != null) {
            commandInput.close();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (commandErrorOutput != null) {
            try {
                commandErrorOutput.close();
            } catch (IOException ex) {
            }
        }
        if (commandOutput != null) {
            try {
                commandOutput.close();
            } catch (IOException ex) {
            }
        }
        if (psSession != null) {
            psSession.destroy();
        }
    }

    /**
     * Executes one or more PowerShell commands. This method will wait until all
     * of the commands are executed, and returns the standard output. If
     * multiple commands are given, they will be executed in order. It is also
     * possible to break multiline commands to multiple parts, like this:
     *
     * <pre>
     * {@code
     * executeCommands(
     *         "if ($cond) {",
     *         "    Do-Stuff",
     *         "}")
     * }
     * </pre>
     *
     * Internally, the commands are just joined with a semicolon {@code ';'}.
     *
     * @param commands one or more commands to execute.
     * @return the standard output of the commands.
     * @throws PowerShellExecutionException if a command encountered an error
     * (wrote something to the standard error stream) while executing.
     * @throws IOException if an IOException occurred while reading the output
     * of the commands.
     * @throws IllegalStateException if this PowerShell session was already
     * closed.
     * @throws RuntimeException if the output stream ended too early, or if the
     * current thread was interrupted while executing.
     */
    public String executeCommands(String... commands) throws PowerShellExecutionException, IOException {
        if (closed) {
            throw new IllegalStateException("This PowerShell session has been closed.");
        }

        StringBuilder commandChainBuilder = new StringBuilder();
        for (String command : commands) {
            commandChainBuilder.append(command);
            commandChainBuilder.append(";");
        }
        String commandChain = commandChainBuilder.toString();

        /* Wrapping the command chain in an "Invoke-Expression" statement in
         * order to sanitize the user input. Otherwise it would be possible to
         * input partial code and leave the PowerShell session in an invalid
         * state where it cannot accept another command. An example would be
         * beginning a string with a quote but not closing it.
         *
         * Also ending the command chain with a command to print the end of
         * command string. This way the end of command can be detected in the
         * output. */
        String wrappedCommandChain = "Invoke-Expression " + escapePowerShellString(commandChain) + ";" + escapePowerShellString(END_OF_COMMAND);

        commandInput.println(wrappedCommandChain);

        try {

            /* Reading the output to the next end of command string. PowerShell
             * also prints a line separator after it. The method may also return
             * an empty Optional if the stream ended before the end of command
             * was reached, but that should not happen under normal
             * circumstances. */
            Optional<String> optionalOutput = outputRecorder.consumeToNextDelimiter(END_OF_COMMAND + System.lineSeparator());
            if (!optionalOutput.isPresent()) {
                throw new RuntimeException("PowerShell output stream ended too early.");
            }
            String output = optionalOutput.get().replace(END_OF_COMMAND + System.lineSeparator(), "");

            /* Checking if any errors were produced during the execution. */
            String errorOutput = errorOutputRecorder.consumeAllAfterCurrentInput();
            if (!errorOutput.isEmpty()) {
                throw new PowerShellExecutionException("Error while executing PowerShell commands:" + System.lineSeparator() + errorOutput);
            }

            return output;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    /**
     * Wraps a string in quotes and escapes all PowerShell special characters.
     * This is a helper method for creating strings that will be interpreted
     * literally by PowerShell.
     *
     * @param s the string to be escaped.
     * @return an escaped string.
     */
    public static String escapePowerShellString(String s) {
        return "'" + s.replace("'", "''") + "'";
    }
}
