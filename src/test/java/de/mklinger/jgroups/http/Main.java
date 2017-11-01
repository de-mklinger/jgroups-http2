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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.jgroups.http.client.jdk9.Jdk9HttpClientImpl;
import de.mklinger.jgroups.http.server.JGroupsServlet;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
public class Main {
	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	public static void main(final String[] args) throws Exception {

		try (final JettyHttpServerImpl server = new JettyHttpServerImpl("127.0.0.1", 8443, 10)) {
			final ServletHolder servletHolder = server.getServletHandler().addServlet(JGroupsServlet.class, "/jgroups");
			servletHolder.setInitOrder(1);
			servletHolder.setInitParameter("externalAddress", NetworkAddress.formatAddress(server.getHttpsBindAddress()));
			servletHolder.setInitParameter("client.class-name", Jdk9HttpClientImpl.class.getName());
			servletHolder.setInitParameter("client.ssl.trust-store", Main.class.getResource("test-keystore.jks").toExternalForm());
			server.start();

			final JChannel channel = (JChannel) server.getServletContext().getAttribute(JGroupsServlet.CHANNEL_ATTRIBUTE);
			channel.setReceiver(new ReceiverAdapter() {
				@Override
				public void receive(final Message msg) {
					LOG.info("##################");
					LOG.info("At {}: Received message from {}: {}", channel.address(), msg.getSrc(), msg.getObject());
					LOG.info("##################");
				}

				@Override
				public void viewAccepted(final View view) {
					LOG.info("##################");
					LOG.info("At {}: Accepted view: {}", channel.address(), view);
					LOG.info("##################");
				}

				@Override
				public void getState(final OutputStream output) throws Exception {
					output.write(("MYSTATE-" + new Date()).getBytes(StandardCharsets.UTF_8));
				}

				@Override
				public void setState(final InputStream input) throws Exception {
					final String state = IOUtils.toString(input, StandardCharsets.UTF_8);
					LOG.info("##################");
					LOG.info("At {}: New state: {}", channel.address(), state);
					LOG.info("##################");
				}
			});
			while (true) {
				//				LOG.info("VIEW: {}", channel.getViewAsString());
				//				channel.send(new Message(null, "Hello world from " + channel.address()));
				Thread.sleep(5000);
			}
		}
	}
}
