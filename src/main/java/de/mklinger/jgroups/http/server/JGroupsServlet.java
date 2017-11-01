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
package de.mklinger.jgroups.http.server;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jgroups.JChannel;
import org.jgroups.protocols.mklinger.HTTP;
import org.jgroups.stack.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.jgroups.http.client.HttpClient;
import de.mklinger.jgroups.http.common.Closeables;
import de.mklinger.jgroups.http.common.SizeValue;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
public class JGroupsServlet extends HttpServlet {
	private static final String PROPS_PREFIX = "de.mklinger.jgroups.http.";
	public static final String CHANNEL_ATTRIBUTE = PROPS_PREFIX + "channel";

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(JGroupsServlet.class);

	private int maxContentLength;
	private HttpReceiver receiver;
	private JChannel channel;
	private String jchannelProps;

	@Override
	public void init() throws ServletException {
		final String clusterName = getSetting("clusterName", Optional.of(() -> "jgroupscluster"));
		IpAddress externalAddress;
		try {
			externalAddress = new IpAddress(getSetting("externalAddress", Optional.empty()));
		} catch (final Exception e) {
			throw new ServletException("Invalid externalAddress", e);
		}
		final String servicePath = getSetting("servicePath", Optional.of(() -> getServletContext().getContextPath() + "/jgroups"));

		final SizeValue maxContentSize = SizeValue.parseSizeValue(getSetting("maxContentSize", Optional.of(() -> "500k")));
		if (maxContentSize.singles() > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Max content size too large: " + maxContentSize);
		}
		this.maxContentLength = (int)maxContentSize.singles();

		this.jchannelProps = getSetting("jchannelProps", Optional.of(() -> "http.xml"));

		final Optional<Supplier<String>> nullDefault = Optional.of(() -> null);

		final Properties httpClientProperties = new Properties();
		setNullableProperty(httpClientProperties, HttpClient.CLASS_NAME, getSetting("client." + HttpClient.CLASS_NAME, nullDefault));
		setNullableProperty(httpClientProperties, HttpClient.KEYSTORE_LOCATION, getSetting("client." + HttpClient.KEYSTORE_LOCATION, nullDefault));
		setNullableProperty(httpClientProperties, HttpClient.KEYSTORE_PASSWORD, getSetting("client." + HttpClient.KEYSTORE_PASSWORD, nullDefault));
		setNullableProperty(httpClientProperties, HttpClient.KEY_PASSWORD, getSetting("client." + HttpClient.KEY_PASSWORD, nullDefault));
		setNullableProperty(httpClientProperties, HttpClient.TRUSTSTORE_LOCATION, getSetting("client." + HttpClient.TRUSTSTORE_LOCATION, nullDefault));
		setNullableProperty(httpClientProperties, HttpClient.TRUSTSTORE_PASSWORD, getSetting("client." + HttpClient.TRUSTSTORE_PASSWORD, nullDefault));

		initChannel(clusterName, externalAddress, servicePath, httpClientProperties);
	}

	private void setNullableProperty(final Properties properties, final String key, final String value) {
		if (key != null && value != null) {
			properties.setProperty(key, value);
		}
	}

	private void initChannel(final String clusterName, final IpAddress externalAddress, final String servicePath, final Properties httpClientProperties) throws ServletException {
		try {
			this.channel = new JChannel(jchannelProps);
		} catch (final Exception e) {
			throw new ServletException("Error creating channel using '" + jchannelProps + "'", e);
		}
		getServletContext().setAttribute(CHANNEL_ATTRIBUTE, this.channel);
		onChannelCreated(this.channel);

		final HTTP httpProtocol = (HTTP) channel.getProtocolStack().getTransport();
		httpProtocol.setExternalAddress(externalAddress);
		httpProtocol.setServicePath(servicePath);
		httpProtocol.setHttpClientProperties(httpClientProperties);
		this.receiver = httpProtocol;

		ForkJoinPool.commonPool().execute(() -> {
			try {
				channel.connect(clusterName);
				onChannelConnected(channel, clusterName);
			} catch (final Exception e) {
				onChannelConnectError(channel, clusterName, e);
			}
		});
	}

	private String getSetting(final String name, final Optional<Supplier<String>> def) throws ServletException {
		String value;
		final String systemPropertyName = PROPS_PREFIX + name;
		value = System.getProperty(systemPropertyName);
		if (value != null && !value.isEmpty()) {
			LOG.debug("Using system property '{}': '{}'", systemPropertyName, value);
			return value;
		}
		LOG.debug("System property not set: '{}'", systemPropertyName);
		value = getServletConfig().getInitParameter(name);
		if (value != null && !value.isEmpty()) {
			LOG.debug("Using init parameter '{}': '{}'", name, value);
			return value;
		}
		LOG.debug("Init parameter not set: '{}'", name);
		value = def
				.orElseThrow(() -> new ServletException("Missing required setting system property '" + systemPropertyName + "' or init parameter '" + name + "'"))
				.get();
		LOG.debug("Using fallback value for setting '{}': '{}'", name, value);
		return value;
	}

	@Override
	public void destroy() {
		Closeables.closeUnchecked(channel);
	}

	@Override
	protected void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		LOG.debug("SERVICE: {} {}", request.getMethod(), request.getPathInfo());
		final AsyncContext asyncContext = request.startAsync();
		final ServletInputStream inputStream = request.getInputStream();
		try {
			inputStream.setReadListener(new JGroupsReadListener(asyncContext, receiver, maxContentLength));
		} catch (final BadRequestException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.toString());
			asyncContext.complete();
		} catch (Exception e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			asyncContext.complete();
		}
	}

	/**
	 * Callback method for sub-classes. Default implementation does nothing.
	 */
	protected void onChannelCreated(final JChannel channel) {
	}

	/**
	 * Callback method for sub-classes. Default implementation does nothing.
	 */
	protected void onChannelConnected(final JChannel channel, final String clusterName) {
	}

	/**
	 * Callback method for sub-classes. Default implementation logs the error.
	 */
	protected void onChannelConnectError(final JChannel channel, final String clusterName, final Exception e) {
		LOG.error("Error connecting to cluster '{}'", clusterName, e);
	}
}
