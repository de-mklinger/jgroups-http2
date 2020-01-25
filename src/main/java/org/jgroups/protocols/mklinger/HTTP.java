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
package org.jgroups.protocols.mklinger;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CompletionException;

import org.jgroups.Address;
import org.jgroups.PhysicalAddress;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.PingData;
import org.jgroups.protocols.TP;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Responses;
import org.jgroups.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.commons.httpclient.BodyHandlers;
import de.mklinger.commons.httpclient.BodyProviders;
import de.mklinger.commons.httpclient.HttpClient;
import de.mklinger.commons.httpclient.HttpRequest;
import de.mklinger.jgroups.http.client.ClientConstants;
import de.mklinger.jgroups.http.client.ClientFactory;
import de.mklinger.jgroups.http.client.DefaultClientFactory;
import de.mklinger.jgroups.http.common.Closeables;
import de.mklinger.jgroups.http.common.PropertiesString;
import de.mklinger.jgroups.http.server.HttpReceiver;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
public class HTTP extends TP implements HttpReceiver {
	private static final Logger LOG = LoggerFactory.getLogger(HTTP.class);

	@Property(
			description = "Http client properties.",
			systemProperty = "jgroups.http.client_props",
			writable = false)
	protected String client_props;

	@Property(
			description = "Http client properties separator.",
			systemProperty = "jgroups.http.client_props_sep",
			writable = false)
	protected String client_props_sep = ",";

	@Property(
			description = "Http service path.",
			systemProperty = "jgroups.http.external_path",
			writable = false)
	protected String external_path = "/jgroups";

	private ClientFactory clientFactory;

	private HttpClient client;

	static {
		ClassConfigurator.add((short)2000, HostAddress.class);
	}

	@Override
	public void start() throws Exception {
		requireValidServicePath();

		try {
			this.client = newClient();
			super.start();
		} catch (final Exception e) {
			try {
				close();
			} catch (final Exception ex) {
				e.addSuppressed(ex);
			}
			throw new RuntimeException("Error starting HTTP", e);
		}
	}

	private HttpClient newClient() {
		final Properties clientProperties;
		if (client_props != null && !client_props.isEmpty()) {
			clientProperties = PropertiesString.fromString(client_props, client_props_sep);
		} else {
			clientProperties = new Properties();
		}

		final ClientFactory clientFactory;
		if (this.clientFactory != null) {
			clientFactory = this.clientFactory;
		} else {
			clientFactory = newClientFactory(clientProperties);
		}

		return clientFactory.newClient(clientProperties);
	}

	public void setClientFactory(final ClientFactory clientFactory) {
		this.clientFactory = clientFactory;
	}

	private ClientFactory newClientFactory(final Properties httpClientProperties) {
		final String clientFactoryClassName = httpClientProperties.getProperty(ClientConstants.CLIENT_FACTORY_CLASSNAME);
		if (clientFactoryClassName != null) {
			LOG.info("Using client factory {}", clientFactoryClassName);
			try {
				return (ClientFactory) Class.forName(clientFactoryClassName).newInstance();
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				throw new RuntimeException("Error instantiating client factory " + clientFactoryClassName);
			}
		} else {
			LOG.info("Using default client factory");
			return new DefaultClientFactory();
		}
	}

	private void requireValidServicePath() {
		if (external_path == null) {
			throw new IllegalArgumentException("external_path is null");
		}
		if (!external_path.isEmpty() && !external_path.startsWith("/")) {
			throw new IllegalArgumentException("external_path must be empty or start with '/'. Given: '" + external_path + "'");
		}
		LOG.info("Using external path '{}'", external_path);
	}

	@Override
	public void destroy() {
		super.destroy();
		close();
	}

	private void close() {
		try {
			Closeables.closeUnchecked(client);
		} finally {
			client = null;
		}
	}

	@Override
	public boolean supportsMulticasting() {
		return false;
	}

	@Override
	public void sendMulticast(final byte[] data, final int offset, final int length) throws Exception {
		sendToMembers(members, data, offset, length);
	}

