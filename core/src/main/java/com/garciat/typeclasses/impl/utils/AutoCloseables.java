package com.garciat.typeclasses.impl.utils;

public final class AutoCloseables {
  private AutoCloseables() {}

  public static SafeAutoCloseable around(Runnable before, Runnable after) {
    return new SafeAutoCloseable() {
      {
        before.run();
      }

      @Override
      public void close() {
        after.run();
      }
    };
  }

  public interface SafeAutoCloseable extends AutoCloseable {
    @Override
    void close();
  }
}
