package com.novakai.orchestrator.engine;

// @author Siddhant Choudhary

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PathUtils {

    private PathUtils() {}

    public static Path resolveJavaBinary(String javaHome) {
        Path javaBin = Path.of(javaHome, "bin", "java");
        if (Files.isExecutable(javaBin)) {
            return javaBin;
        }
        Path javaExe = Path.of(javaHome, "bin", "java.exe");
        if (Files.isExecutable(javaExe)) {
            return javaExe;
        }
        return javaBin;
    }

    public static String joinClasspath(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(File.pathSeparatorChar);
            }
            sb.append(entries.get(i));
        }
        return sb.toString();
    }
}
