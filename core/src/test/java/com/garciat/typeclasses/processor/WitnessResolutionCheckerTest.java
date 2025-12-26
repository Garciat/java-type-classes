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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class WitnessResolutionCheckerTest {
  @Nullable @TempDir Path tempDir;

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Example1.java",
        "Example2.java",
        "Example3.java",
        "Example4.java",
        "Example5.java",
        "Example6.java",
      })
  public void checkExample(String fileName) throws IOException {
    requireNonNull(tempDir);

    // Given
    var compiler = ToolProvider.getSystemJavaCompiler();

    var diagnostics = new DiagnosticCollector<JavaFileObject>();

    var fileManager = compiler.getStandardFileManager(diagnostics, null, null);
    fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(tempDir.toFile()));
    fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(tempDir.toFile()));

    var files = new java.util.ArrayList<File>();
    files.add(new File("src/test/java/com/garciat/typeclasses/examples/" + fileName));

    var compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);

    var task =
        compiler.getTask(
            null,
            fileManager,
            diagnostics,
            List.of("-classpath", System.getProperty("java.class.path")),
            null,
            compilationUnits);
    task.setProcessors(List.of(new WitnessResolutionChecker()));

    // When
    boolean success = task.call();

    // Then
    assertThat(diagnostics.getDiagnostics()).isEmpty();
    assertThat(success).isTrue();
  }
}
