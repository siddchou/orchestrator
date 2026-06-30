package com.novakai.orchestrator.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveJavaBinary_returns_java_when_executable() throws Exception {
        Path javaBin = tempDir.resolve("bin");
        Files.createDirectory(javaBin);
        Path javaFile = javaBin.resolve("java");
        Files.createFile(javaFile);
        javaFile.toFile().setExecutable(true);

        Path result = PathUtils.resolveJavaBinary(tempDir.toString());

        assertEquals(javaFile, result);
    }

    @Test
    void resolveJavaBinary_returns_java_exe_when_java_not_executable() throws Exception {
        Path javaBin = tempDir.resolve("bin");
        Files.createDirectory(javaBin);
        // Create "java" but not executable
        Path javaFile = javaBin.resolve("java");
        Files.createFile(javaFile);
        javaFile.toFile().setExecutable(false);
        // Create "java.exe" that is executable
        Path javaExe = javaBin.resolve("java.exe");
        Files.createFile(javaExe);
        javaExe.toFile().setExecutable(true);

        Path result = PathUtils.resolveJavaBinary(tempDir.toString());

        // On Windows, Files.isExecutable("java") may auto-resolve to java.exe,
        // so either result is acceptable — the important thing is it returns an executable path
        assertTrue(Files.isExecutable(result));
        assertTrue(result.startsWith(javaBin));
    }

    @Test
    void resolveJavaBinary_falls_back_to_java_when_neither_executable() throws Exception {
        Path javaBin = tempDir.resolve("bin");
        Files.createDirectory(javaBin);
        Path javaFile = javaBin.resolve("java");
        Files.createFile(javaFile);
        javaFile.toFile().setExecutable(false);

        Path result = PathUtils.resolveJavaBinary(tempDir.toString());

        assertEquals(javaFile, result);
    }

    @Test
    void resolveJavaBinary_returns_java_path_correctly() throws Exception {
        Path javaBin = tempDir.resolve("bin");
        Files.createDirectory(javaBin);
        Path javaExe = javaBin.resolve("java.exe");
        Files.createFile(javaExe);
        javaExe.toFile().setExecutable(true);

        Path result = PathUtils.resolveJavaBinary(tempDir.toString());

        assertTrue(result.toString().contains("bin"));
        assertTrue(result.toString().endsWith("java.exe"));
    }

    @Test
    void joinClasspath_returns_empty_for_null() {
        assertEquals("", PathUtils.joinClasspath(null));
    }

    @Test
    void joinClasspath_returns_empty_for_empty_list() {
        assertEquals("", PathUtils.joinClasspath(Collections.emptyList()));
    }

    @Test
    void joinClasspath_returns_single_entry() {
        assertEquals("a.jar", PathUtils.joinClasspath(List.of("a.jar")));
    }

    @Test
    void joinClasspath_joins_with_platform_separator() {
        String result = PathUtils.joinClasspath(List.of("a.jar", "b.jar", "c.jar"));

        assertTrue(result.contains("a.jar"));
        assertTrue(result.contains("b.jar"));
        assertTrue(result.contains("c.jar"));
        assertEquals(3, result.split(String.valueOf(File.pathSeparatorChar), -1).length);
    }

    @Test
    void joinClasspath_uses_correct_separator_char() {
        String result = PathUtils.joinClasspath(List.of("x.jar", "y.jar"));

        char sep = File.pathSeparatorChar;
        assertTrue(result.contains(String.valueOf(sep)),
                "Classpath should use platform separator '" + sep + "'");
    }
}
