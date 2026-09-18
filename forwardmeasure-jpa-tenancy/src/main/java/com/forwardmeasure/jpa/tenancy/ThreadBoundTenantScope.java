package com.forwardmeasure.jpa.tenancy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit, nestable tenant scope for request and message-consumer adapters. Closing a scope is
 * mandatory and removes the ThreadLocal when the stack becomes empty, preventing pooled-thread
 * tenant leakage.
 */
public final class ThreadBoundTenantScope implements TenantScope {

  private final ThreadLocal<Deque<TenantDatabase>> scopes =
      ThreadLocal.withInitial(ArrayDeque::new);

  @Override
  public Optional<TenantDatabase> current() {
    Deque<TenantDatabase> stack = scopes.get();
    if (stack.isEmpty()) {
      scopes.remove();
      return Optional.empty();
    }
    return Optional.of(stack.peek());
  }

  @Override
  public Scope open(TenantDatabase database) {
    Objects.requireNonNull(database, "database");
    Thread owner = Thread.currentThread();
    Deque<TenantDatabase> stack = scopes.get();
    stack.push(database);
    return new Scope() {
      private boolean closed;

      @Override
      public void close() {
        if (closed) {
          return;
        }
        if (Thread.currentThread() != owner) {
          throw new IllegalStateException(
              "Tenant scope must be closed on the thread that opened it");
        }
        Deque<TenantDatabase> current = scopes.get();
        if (current.isEmpty() || !database.equals(current.peek())) {
          throw new IllegalStateException("Tenant scopes must be closed in reverse order");
        }
        current.pop();
        if (current.isEmpty()) {
          scopes.remove();
        }
        closed = true;
      }
    };
  }
}
