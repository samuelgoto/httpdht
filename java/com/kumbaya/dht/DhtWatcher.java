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

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.limewire.mojito.EntityKey;
import org.limewire.mojito.db.DHTValueEntity;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Watches the DHT for changes and publishes them.
 *
 * @author Gustavo Moura (gmoura@google.com)
 */
@Singleton
public class DhtWatcher extends AbstractIdleService {
  private static final Log logger = LogFactory.getLog(AbstractIdleService.class);

  private static final long UPDATE_DELAY_SEC = 5;

  private final Provider<Map<EntityKey, DhtListener>> listenerProvider;
  private final Map<String, ImmutableSet<DHTValueEntity>> currentValues = new MapMaker()
      .makeComputingMap(Functions.constant(ImmutableSet.<DHTValueEntity>of()));
  private final Dht dht;
  private final ScheduledExecutorService executor;

  @Inject
  public DhtWatcher(Provider<Map<EntityKey, DhtListener>> listenerProvider, Dht dht,
      ScheduledExecutorService executor) {
    this.dht = dht;
    this.listenerProvider = listenerProvider;
    this.executor = executor;
  }

  @Override
  protected void startUp() throws Exception {
    executor.scheduleWithFixedDelay(new DhtChecker(),
        UPDATE_DELAY_SEC, UPDATE_DELAY_SEC, TimeUnit.SECONDS);
  }

  @Override
  protected void shutDown() throws Exception {
    executor.shutdown();
  }

  public void forceCheck() {
    new DhtChecker().run();
  }

  private class DhtChecker implements Runnable {
    @Override
    public void run() {
      for (EntityKey key : listenerProvider.get().keySet()) {
        try {
          ImmutableSet<DHTValueEntity> oldValues = currentValues.get(key);
          ImmutableSet<DHTValueEntity> newValues = ImmutableSet.copyOf(dht.get(key));
          if (!newValues.equals(oldValues)) {
            listenerProvider.get().get(key).onChange(
                Sets.difference(newValues, oldValues),
                Sets.difference(oldValues, newValues));
          }
        } catch (InterruptedException e) {
          logger.warn("DHT update failed", e);
        } catch (ExecutionException e) {
          logger.warn("DHT update failed", e);
        } catch (IOException e) {
          logger.warn("DHT update failed", e);
        }
      }
    }
  }
}
