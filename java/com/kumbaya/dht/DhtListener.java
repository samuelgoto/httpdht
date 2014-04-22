/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kumbaya.dht;

import org.limewire.mojito.db.DHTValueEntity;

import java.io.IOException;
import java.util.Set;

/**
 * Listener for DHT changes on a given key.
 *
 * @author Gustavo Moura (gmoura@google.com)
 */
public interface DhtListener {

  /**
   * Invoked when there's a change to the values for the DHT key this listener is registered on.
   *
   * @param newValues the new values added on this change.
   * @param deletedValues the old values removed in this change.
   */
  void onChange(Set<DHTValueEntity> newValues, Set<DHTValueEntity> deletedValues)
      throws IOException;
}
