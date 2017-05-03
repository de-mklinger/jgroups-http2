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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.jgroups.PhysicalAddress;
import org.jgroups.annotations.Property;
import org.jgroups.protocols.TP;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.jgroups.http.client.BytesContentProvider;
import de.mklinger.jgroups.http.client.CompleteListener;
import de.mklinger.jgroups.http.client.HttpClient;
import de.mklinger.jgroups.http.client.Result;
import de.mklinger.jgroups.http.common.Closeables;
import de.mklinger.jgroups.http.common.PropertiesString;
import de.mklinger.jgroups.http.server.HttpReceiver;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
public class HTTP extends TP implements HttpReceiver {
	private static final Logger LOG = LoggerFactory.getLogger(HTTP.class);

	@Property(
			description = "Http client implementation class. Must implement de.mklinger.jgroups.http.client.HttpClient",
			systemProperty = "de.mklinger.jgroups.http.clientImpl",
			converter = ClassConverter.class,
			writable = false)
	protected Class<? extends HttpClient> httpClientClass;

	@Property(
			description = "Http client properties.",
			systemProperty = "de.mklinger.jgroups.http.clientProperties",
			writable = false)
	protected String httpClientPropertiesString;

	@Property(
			description = "Http client properties separator.",
			systemProperty = "de.mklinger.jgroups.http.clientPropertiesSeparator",
			writable = false)
	protected String httpClientPropertiesStringSeparator = ",";

	private HttpClient client;
	private String servicePath = "/jgroups";
	private Properties httpClientProperties;

	public void setExternalAddress(final IpAddress externalAddress) {
		external_addr = externalAddress.getIpAddress();
		external_port = externalAddress.getPort();
	}

	public void setServicePath(final String servicePath) {
		this.servicePath = servicePath;
	}

	public void setHttpClientProperties(final Properties httpClientProperties) {
		this.httpClientProperties = httpClientProperties;
	}

	@Override
	public void start() throws Exception {
		requireValidServicePath();

		try {
			if (httpClientProperties == null && httpClientPropertiesString != null && !httpClientPropertiesString.isEmpty()) {
				httpClientProperties = PropertiesString.fromString(httpClientPropertiesString, httpClientPropertiesStringSeparator);
			}
			client = httpClientClass.newInstance();
			if (httpClientProperties != null) {
				client.configure(httpClientProperties);
			}
			client.start();
			super.start();
		} catch (final Exception e) {
			try {
				close();
			} catch (final Exception ex) {
				e.addSuppressed(ex);
			}
			throw new RuntimeException("Error instantiating http client class " + httpClientClass, e);
		}
	}

	private void requireValidServicePath() {
		if (servicePath == null) {
			throw new IllegalArgumentException("servicePath is null");
		}
		if (!servicePath.isEmpty() && !servicePath.startsWith("/")) {
			throw new IllegalArgumentException("servicePath must be empty or start with '/'. Given: '" + servicePath + "'");
		}
		LOG.info("Using service path '{}'", servicePath);
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

		client.newRequest(getServiceUrl(destIpAddress))
		.header("X-Sender", getLocalPhysicalAddress())
		.content(new BytesContentProvider("application/x-jgroups-message", data, 0, length))
		.send(new CompleteListener() {
			@Override
			public void onComplete(final Result result) {
				if (result.isFailed()) {
					if (result.getFailure() instanceof ConnectException) {
						LOG.info("Send to {}: Failed: {}", destIpAddress, result.getFailure().toString());
					} else {
						LOG.warn("Send to {}: Failed:", destIpAddress, result.getFailure());
					}
					// TODO trigger SUSPECT here?
				} else {
					LOG.debug("Send to {}: Complete: {}", destIpAddress, result.getResponse().getStatus());
				}
			}
		});
	}

	private String getServiceUrl(final IpAddress destIpAddress) {
		final StringBuilder sb = new StringBuilder();
		sb.append("https://");
		final String hostAddress = destIpAddress.getIpAddress().getHostAddress();
		if (hostAddress.indexOf(':') != -1) {
			// IPv6 address with colons
			sb.append('[');
			sb.append(hostAddress);
			sb.append(']');
		} else {
			sb.append(hostAddress);
		}
		sb.append(':');
		sb.append(destIpAddress.getPort());
		sb.append(servicePath);
		return sb.toString();
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
		return new IpAddress(external_addr, external_port);
	}

	public static void main(final String[] args) throws SocketException {
		System.out.println(Util.getNonLoopbackAddress());
		System.out.println();
		final List<NetworkInterface> interfaces = Util.getAllAvailableInterfaces();
		for (final NetworkInterface interfaze : interfaces) {
			System.out.println(interfaze.getDisplayName());
			for (final InetAddress addr : Collections.list(interfaze.getInetAddresses())) {
				System.out.println(addr);
			}
			System.out.println();
		}
	}
}
