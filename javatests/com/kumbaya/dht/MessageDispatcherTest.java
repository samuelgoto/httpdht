package com.kumbaya.dht;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.limewire.io.SimpleNetworkInstanceUtils;
import org.limewire.mojito.Context;
import org.limewire.mojito.MojitoDHT;
import org.limewire.mojito.MojitoFactory;
import org.limewire.mojito.io.MessageDispatcher;
import org.limewire.mojito.io.MessageDispatcher.MessageDispatcherListener;
import org.limewire.mojito.io.MessageDispatcherFactory;
import org.limewire.mojito.messages.MessageFactory;
import org.limewire.mojito.settings.NetworkSettings;
import org.limewire.mojito.util.ContactUtils;

import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;

public class MessageDispatcherTest {
	private IMocksControl control = EasyMock.createControl();
	MessageDispatcherFactory messageFactory = control.createMock(
			MessageDispatcherFactory.class);
	MessageDispatcher messageDispatcher = control.createMock(
			MessageDispatcher.class);

	@Before
	public void setUp() {
		control.reset();
		NetworkSettings.LOCAL_IS_PRIVATE.setValue(false);
		NetworkSettings.FILTER_CLASS_C.setValue(false);
		ContactUtils.setNetworkInstanceUtils(new SimpleNetworkInstanceUtils(false));
	}

	@Test
	public void testSettingCustomMessageDispatcher() throws Exception {
		expect(messageFactory.create(isA(Context.class))).andReturn(messageDispatcher);
		
		messageDispatcher.addMessageDispatcherListener(
				isA(MessageDispatcherListener.class));
		
		// TODO(goto): switch these to an HTTP implementation.
		expect(messageDispatcher.isBound()).andReturn(false);
		messageDispatcher.bind(isA(SocketAddress.class));
		expect(messageDispatcher.isBound()).andReturn(true);
		expect(messageDispatcher.isRunning()).andReturn(true).anyTimes();
		messageDispatcher.stop();
		messageDispatcher.close();
		
		control.replay();

	    MojitoDHT dht = MojitoFactory.createDHT("bootstrap");
	    dht.setMessageDispatcher(messageFactory);
	    dht.bind(new InetSocketAddress("localhost", 8080));
	    dht.start();
	    dht.close();
	}
}
