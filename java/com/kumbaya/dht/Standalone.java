package com.kumbaya.dht;

import java.net.InetSocketAddress;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.limewire.io.SimpleNetworkInstanceUtils;
import org.limewire.mojito.Context;
import org.limewire.mojito.MojitoFactory;
import org.limewire.mojito.settings.NetworkSettings;
import org.limewire.mojito.util.ContactUtils;

import com.kumbaya.dht.JettyMessageDispatcher.InternalInetSocketAddress;
import com.kumbaya.dht.MessageDispatcherTest.MessageDispatcherFactoryImpl;

public class Standalone {
	public static void main(String[] args) throws Exception {
		NetworkSettings.LOCAL_IS_PRIVATE.setValue(false);
		NetworkSettings.FILTER_CLASS_C.setValue(false);
		ContactUtils.setNetworkInstanceUtils(new SimpleNetworkInstanceUtils(false));
		
		Options options = new Options();
		options.addOption("port", true, "The external port");
		options.addOption("proxy", true, "The proxy port");
		options.addOption("bootstrap", true, "The node to bootstrap");

		CommandLineParser parser = new PosixParser();

		CommandLine line = parser.parse(options, args);

		line.getOptionValue("port");

		final int port;
		if (System.getenv().containsKey("PORT")) {
			port = Integer.valueOf(System.getenv("PORT"));
		} else if (line.hasOption("port")) {
			port = Integer.valueOf(line.getOptionValue("port"));
		} else {
			port = 8080;
		}
		
		final int proxy;
		if (line.hasOption("proxy")) {
			proxy = Integer.valueOf(line.getOptionValue("proxy"));
		} else {
			proxy = port;
		}

		Context dht = (Context) MojitoFactory.createDHT("bootstrap");
		dht.setMessageDispatcher(new MessageDispatcherFactoryImpl());
		dht.bind(new InternalInetSocketAddress("localhost", port, proxy));
		dht.start();

		if (line.hasOption("bootstrap")) {
			String[] ip = line.getOptionValue("bootstrap").split(":");
			dht.bootstrap(new InetSocketAddress(ip[0],
					Integer.parseInt(ip[1])));
		}
	}
}
