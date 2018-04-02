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
package tuupertunut.powershelllibjava;

import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An asynchronous recorder for Readers (character streams). A recorder instance
 * is associated with a {@link java.io.Reader} and then executed on a
 * {@link java.lang.Thread} or an {@link java.util.concurrent.Executor}. The
 * recorder will then autonomously record the input of the given Reader into an
 * internal buffer. Another thread may then consume the buffer in parts, or as a
 * whole.
 *
 * @author Tuupertunut
 */
public class AsyncReaderRecorder implements Runnable {

    private final Reader reader;

    private final StringBuilder buffer = new StringBuilder();
    private IOException storedException = null;
    private boolean endOfStreamReached = false;
    private boolean inputReadingInProgress = false;

    private final Lock lock = new ReentrantLock();
    private final Condition consumptionCondition = lock.newCondition();

    private ConsumptionConditionType conditionType;
    private String delimiter;
    private int amount;

    private int firstDelimiterIndex;

    /**
     * Creates a new recorder, recording the given Reader.
     *
     * @param reader the reader to be recorded.
     */
    public AsyncReaderRecorder(Reader reader) {
        this.reader = reader;
    }

    /**
     * Records the reader into the buffer. This method is meant to be executed
     * on a separate thread. It will block for as long as the reader has not
     * closed or thrown an exception.
     */
    @Override
    public void run() {
        while (true) {
            IOException exception = null;
            int readResult = -1;
            try {
                readResult = reader.read();
            } catch (IOException ex) {
                exception = ex;
            }

            if (Thread.interrupted()) {
                break;
            }

            lock.lock();
            try {
                if (exception != null) {
                    storedException = exception;
                    consumptionCondition.signalAll();
                    break;
                } else if (readResult == -1) {
                    endOfStreamReached = true;
                    consumptionCondition.signalAll();
                    break;
                } else {
                    buffer.append((char) readResult);

                    try {
                        inputReadingInProgress = reader.ready();
                    } catch (IOException ex) {
                        storedException = ex;
                        consumptionCondition.signalAll();
                        break;
                    }

                    if (conditionType == ConsumptionConditionType.CURRENT_INPUT) {
                        if (!inputReadingInProgress) {
                            consumptionCondition.signalAll();
                        }
                    } else if (conditionType == ConsumptionConditionType.DELIMITER) {
                        if (bufferEndsWith(delimiter)) {
                            firstDelimiterIndex = buffer.length();
                            consumptionCondition.signalAll();
                        }
                    } else if (conditionType == ConsumptionConditionType.AMOUNT) {
                        if (buffer.length() >= amount) {
                            consumptionCondition.signalAll();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    // internal methods
    private boolean bufferEndsWith(String s) {
        if (buffer.length() < s.length()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (buffer.charAt(buffer.length() - 1 - i) != s.charAt(s.length() - 1 - i)) {
                return false;
            }
        }
        return true;
    }

    private int bufferFirstEndIndexOf(String s) {
        int index = buffer.indexOf(s);
        if (index != -1) {
            index += s.length();
        }
        return index;
    }

    private boolean shouldWaitForCurrentInput() {
        return inputReadingInProgress && !endOfStreamReached && storedException == null;
    }

    private boolean shouldWaitForDelimiter() {
        return firstDelimiterIndex == -1 && !endOfStreamReached && storedException == null;
    }

    private boolean shouldWaitForAmount() {
        return buffer.length() < amount && !endOfStreamReached && storedException == null;
    }

    // info methods
    public boolean hasStreamEnded() {
        lock.lock();
        try {
            return endOfStreamReached;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasExceptionOccurred() {
        lock.lock();
        try {
            return storedException != null;
        } finally {
            lock.unlock();
        }
    }

    public IOException getException() {
        lock.lock();
        try {
            return storedException;
        } finally {
            lock.unlock();
        }
    }

    public String getBuffer() throws IOException {
        lock.lock();
        try {
            if (storedException != null) {
                throw storedException;
            }

            return buffer.toString();
        } finally {
            lock.unlock();
        }
    }

    public int getBufferedAmount() throws IOException {
        lock.lock();
        try {
            if (storedException != null) {
                throw storedException;
            }

            return buffer.length();
        } finally {
            lock.unlock();
        }
    }

    // consumption methods
    /**
     * Consumes and returns the whole buffer at once.
     *
     * Note: Only one consumption method should be called at once.
     *
     * @return the buffer contents.
     * @throws IOException if an IOException has occurred since the last
     * consumption.
     */
    public String consumeAll() throws IOException {
        lock.lock();
        try {
            if (storedException != null) {
                throw storedException;
            }

            String bufferContent = buffer.toString();
            buffer.setLength(0);
            return bufferContent;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Consumes and returns the whole buffer after the current reading is
     * complete. This method will wait until there is no input immediately
     * available from the Reader. This is preferable to {@link #consumeAll()} in
     * cases where the input is some kind of message based communication,
     * because the whole current message will be included into the buffer before
     * consuming it.
     *
     * Note: Only one consumption method should be called at once.
     *
     * @return the buffer contents.
     * @throws IOException if an IOException has occurred since the last
     * consumption or occurs while waiting.
     * @throws InterruptedException if the current thread was interrupted while
     * waiting for the current input to end.
     */
    public String consumeAllAfterCurrentInput() throws IOException, InterruptedException {
        lock.lock();
        try {
            if (shouldWaitForCurrentInput()) {
                conditionType = ConsumptionConditionType.CURRENT_INPUT;

                while (shouldWaitForCurrentInput()) {
                    consumptionCondition.await();
                }

                conditionType = ConsumptionConditionType.NONE;
            }

            if (storedException != null) {
                throw storedException;
            }

            String bufferContent = buffer.toString();
            buffer.setLength(0);
            return bufferContent;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Consumes and returns the buffer up to the point where the delimiter first
     * occurs. The delimiter will be included in the consumed part. If the
     * delimiter does not yet occur in the buffer, this method will wait until
     * the next one is recorded or the input stream ends. This is useful when
     * the input is divided into segments, separated by a delimiter.
     *
     * Note: Only one consumption method should be called at once.
     *
     * @param delimiter the delimiter up to which to consume.
     * @return an optional containing the buffer contents up to and including
     * the delimiter, or an empty optional if the input stream ended before a
     * delimiter was recorded.
     * @throws IOException if an IOException has occurred since the last
     * consumption or occurs while waiting.
     * @throws InterruptedException if the current thread was interrupted while
     * waiting for the delimiter to be recorded.
     */
    public Optional<String> consumeToNextDelimiter(String delimiter) throws IOException, InterruptedException {
        lock.lock();
        try {
            firstDelimiterIndex = bufferFirstEndIndexOf(delimiter);

            if (shouldWaitForDelimiter()) {
                conditionType = ConsumptionConditionType.DELIMITER;
                this.delimiter = delimiter;

                while (shouldWaitForDelimiter()) {
                    consumptionCondition.await();
                }

                conditionType = ConsumptionConditionType.NONE;
            }

            if (storedException != null) {
                throw storedException;
            }

            if (firstDelimiterIndex != -1) {
                String bufferContent = buffer.substring(0, firstDelimiterIndex);
                buffer.delete(0, firstDelimiterIndex);
                return Optional.of(bufferContent);
            } else {
                return Optional.empty();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Consumes and returns a fixed amount of characters from the buffer. If the
     * buffer does not yet contain that many characters, this method will wait
     * until it does or the input stream ends.
     *
     * Note: Only one consumption method should be called at once.
     *
     * @param amount the amount of characters to consume.
     * @return an optional containing the next given amount of buffer contents,
     * or an empty optional if the input stream ended before that many
     * characters were recorded.
     * @throws IOException if an IOException has occurred since the last
     * consumption or occurs while waiting.
     * @throws InterruptedException if the current thread was interrupted while
     * waiting for more characters to be recorded.
     */
    public Optional<String> consumeAmount(int amount) throws IOException, InterruptedException {
        lock.lock();
        try {
            if (shouldWaitForAmount()) {
                conditionType = ConsumptionConditionType.AMOUNT;
                this.amount = amount;

                while (shouldWaitForAmount()) {
                    consumptionCondition.await();
                }

                conditionType = ConsumptionConditionType.NONE;
            }

            if (storedException != null) {
                throw storedException;
            }

            if (buffer.length() >= amount) {
                String bufferContent = buffer.substring(0, amount);
                buffer.delete(0, amount);
                return Optional.of(bufferContent);
            } else {
                return Optional.empty();
            }
        } finally {
            lock.unlock();
        }
    }

    private static enum ConsumptionConditionType {

        NONE,
        CURRENT_INPUT,
        DELIMITER,
        AMOUNT;
    }
}