	@Override
	public void sendUnicast(final PhysicalAddress dest, final byte[] data, final int offset, final int length) throws Exception {
		send((IpAddress)dest, data, offset, length);
	}

	private void send(final IpAddress destIpAddress, final byte[] _data, final int offset, final int length) {
		// Must copy data, as we send async and caller re-uses the byte array :-(
		final byte[] data = new byte[length];
		System.arraycopy(_data, offset, data, 0, length);

		LOG.debug("Sending message to {}...", destIpAddress);

		final HttpRequest request = HttpRequest.newBuilder(getServiceUrl(destIpAddress))
				.header("X-Sender", getLocalPhysicalAddress())
				.header("Content-Type", "application/x-jgroups-message")
				.POST(BodyProviders.fromByteArray(data))
				.build();

		client.sendAsync(request, BodyHandlers.discard())
		.thenAccept(response -> LOG.debug("Send to {}: Complete: {}", destIpAddress, response.statusCode()))
		.exceptionally(failure -> {
			// TODO why CompletionException here?
			Throwable ex = failure;
			if (ex instanceof CompletionException) {
				ex = ex.getCause();
			}
			if (ex instanceof ConnectException || ex instanceof SocketTimeoutException) {
				LOG.info("Send to {}: Failed: {}", destIpAddress, ex.toString());
			} else {
				LOG.warn("Send to {}: Failed:", destIpAddress, ex);
			}
			// TODO trigger SUSPECT here?
			return null;
		});
	}

	private URI getServiceUrl(final IpAddress destIpAddress) {
		final StringBuilder sb = new StringBuilder();
		sb.append("https://");

		final String hostName = getHostName(destIpAddress);
		if (hostName != null) {
			LOG.debug("Using host name for service URL: {}", hostName);
			sb.append(hostName);
		} else {
			final String hostAddress = destIpAddress.getIpAddress().getHostAddress();
			LOG.debug("Using ip address for service URL: {}", hostAddress);
			if (hostAddress.indexOf(':') != -1) {
				// IPv6 address with colons
				sb.append('[');
				sb.append(hostAddress);
				sb.append(']');
			} else {
				sb.append(hostAddress);
			}
		}

		sb.append(':');
		sb.append(destIpAddress.getPort());
		sb.append(external_path);

		return URI.create(sb.toString());
	}

	private String getHostName(final IpAddress ipAddress) {
		if (ipAddress instanceof HostAddress) {
			return ((HostAddress)ipAddress).getHostName();
		} else {
			return null;
		}
	}

	public HttpClient getClient() {
		return client;
	}

	@Override
	public String getInfo() {
		return "HTTP@" + getLocalPhysicalAddress();
	}

	@Override
	protected PhysicalAddress getPhysicalAddress() {
		return createLocalAddress();
	}

	protected IpAddress createLocalAddress() {
		if (external_addr == null || external_port == 0) {
			throw new IllegalStateException("External address is not set");
		}
		return new HostAddress(external_addr, external_port);
	}

	// Implementation taken from org.jgroups.protocols.TP.sendToSingleMember(Address, byte[], int, int)
	public PhysicalAddress getPhysicalAddress(final Address dest) {
		if (dest instanceof PhysicalAddress) {
			return (PhysicalAddress)dest;
		}

		PhysicalAddress physical_dest;
		if ((physical_dest = getPhysicalAddressFromCache(dest)) != null) {
			return physical_dest;
		}

		if (who_has_cache.addIfAbsentOrExpired(dest)) { // true if address was added
			// FIND_MBRS must return quickly
			final Responses responses = fetchResponsesFromDiscoveryProtocol(Collections.singletonList(dest));
			try {
				for (final PingData data : responses) {
					if (data.getAddress() != null && data.getAddress().equals(dest)) {
						if ((physical_dest = data.getPhysicalAddr()) != null) {
							return physical_dest;
						}
					}
				}
				log.warn(Util.getMessage("PhysicalAddrMissing"), local_addr, dest);
			} finally {
				responses.done();
			}
		}

		return null;
	}
}
