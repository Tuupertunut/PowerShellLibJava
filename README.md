# PowerShellLibJava

A simple library for using PowerShell from Java.

## Usage

`PowerShell.open()` opens a new PowerShell session. You can execute a PowerShell command with `psSession.executeCommands(command)`. It will return the output of the command as a string:
```java
try (PowerShell psSession = PowerShell.open()) {
    System.out.println(psSession.executeCommands("Write-Output 'hello Java'"));
} catch (IOException | PowerShellExecutionException ex) {
    ex.printStackTrace();
}
```
```
hello Java
```

---

You can also execute multiple lines of commands at once:
```java
try (PowerShell psSession = PowerShell.open()) {
    System.out.println(psSession.executeCommands(
            "for ($i = 1; $i -le 5; $i++) {",
            "    Write-Output $i",
            "}"));
} catch (IOException | PowerShellExecutionException ex) {
    ex.printStackTrace();
}
```
```
1
2
3
4
5
```

---

If your PowerShell code has parameters that are dynamically read from Java strings, they might contain characters that have special meaning in PowerShell (kind of like SQL injection). You can sanitize your input with `PowerShell.escapePowerShellString(parameter)`:
```java
String param = "thi's won't bre;ak' the' code";

try (PowerShell psSession = PowerShell.open()) {
    System.out.println(psSession.executeCommands("Write-Output " + PowerShell.escapePowerShellString(param)));
} catch (IOException | PowerShellExecutionException ex) {
    ex.printStackTrace();
}
```
```
thi's won't bre;ak' the' code
```

---

If there is an error on executing the command, a `PowerShellExecutionException` is thrown:
```java
try (PowerShell psSession = PowerShell.open()) {
    System.out.println(psSession.executeCommands("this is not a valid command"));
} catch (IOException | PowerShellExecutionException ex) {
    ex.printStackTrace();
}
```
```
tuupertunut.powershelllibjava.PowerShellExecutionException: Error while executing PowerShell commands:
this : The term 'this' is not recognized as the name of a cmdlet, function, script file, or operable program. Check the spelling of the name, or if a path was included, verify that the path is correct and try again.
...
```

## Requirements

You must have PowerShell installed on your machine.

**OS:** Windows is the only currently supported OS. This library has not been tested on Linux, but it might work.

**Java:** Java 8 or higher is required.

## Maven Central

[![https://mvnrepository.com/artifact/com.github.tuupertunut/powershell-lib-java](https://img.shields.io/maven-central/v/com.github.tuupertunut/powershell-lib-java.svg?style=for-the-badge)](https://mvnrepository.com/artifact/com.github.tuupertunut/powershell-lib-java)

**groupId:** com.github.tuupertunut

**artifactId:** powershell-lib-java
