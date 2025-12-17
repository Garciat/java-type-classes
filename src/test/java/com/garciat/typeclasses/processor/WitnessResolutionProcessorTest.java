package com.garciat.typeclasses.processor;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WitnessResolutionProcessorTest {
  @Nullable @TempDir Path tempDir;

  @Test
  public void test() throws IOException {
    requireNonNull(tempDir);

    // Given
    var compiler = ToolProvider.getSystemJavaCompiler();

    var diagnostics = new DiagnosticCollector<JavaFileObject>();

    var fileManager = compiler.getStandardFileManager(diagnostics, null, null);
    fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(tempDir.toFile()));
    fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(tempDir.toFile()));

    var files = new java.util.ArrayList<File>();
    files.add(new File("src/test/java/com/garciat/typeclasses/ExamplesTest.java"));

    var compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);

    var task =
        compiler.getTask(
            null,
            fileManager,
            diagnostics,
            List.of(
                "-Xplugin:WitnessResolutionChecker",
                "-classpath",
                System.getProperty("java.class.path")),
            null,
            compilationUnits);

    // When
    boolean success = task.call();

    // Then
    assertThat(diagnostics.getDiagnostics()).isEmpty();
    assertThat(success).isTrue();
  }
}
