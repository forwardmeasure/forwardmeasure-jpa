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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The platform's own DID method and the DIDs it mints. The method name is configuration (shared
 * platform value {@code platform.identity.didMethod}, delivered as {@value #METHOD_ENV}), never a
 * literal in code, so it can be renamed in one place.
 *
 * <ul>
 *   <li>{@code did:<method>:bundle:<domain>} - a product's workflow bundle; its resources are
 *       addressed as DID URLs ({@link DidUrl}) under it.
 *   <li>{@code did:<method>:entity:<tenantId>:<subjectUuid>} - a resolved entity.
 * </ul>
 *
 * <p>Workflow definitions are data, so they can't read configuration: they write {@value
 * #BUNDLE_DID_TOKEN} where their own bundle's DID belongs ({@code @BUNDLE_DID@/asyncapi.yaml}), and
 * the bundle install renders it with {@link #renderBundleDid}. Neither the method nor the domain is
 * ever written in a definition.
 */
public record PlatformDids(String method) {
  public static final String METHOD_ENV = "FORWARDMEASURE_DID_METHOD";
  public static final String BUNDLE_DID_TOKEN = "@BUNDLE_DID@";
  private static final Pattern METHOD = Pattern.compile("^[a-z0-9]+$");
  private static final Pattern SEGMENT = Pattern.compile("^[A-Za-z0-9._-]+$");
  private static final String BUNDLE = "bundle";
  private static final String ENTITY = "entity";

  public PlatformDids {
    Objects.requireNonNull(method, "method");
    if (!METHOD.matcher(method).matches()) {
      throw new IllegalArgumentException("Not a DID method name: " + method);
    }
  }

  /**
   * The method from {@value #METHOD_ENV}; fails when it is unset, never falls back to a default.
   */
  public static PlatformDids fromEnvironment() {
    String configured = System.getenv(METHOD_ENV);
    if (configured == null || configured.isBlank()) {
      throw new IllegalStateException(METHOD_ENV + " is not set");
    }
    return new PlatformDids(configured.trim());
  }

  public Did bundle(String domain) {
    return Did.parse("did:" + method + ":" + BUNDLE + ":" + segment(domain, "domain"));
  }

  public DidUrl bundleResource(String domain, String path) {
    return new DidUrl(bundle(domain), path);
  }

  public Did entity(UUID tenantId, UUID subjectUuid) {
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(subjectUuid, "subjectUuid");
    return Did.parse("did:" + method + ":" + ENTITY + ":" + tenantId + ":" + subjectUuid);
  }

  /** {@code text} with every {@value #BUNDLE_DID_TOKEN} replaced by {@code domain}'s bundle DID. */
  public String renderBundleDid(String text, String domain) {
    Objects.requireNonNull(text, "text");
    return text.replace(BUNDLE_DID_TOKEN, bundle(domain).value());
  }

  /** The bundle's domain when {@code did} is one of this platform's bundle DIDs. */
  public Optional<String> bundleDomain(Did did) {
    Objects.requireNonNull(did, "did");
    String prefix = BUNDLE + ":";
    String id = did.methodSpecificId();
    if (!method.equals(did.method()) || !id.startsWith(prefix)) {
      return Optional.empty();
    }
    String domain = id.substring(prefix.length());
    return SEGMENT.matcher(domain).matches() ? Optional.of(domain) : Optional.empty();
  }

  private static String segment(String value, String name) {
    Objects.requireNonNull(value, name);
    if (!SEGMENT.matcher(value).matches()) {
      throw new IllegalArgumentException("Not a valid " + name + " for a DID: " + value);
    }
    return value;
  }
}
