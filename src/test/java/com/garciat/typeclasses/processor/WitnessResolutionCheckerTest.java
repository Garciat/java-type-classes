package com.garciat.typeclasses.processor;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.tools.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WitnessResolutionCheckerTest {
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
            List.of("-classpath", System.getProperty("java.class.path")),
            null,
            compilationUnits);
    task.setProcessors(List.of(new WitnessResolutionChecker()));

    // When
    boolean success = task.call();

    // Then
    var unexpectedDiagnostics =
        diagnostics.getDiagnostics().stream()
            .filter(
                d ->
                    !(d.getKind() == Diagnostic.Kind.WARNING
                        && d.getMessage(null)
                            .contains("Could not register TaskListener for AST rewriting")))
            .toList();
    assertThat(unexpectedDiagnostics).isEmpty();
    assertThat(success).isTrue();
  }
}
