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

/**
 * The one platform-owned schema inside every {@link TenantDatabase} that holds the tenant's actors:
 * each person or service account with access to the tenant, once, shared by every product.
 *
 * <p>Unlike a {@link FunctionalSchema}, it belongs to no product. It is created and migrated before
 * any product's own schema, and products reference {@code identity.actor} by foreign key from their
 * own schemas (a cross-schema foreign key inside one PostgreSQL database). Products' runtime roles
 * may only read it; only tenant-access provisioning writes it.
 */
public final class IdentitySchema {

  /** The schema name; a compile-time constant so JPA mappings can use it in annotations. */
  public static final String NAME = "identity";

  private IdentitySchema() {}
}
