package com.kumbaya.dht;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.limewire.io.SimpleNetworkInstanceUtils;
import org.limewire.mojito.Context;
import org.limewire.mojito.KUID;
import org.limewire.mojito.MojitoDHT;
import org.limewire.mojito.MojitoFactory;
import org.limewire.mojito.StatusCode;
import org.limewire.mojito.concurrent.DHTFuture;
import org.limewire.mojito.db.DHTValue;
import org.limewire.mojito.io.MessageDispatcher;
import org.limewire.mojito.io.MessageDispatcherFactory;
import org.limewire.mojito.io.MessageInputStream;
import org.limewire.mojito.io.Tag;
import org.limewire.mojito.messages.DHTMessage;
import org.limewire.mojito.messages.FindNodeRequest;
import org.limewire.mojito.messages.FindNodeResponse;
import org.limewire.mojito.messages.PingRequest;
import org.limewire.mojito.messages.PingResponse;
import org.limewire.mojito.messages.StoreRequest;
import org.limewire.mojito.messages.StoreResponse;
import org.limewire.mojito.messages.impl.FindNodeResponseImpl;
import org.limewire.mojito.messages.impl.PingResponseImpl;
import org.limewire.mojito.messages.impl.StoreResponseImpl;
import org.limewire.mojito.result.BootstrapResult;
import org.limewire.mojito.result.BootstrapResult.ResultType;
import org.limewire.mojito.result.StoreResult;
import org.limewire.mojito.routing.Contact;
import org.limewire.mojito.routing.Contact.State;
import org.limewire.mojito.routing.Vendor;
import org.limewire.mojito.routing.Version;
import org.limewire.mojito.routing.impl.RemoteContact;
import org.limewire.mojito.settings.NetworkSettings;
import org.limewire.mojito.util.ContactUtils;
import org.limewire.security.SecureMessage;
import org.limewire.security.SecureMessageCallback;

