/*
 * Copyright 2016-present mklinger GmbH - http://www.mklinger.de
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mklinger.jgroups.http;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import org.eclipse.jetty.servlet.ServletHolder;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.jgroups.http.server.JGroupsServlet;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
public class HttpClusterIT {
	private static final Logger LOG = LoggerFactory.getLogger(HttpClusterIT.class);

	@Test
	public void test() throws Exception {
		final Set<Object> received = Collections.synchronizedSet(new HashSet<>());

		try (final JettyHttpServerImpl server1 = new JettyHttpServerImpl("localhost", FreePort.get(8443), 100)) {

			final JChannel channel1;

			try (final JettyHttpServerImpl server2 = new JettyHttpServerImpl("localhost", FreePort.get(8444), 100)) {

				initServlet(server1, server2);
				server1.start();
				channel1 = getChannel(server1);
				channel1.setReceiver(message -> received.add(message.getObject()));

				waitForViewSize(channel1, 1);

				initServlet(server2, server1);
				server2.start();
				final JChannel channel2 = getChannel(server2);
				channel2.setReceiver(message -> received.add(message.getObject()));

				waitForViewSize(channel1, 2);
				waitForViewSize(channel2, 2);

				channel1.send(new Message(null, "message from channel1"));
				channel2.send(new Message(null, "message from channel2"));

				waitFor(() -> received.contains("message from channel1"), "message from channel1");
				waitFor(() -> received.contains("message from channel2"), "message from channel2");
			}

			waitForViewSize(channel1, 1);
		}
	}

	private static void initServlet(final JettyHttpServerImpl server, final JettyHttpServerImpl otherServer) {
		final ServletHolder servletHolder = server.getServletHandler().addServlet(JGroupsServlet.class, "/jgroups");
		servletHolder.setInitOrder(1);
		servletHolder.setInitParameter("protocol.mklinger.HTTP.external_addr", server.getHttpsBindAddress().getHostString());
		servletHolder.setInitParameter("protocol.mklinger.HTTP.external_port", String.valueOf(server.getHttpsBindAddress().getPort()));
		servletHolder.setInitParameter("protocol.mklinger.HTTP.client_props",
				"ssl.trust-store=" + HttpClusterIT.class.getResource("ca-cert.p12").toExternalForm());

		final InetSocketAddress otherServerAddress = otherServer.getHttpsBindAddress();
		servletHolder.setInitParameter("protocol.mklinger.HTTPPING.initial_ping_addresses",
				otherServerAddress.getHostString() + ":" + otherServerAddress.getPort());
	}

	private static JChannel getChannel(final JettyHttpServerImpl server1) {
		return (JChannel) server1.getServletContext().getAttribute(JGroupsServlet.CHANNEL_ATTRIBUTE);
	}

	private static void waitForViewSize(final JChannel channel, final int size) throws InterruptedException, TimeoutException {
		waitFor(() -> channel.getView() != null
				&& channel.getView().getMembers().size() == size,
				"view size " + size);
	}

	private static void waitFor(BooleanSupplier predicate, String description) throws InterruptedException, TimeoutException {
		LOG.info("Waiting for {}", description);

		final long timeoutMillis = TimeUnit.SECONDS.toMillis(30);
		final long startTimeMillis = System.currentTimeMillis();

		while (!predicate.getAsBoolean()) {
			final long nowMillis = System.currentTimeMillis();
			if (nowMillis - startTimeMillis > timeoutMillis) {
				throw new TimeoutException("Timeout waiting for " + description);
			}
			Thread.sleep(1);
		}

		LOG.info("Have {}", description);
	}
}
