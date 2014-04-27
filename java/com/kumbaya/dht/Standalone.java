package com.kumbaya.dht;

import java.net.InetSocketAddress;

import org.limewire.mojito.Context;
import org.limewire.mojito.MojitoFactory;

import com.kumbaya.dht.MessageDispatcherTest.MessageDispatcherFactoryImpl;

public class Standalone {
	public static void main(String[] args) throws Exception {
		int port = System.getenv().containsKey("PORT") ? 
				Integer.valueOf(System.getenv("PORT")) :
				8080;
		
		Context dht = (Context) MojitoFactory.createDHT("bootstrap");
		dht.setMessageDispatcher(new MessageDispatcherFactoryImpl());
		dht.bind(new InetSocketAddress("localhost", port));
		dht.start();
	}
}
