package main.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    public static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    javaFiles.addAll(findJavaFiles(file));
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
        return javaFiles;
    }

    public static List<File> findJarFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    javaFiles.addAll(findJarFiles(file));
                } else if (file.getName().endsWith(".jar")) {
                    javaFiles.add(file);
                }
            }
        }
        return javaFiles;
    }
}
