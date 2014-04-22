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

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.limewire.io.SimpleNetworkInstanceUtils;
import org.limewire.mojito.EntityKey;
import org.limewire.mojito.KUID;
import org.limewire.mojito.MojitoDHT;
import org.limewire.mojito.MojitoFactory;
import org.limewire.mojito.db.DHTValue;
import org.limewire.mojito.db.DHTValueEntity;
import org.limewire.mojito.db.DHTValueType;
import org.limewire.mojito.db.Storable;
import org.limewire.mojito.db.StorableModel;
import org.limewire.mojito.result.FindValueResult;
import org.limewire.mojito.result.StoreResult;
import org.limewire.mojito.util.ContactUtils;
import org.limewire.mojito.util.DatabaseUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Singleton
public class Dht {
  private final MojitoDHT node = MojitoFactory.createDHT("bootstrap");
  @Inject private MojitoDHT dht;
  @Inject private Model model;
  @Inject private int port = 8080;
  @Inject private String bootstrap = "";

  public StoreResult put(KUID key, DHTValue value) throws InterruptedException, ExecutionException {
    return put(DHTValueEntity.createFromValue(dht, key, value));
  }

  public List<DHTValueEntity> get(KUID key, DHTValueType keyType)
      throws InterruptedException, ExecutionException {
    return get(Keys.as(key, keyType));
  }

  public List<DHTValueEntity> get(EntityKey entityKey)
      throws InterruptedException, ExecutionException {
    List<DHTValueEntity> all = new ArrayList<DHTValueEntity>();
    FindValueResult result = dht.get(entityKey).get();

    for (DHTValueEntity entry : result.getEntities()) {
      all.add(entry);
    }

    for (EntityKey entry : result.getEntityKeys()) {
      FindValueResult entries = dht.get(entry).get();
      for (DHTValueEntity entity : entries.getEntities()) {
        all.add(entity);
      }
    }

    return all;
  }

  public void remove(KUID key) throws InterruptedException, ExecutionException {
    dht.remove(key).get();
  }

  private StoreResult put(DHTValueEntity value)
      throws InterruptedException, ExecutionException {
    model.add(value);
    StoreResult result = dht.put(value.getPrimaryKey(), value.getValue()).get();
    return result;
  }

  public Dht start() throws IOException, NumberFormatException, InterruptedException, ExecutionException {
    // The following lines allows the DHT to connect to local ip addresses
    // which is sometimes useful for debugging purposes. Production binaries
    // should probably not set these flags though. Leaving them as comments
    // for convenience.
    // NetworkSettings.LOCAL_IS_PRIVATE.setValue(false);
    // NetworkSettings.FILTER_CLASS_C.setValue(false);
    ContactUtils.setNetworkInstanceUtils(new SimpleNetworkInstanceUtils(false));

    if ("".equals(bootstrap)) {
      node.bind(new InetSocketAddress(port - 1));
      node.start();
      bootstrap = "localhost:" + (Integer.toString(port - 1));
    }

    dht.getStorableModelManager().addStorableModel(
        DHTValueType.TEXT, model);

    dht.bind(new InetSocketAddress(port));
    dht.start();
    String[] addr = bootstrap.split(":");
    dht.bootstrap(new InetSocketAddress(addr[0], Integer.parseInt(addr[1]))).get();

    return this;
  }
  
  public boolean bootstraped() {
    return dht.isBootstrapped();
  }

  public Dht stop() {
    node.close();
    dht.close();
    return this;
  }

  public static class Model implements StorableModel {
    private final Set<DHTValueEntity> values = Sets.newHashSet();

    public Model add(DHTValueEntity value) {
      values.add(value);
      return this;
    }

    @Override
    public Collection<Storable> getStorables() {
      Set<Storable> result = Sets.newHashSet();

      for (DHTValueEntity value : values) {
        Storable storable = new Storable(value.getPrimaryKey(), value.getValue());
        if (DatabaseUtils.isPublishingRequired(storable)) {
          result.add(storable);
        }
      }

      return result;
    }

    @Override
    public void handleStoreResult(Storable value, StoreResult result) {
    }

    @Override
    public void handleContactChange() {
    }
  }
}
