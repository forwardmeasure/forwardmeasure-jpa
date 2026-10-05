/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  private record Frame(TenantId tenantId) {}

  private final ThreadLocal<Deque<Frame>> scopes = ThreadLocal.withInitial(ArrayDeque::new);

  @Override
  public Optional<TenantId> current() {
    Deque<Frame> stack = scopes.get();
    if (stack.isEmpty()) {
      scopes.remove();
      return Optional.empty();
    }
    return Optional.of(stack.peek().tenantId());
  }

  @Override
  public Scope open(TenantId tenantId) {
    Objects.requireNonNull(tenantId, "tenantId");
    Thread owner = Thread.currentThread();
    Deque<Frame> stack = scopes.get();
    Frame frame = new Frame(tenantId);
    stack.push(frame);
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
        Deque<Frame> current = scopes.get();
        if (current.isEmpty() || current.peek() != frame) {
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
