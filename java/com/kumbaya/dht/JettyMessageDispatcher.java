package com.kumbaya.dht;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.Address;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.limewire.mojito.Context;
import org.limewire.mojito.io.Tag;
import org.limewire.mojito.messages.DHTMessage;
import org.limewire.security.SecureMessage;
import org.limewire.security.SecureMessageCallback;

class JettyMessageDispatcher implements Dispatcher {
	private final Context context;
	private final HttpClient client = new HttpClient();
	private Server server;
	private HttpMessageDispatcher dispatcher;

	JettyMessageDispatcher(Context context) {
		this.context = context;
		client.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
	}
	
	public JettyMessageDispatcher setDispatcher(HttpMessageDispatcher dispatcher) {
		this.dispatcher = dispatcher;
		return this;
	}

	@Override
	public void bind(SocketAddress address) throws IOException {
		server = new Server(((InetSocketAddress) address).getPort());

		server.setHandler(new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest,
					HttpServletRequest request, HttpServletResponse response)
					throws IOException, ServletException {
				int length = request.getContentLength();
				byte[] data = new byte[length];
				DataInputStream dataIs = new DataInputStream(
						request.getInputStream());
				dataIs.readFully(data);

				String ip = request.getHeader("X-Node-IP");
				String port = request.getHeader("X-Node-Port");
				InetSocketAddress src = new InetSocketAddress(ip,
						Integer.valueOf(port)); 

				DHTMessage destination = context.getMessageFactory()
						.createMessage(src, ByteBuffer.wrap(data));

				// System.out.println("received:");
				// System.out.println(destination);

				dispatcher.handleMessage(destination);
			}
		});
					
		try {
			server.start();
			client.start();
		} catch (InterruptedException e) {
			throw new IOException(e);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public boolean submit(Tag tag) {
		// System.out.println("sending:");
		// System.out.println(tag);
		HttpExchange request = new HttpExchange() {
		    protected void onResponseComplete() throws IOException {
		        int status = getStatus();
		        if (status == 200) {
		        	
		        }
		    }
		};
		 
		// Optionally set the HTTP method
		request.setMethod("POST");
		
		InetSocketAddress ip = (InetSocketAddress) tag.getSocketAddress();
		request.setAddress(new Address(ip.getAddress().getHostAddress(),
				ip.getPort()));

		request.setURI("/");

		// TODO(goto): figure out what's the best way to do this.
		request.addRequestHeader("X-Node-IP",
				((InetSocketAddress) context.getContactAddress()).getAddress().getHostAddress());
		request.addRequestHeader("X-Node-Port",
				String.valueOf(((InetSocketAddress) context.getContactAddress()).getPort()));
		
		try {
			DHTMessage message = tag.getMessage();
			ByteBuffer data = context.getMessageFactory()
					.writeMessage(ip, message);
			
			request.setRequestContent(new ByteArrayBuffer(data.array()));

			client.send(request);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public void verify(SecureMessage secureMessage,
			SecureMessageCallback smc) {
		throw new UnsupportedOperationException(
				"Dispatcher doesn't support verifying secure messages");
	}
}
