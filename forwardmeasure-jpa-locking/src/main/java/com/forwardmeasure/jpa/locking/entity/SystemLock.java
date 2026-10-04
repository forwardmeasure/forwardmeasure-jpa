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
package com.forwardmeasure.jpa.locking.entity;

import com.forwardmeasure.jpa.core.entity.AbstractBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A named, transaction-scoped database mutex.
 *
 * <p>Rows are provisioned by application migrations. Applications must never create or delete lock
 * rows at runtime.
 */
@Entity
@Table(name = "system_lock")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemLock extends AbstractBaseEntity<String> {

  private static final long serialVersionUID = 1L;

  @Id
  @NotBlank
  @Column(name = "lock_name", nullable = false, length = 256)
  private String lockName;

  @Column(name = "description", length = 512)
  private String description;

  @Override
  public String getId() {
    return lockName;
  }

  @Override
  public void setId(String id) {
    lockName = id;
  }
}