import com.google.common.collect.ImmutableSet;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MessageDispatcherTest {
	private IMocksControl control = EasyMock.createControl();
	MessageDispatcherFactory messageFactory = control.createMock(
			MessageDispatcherFactory.class);
	Dispatcher messageDispatcher = control.createMock(
			Dispatcher.class);

	@Before
	public void setUp() {
		control.reset();
		NetworkSettings.LOCAL_IS_PRIVATE.setValue(false);
		NetworkSettings.FILTER_CLASS_C.setValue(false);
		ContactUtils.setNetworkInstanceUtils(new SimpleNetworkInstanceUtils(false));
	}
	
	interface Dispatcher {
		public void bind(SocketAddress address) throws IOException;
		public boolean isBound();
		boolean submit(Tag tag);
		void verify(SecureMessage secureMessage, SecureMessageCallback smc);
	}

	static class HttpMessageDispatcher extends MessageDispatcher {
		private boolean started = false;
		private final Dispatcher dispatcher;

		public HttpMessageDispatcher(Context context, Dispatcher dispatcher) {
			super(context);
			this.dispatcher = dispatcher;
		}
		
		@Override
	    public void handleMessage(DHTMessage message) {
			super.handleMessage(message);
		}

		@Override
		public void bind(SocketAddress address) throws IOException {
			dispatcher.bind(address);
		}

		@Override
		public boolean isBound() {
			return dispatcher.isBound();
		}

		@Override
		public boolean isRunning() {
			return started;
		}

		@Override
		protected boolean submit(Tag tag) {
			register(tag);
			return dispatcher.submit(tag);
		}

		@Override
		public void start() {
			super.start();
			started = true;
		}

		@Override
		protected void process(Runnable runnable) {
			runnable.run();
		}

		@Override
		protected void verify(SecureMessage secureMessage,
				SecureMessageCallback smc) {
			dispatcher.verify(secureMessage, smc);
		}
	}
	
	private HttpMessageDispatcher messageFactory(MojitoDHT node) {
	    HttpMessageDispatcher httpMessageDispatcher = new HttpMessageDispatcher(
	    		(Context) node, messageDispatcher);
	    expect(messageFactory.create(isA(Context.class))).andReturn(
	    		httpMessageDispatcher);
	    return httpMessageDispatcher;
	}

	@Test
	public void testBootstrapingANodeAndStoringAValue() throws Exception {
	    MojitoDHT node = MojitoFactory.createDHT("local test node");

	    HttpMessageDispatcher httpMessageDispatcher = messageFactory(
	    		node);
		
		expect(messageDispatcher.isBound()).andReturn(false);
		messageDispatcher.bind(isA(SocketAddress.class));
		expect(messageDispatcher.isBound()).andReturn(true);
		
		// While bootstraping, the node pings and sends a find node request
		// to the bootstrap node.
		Capture<Tag> pingRequestTag = new Capture<Tag>();
		expect(messageDispatcher.submit(capture(pingRequestTag))).andReturn(true);
		
		Capture<Tag> findNodeRequestTag = new Capture<Tag>();
		expect(messageDispatcher.submit(capture(findNodeRequestTag))).andReturn(true);
		
		// While storing a result, the node sends a find node request,
		// to which the bootstrap node replies.
		Capture<Tag> findNodeRequestTag2 = new Capture<Tag>();
		expect(messageDispatcher.submit(capture(findNodeRequestTag2))).andReturn(true);
		
		Capture<Tag> storeRequestTag = new Capture<Tag>();
		expect(messageDispatcher.submit(capture(storeRequestTag))).andReturn(true);
		
		control.replay();
		
	    node.setMessageDispatcher(messageFactory);
	    InetSocketAddress local = new InetSocketAddress("localhost", 8081);
	    node.bind(local);
	    node.start();
	    InetSocketAddress bootstrap = new InetSocketAddress("localhost", 8080);
	    DHTFuture<BootstrapResult> result = node.bootstrap(bootstrap);
	    
	    // Allows the message executor to catch up.
	    Thread.sleep(200);

	    PingRequest pingRequest = (PingRequest) pingRequestTag.getValue().getMessage();

	    Contact contact = new RemoteContact(
	    		bootstrap,
	    		Vendor.UNKNOWN, Version.ZERO, 
	            KUID.createRandomID(),
	            bootstrap, 
	            1, 0, State.ALIVE);
	    
	    PingResponse pingResponse = new PingResponseImpl(
	    		(Context) node, contact, pingRequest.getMessageID(), 
	    		local, 
	    		BigInteger.ONE);

	    httpMessageDispatcher.handleMessage(pingResponse);
	    
	    Thread.sleep(200);
	    
	    FindNodeRequest findNodeRequest = (FindNodeRequest) findNodeRequestTag.getValue().getMessage();
	    
	    FindNodeResponse findNodeResponse = new FindNodeResponseImpl(
	    		(Context) node, contact, findNodeRequest.getMessageID(), 
	    		null, Collections.<Contact>emptySet());

	    httpMessageDispatcher.handleMessage(findNodeResponse);
	    
	    Thread.sleep(200);
	    
	    assertEquals(ResultType.BOOTSTRAP_SUCCEEDED, result.get().getResultType());
	    
	    assertTrue(node.isBootstrapped());
	    
	    DHTFuture<StoreResult> storeResult = node.put(
	    		Keys.of("foo"), Values.of("bar"));
	    
	    Thread.sleep(200);
	    
	    FindNodeRequest findNodeRequest2 = (FindNodeRequest) findNodeRequestTag2.getValue().getMessage();
	    
	    FindNodeResponse findNodeResponse2 = new FindNodeResponseImpl(
	    		(Context) node, contact, findNodeRequest2.getMessageID(), 
	    		null, Collections.<Contact>emptySet());

	    Thread.sleep(200);

	    httpMessageDispatcher.handleMessage(findNodeResponse2);

	    StoreRequest storeRequest = (StoreRequest) storeRequestTag.getValue().getMessage();

	    StoreResponse storeResponse = new StoreResponseImpl(
	    		(Context) node, contact, storeRequest.getMessageID(),
	    		ImmutableSet.of(new StoreResponse.StoreStatusCode(
	    				Keys.of("foo"), ((Context) node).getLocalNodeID(), 
	    				StoreResponse.OK)));
	    
	    httpMessageDispatcher.handleMessage(storeResponse);
	    
	    assertEquals(1, storeResult.get().getValues().size());
	    assertEquals(Values.of("bar"),
	    		storeResult.get().getValues().iterator().next().getValue());
	}
}
