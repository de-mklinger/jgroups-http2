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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.servlet.ServletHolder;
import org.jgroups.JChannel;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.jgroups.http.server.JGroupsServlet;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
@Ignore("To be extended by a real test class")
public abstract class HttpClusterITBase {
	private static final Logger LOG = LoggerFactory.getLogger(HttpClusterITBase.class);

	protected static void testWithClient(final String clientClassName) throws InterruptedException, TimeoutException {
		try (final JettyHttpServerImpl server1 = new JettyHttpServerImpl("127.0.0.1", 8443, 100)) {

			try (final JettyHttpServerImpl server2 = new JettyHttpServerImpl("127.0.0.1", 8444, 100)) {

				initServlet(server1, server2, clientClassName);
				server1.start();

				waitForViewSize(server1, 1);

				initServlet(server2, server1, clientClassName);
				server2.start();

				waitForViewSize(server1, 2);
				waitForViewSize(server2, 2);
			}

			waitForViewSize(server1, 1);
		}
	}

	private static void initServlet(final JettyHttpServerImpl server, final JettyHttpServerImpl otherServer, final String clientClassName) {
		final ServletHolder servletHolder = server.getServletHandler().addServlet(JGroupsServlet.class, "/jgroups");
		servletHolder.setInitOrder(1);
		servletHolder.setInitParameter("protocol.mklinger.HTTP.external_addr", server.getHttpsBindAddress().getHostString());
		servletHolder.setInitParameter("protocol.mklinger.HTTP.external_port", String.valueOf(server.getHttpsBindAddress().getPort()));
		servletHolder.setInitParameter("protocol.mklinger.HTTP.client_props",
				"class-name=" + clientClassName + "," +
						"ssl.trust-store=" + HttpClusterITBase.class.getResource("test-keystore.jks").toExternalForm());

		final InetSocketAddress otherServerAddress = otherServer.getHttpsBindAddress();
		servletHolder.setInitParameter("protocol.TCPPING.initial_hosts",
				otherServerAddress.getHostString() + "[" + otherServerAddress.getPort() + "]");
		servletHolder.setInitParameter("protocol.TCPPING.port_range", "0");
	}

	private static JChannel getChannel(final JettyHttpServerImpl server) {
		return (JChannel) server.getServletContext().getAttribute(JGroupsServlet.CHANNEL_ATTRIBUTE);
	}

	private static Optional<Throwable> getError(final JettyHttpServerImpl server) {
		return Optional.ofNullable((Throwable) server.getServletContext().getAttribute(JGroupsServlet.ERROR_ATTRIBUTE));
	}

	private static void waitForViewSize(final JettyHttpServerImpl server, final int size) throws InterruptedException, TimeoutException {
		final JChannel channel = getChannel(server);
		LOG.info("##### Waiting for view size: {}", size);
		final AtomicInteger viewSize;
		final View view = channel.getView();
		if (view != null) {
			viewSize = new AtomicInteger(view.getMembers().size());
		} else {
			viewSize = new AtomicInteger(0);
		}
		channel.setReceiver(new ReceiverAdapter() {
			@Override
			public void viewAccepted(final View view) {
				LOG.info("##### VIEW: {}", view);
				viewSize.set(view.getMembers().size());
			}
		});
		final long timeoutMillis = TimeUnit.SECONDS.toMillis(30);
		final long startTimeMillis = System.currentTimeMillis();
		while (viewSize.get() != size) {
			getError(server).ifPresent(e -> {
				throw new AssertionError("JGroups servlet error", e);
			});
			final long nowMillis = System.currentTimeMillis();
			if (nowMillis - startTimeMillis > timeoutMillis) {
				throw new TimeoutException("Timeout waiting for view size " + size);
			}
			Thread.sleep(1);
		}
		LOG.info("##### Have expected view size: {}", size);
	}
}
