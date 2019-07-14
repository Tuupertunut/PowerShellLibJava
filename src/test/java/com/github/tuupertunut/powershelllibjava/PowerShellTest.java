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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.tuupertunut.powershelllibjava;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Tuupertunut
 */
public class PowerShellTest {

    @Test(timeout = 7000)
    public void testHelloJava() throws IOException, PowerShellExecutionException {
        try (PowerShell psSession = PowerShell.open()) {
            Assert.assertEquals("hello Java" + System.lineSeparator(), psSession.executeCommands("Write-Output 'hello Java'"));
        }
    }

    @Test(timeout = 7000)
    public void testMultiline() throws IOException, PowerShellExecutionException {
        try (PowerShell psSession = PowerShell.open()) {
            Assert.assertEquals(
                    "1" + System.lineSeparator()
                    + "2" + System.lineSeparator()
                    + "3" + System.lineSeparator()
                    + "4" + System.lineSeparator()
                    + "5" + System.lineSeparator(),
                    psSession.executeCommands(
                            "for ($i = 1; $i -le 5; $i++) {",
                            "    Write-Output $i",
                            "}"));
        }
    }

    @Test(timeout = 7000)
    public void testRememberSession() throws IOException, PowerShellExecutionException {
        try (PowerShell psSession = PowerShell.open()) {
            psSession.executeCommands("$s = 'abc'");
            Assert.assertEquals("abc" + System.lineSeparator(), psSession.executeCommands("Write-Output $s"));
        }
    }

    @Test(timeout = 7000)
    public void testEscape() throws IOException, PowerShellExecutionException {
        String param = "thi's won't bre;ak' the' code";

        try (PowerShell psSession = PowerShell.open()) {
            Assert.assertEquals("thi's won't bre;ak' the' code" + System.lineSeparator(), psSession.executeCommands("Write-Output " + PowerShell.escapePowerShellString(param)));
        }
    }

    @Test(timeout = 7000)
    public void testExceptionOnInvalidCommand() throws IOException {
        try (PowerShell psSession = PowerShell.open()) {
            psSession.executeCommands("this is not a valid command");
            Assert.fail();
        } catch (PowerShellExecutionException ex) {
        }
    }

    @Test(timeout = 7000)
    public void testExceptionOnThrow() throws IOException {
        try (PowerShell psSession = PowerShell.open()) {
            psSession.executeCommands("throw 'error message'");
            Assert.fail();
        } catch (PowerShellExecutionException ex) {
        }
    }

    @Test(timeout = 7000)
    public void testExceptionOnUnclosedString() throws IOException {
        try (PowerShell psSession = PowerShell.open()) {
            psSession.executeCommands("Write-Output 'unclosed");
            Assert.fail();
        } catch (PowerShellExecutionException ex) {
        }
    }
}
