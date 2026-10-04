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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A W3C DID Core 1.1 DID URL limited to a DID and an absolute path ({@code did:fwm:bundle:fei/
 * asyncapi/ingestion-worker.yaml}): the path names a resource the DID's subject holds. Query and
 * fragment are not supported.
 *
 * <p>{@link java.net.URI} treats a DID URL as opaque, so {@code URI.resolve} silently ignores
 * relative references against it; {@link #resolve(String)} applies RFC 3986 reference resolution to
 * the path instead.
 */
public record DidUrl(Did did, String path) {
  private static final Pattern PATH =
      Pattern.compile("^(/(?:[A-Za-z0-9._~!$&'()*+,;=:@-]|%[0-9A-Fa-f]{2})*)+$");

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static DidUrl parse(String value) {
    Objects.requireNonNull(value, "value");
    int slash = value.indexOf('/');
    if (slash < 0) {
      throw new IllegalArgumentException("A DID URL must carry a path: " + value);
    }
    return new DidUrl(Did.parse(value.substring(0, slash)), value.substring(slash));
  }

  public DidUrl {
    Objects.requireNonNull(did, "did");
    Objects.requireNonNull(path, "path");
    if (!PATH.matcher(path).matches() || path.contains("?") || path.contains("#")) {
      throw new IllegalArgumentException("Not a DID URL path: " + path);
    }
    for (String segment : path.substring(1).split("/", -1)) {
      if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
        throw new IllegalArgumentException("DID URL path must be normalized: " + path);
      }
    }
  }

  /** True for text that starts with {@code did:} - the cheap test before {@link #parse}. */
  public static boolean isDidUrl(String value) {
    return value != null && value.startsWith("did:");
  }

  /**
   * Resolves a reference found inside the resource this DID URL names, as RFC 3986 does for a
   * hierarchical URI: an absolute DID URL is returned as is, a path starting with {@code /}
   * replaces this path, anything else replaces this path's last segment. Dot segments are removed;
   * climbing above the root is rejected rather than clamped.
   */
  public DidUrl resolve(String reference) {
    Objects.requireNonNull(reference, "reference");
    if (isDidUrl(reference)) {
      return parse(reference);
    }
    if (reference.contains(":") && reference.indexOf(':') < firstOf(reference, '/')) {
      throw new IllegalArgumentException(
          "A reference inside a DID-addressed resource must be relative or a DID URL: "
              + reference);
    }
    if (reference.contains("?") || reference.contains("#")) {
      throw new IllegalArgumentException("Query and fragment are not supported: " + reference);
    }
    String merged =
        reference.startsWith("/")
            ? reference
            : path.substring(0, path.lastIndexOf('/') + 1) + reference;
    Deque<String> segments = new ArrayDeque<>();
    for (String segment : merged.substring(1).split("/", -1)) {
      if (".".equals(segment) || segment.isEmpty()) {
        continue;
      }
      if ("..".equals(segment)) {
        if (segments.isEmpty()) {
          throw new IllegalArgumentException("Reference climbs above " + did + ": " + reference);
        }
        segments.removeLast();
      } else {
        segments.addLast(segment);
      }
    }
    if (segments.isEmpty()) {
      throw new IllegalArgumentException("Reference resolves to no resource: " + reference);
    }
    return new DidUrl(did, "/" + String.join("/", segments));
  }

  private static int firstOf(String value, char character) {
    int index = value.indexOf(character);
    return index < 0 ? Integer.MAX_VALUE : index;
  }

  @JsonValue
  @Override
  public String toString() {
    return did.value() + path;
  }
}
