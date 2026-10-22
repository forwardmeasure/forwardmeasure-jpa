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
package com.forwardmeasure.jpa.identity.entity;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An actor's DID, synthesised on the fly from its identity - never stored. An actor is identified
 * by (identity provider, subject); its DID is {@code did:web:<domain>:actors:<provider>:<subject>},
 * where {@code <domain>} is the platform's domain (the domain tenant DIDs are issued under). This
 * is how APIs name an owner or an acting actor: never an internal id. The DID is reversible, so an
 * API can accept one and recover the identity to look the actor up.
 *
 * <p>Each segment is percent-encoded outside {@code [A-Za-z0-9._-]}, as the did:web method-specific
 * id requires (so a domain's port separator becomes {@code %3A}).
 */
public final class ActorDids {

  private static final String ACTORS = "actors";
  private static final Pattern UNRESERVED = Pattern.compile("[A-Za-z0-9._-]");

  private ActorDids() {}

  /** An actor's identity as a DID carries it. */
  public record ActorIdentity(String identityProvider, String subjectIdentifier) {
    public ActorIdentity {
      requireText(identityProvider, "identityProvider");
      requireText(subjectIdentifier, "subjectIdentifier");
    }
  }

  /** The DID of the actor with this identity under the platform domain. */
  public static String of(String domain, String identityProvider, String subjectIdentifier) {
    requireText(domain, "domain");
    requireText(identityProvider, "identityProvider");
    requireText(subjectIdentifier, "subjectIdentifier");
    return "did:web:"
        + encode(domain)
        + ":"
        + ACTORS
        + ":"
        + encode(identityProvider)
        + ":"
        + encode(subjectIdentifier);
  }

  /** The DID of this actor under the platform domain. */
  public static String of(String domain, Actor actor) {
    Objects.requireNonNull(actor, "actor");
    return of(domain, actor.getIdentityProvider(), actor.getSubjectIdentifier());
  }

  /**
   * Recovers the identity from an actor DID issued under this domain.
   *
   * @throws IllegalArgumentException if it is not an actor DID under this domain
   */
  public static ActorIdentity parse(String domain, String did) {
    requireText(domain, "domain");
    requireText(did, "did");
    String prefix = "did:web:" + encode(domain) + ":" + ACTORS + ":";
    if (!did.startsWith(prefix)) {
      throw new IllegalArgumentException("Not an actor DID under " + domain + ": " + did);
    }
    String[] parts = did.substring(prefix.length()).split(":", -1);
    if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
      throw new IllegalArgumentException("Not an actor DID under " + domain + ": " + did);
    }
    return new ActorIdentity(decode(parts[0]), decode(parts[1]));
  }

  private static String encode(String value) {
    StringBuilder encoded = new StringBuilder();
    for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
      char c = (char) (b & 0xFF);
      if (b >= 0 && UNRESERVED.matcher(String.valueOf(c)).matches()) {
        encoded.append(c);
      } else {
        encoded.append('%').append(String.format("%02X", b & 0xFF));
      }
    }
    return encoded.toString();
  }

  private static String decode(String value) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '%') {
        if (i + 2 >= value.length()) {
          throw new IllegalArgumentException("Truncated percent-encoding in " + value);
        }
        bytes.write(Integer.parseInt(value.substring(i + 1, i + 3), 16));
        i += 2;
      } else if (UNRESERVED.matcher(String.valueOf(c)).matches()) {
        bytes.write(c);
      } else {
        throw new IllegalArgumentException("Invalid character in actor DID segment: " + value);
      }
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
  }
}
