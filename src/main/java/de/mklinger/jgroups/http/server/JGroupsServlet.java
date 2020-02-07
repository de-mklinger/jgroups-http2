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
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jgroups.JChannel;
import org.jgroups.conf.ProtocolStackConfigurator;
import org.jgroups.conf.XmlConfigurator;
import org.jgroups.protocols.mklinger.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.mklinger.jgroups.http.common.Closeables;
import de.mklinger.jgroups.http.common.SizeValue;
import de.mklinger.micro.annotations.Nullable;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
public class JGroupsServlet extends HttpServlet {
	private static final String PROPS_PREFIX = "de.mklinger.jgroups.http.";
	public static final String CHANNEL_ATTRIBUTE = PROPS_PREFIX + "channel";
	private static final String RECEIVER_ATTRIBUTE = PROPS_PREFIX + "receiver";
	private static final String MAX_CONTENT_LENGTH_ATTRIBUTE = PROPS_PREFIX + "maxContentLength";

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(JGroupsServlet.class);

	@Override
	public void init() throws ServletException {
		initMaxContentSize();

		final ProtocolStackConfigurator protocolStackConfigurator = initProtocolStack();
		final String channelName = getSetting("channelName", () -> null);
		final JChannel channel = createChannel(protocolStackConfigurator, channelName);

		final boolean connect = "true".equals(getSetting("connect", () -> "true"));
		if (connect) {
			final String clusterName = getSetting("clusterName",
					() -> getSetting("cluster.name", // support "cluster.name" for compatibility with earlier versions
							() -> "jgroupscluster"));
			connectChannel(clusterName, channel);
		}
	}

	private void initMaxContentSize() throws ServletException {
		final SizeValue maxContentSize = SizeValue.parseSizeValue(getSetting("maxContentSize", () -> "500k"));
		if (maxContentSize.singles() > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Max content size too large: " + maxContentSize);
		}

		getServletContext().setAttribute(MAX_CONTENT_LENGTH_ATTRIBUTE, (int)maxContentSize.singles());
	}

	private ProtocolStackConfigurator initProtocolStack() throws ServletException {
		final Map<String, String> protocolParameters = getProtocolParameters();
		final String baseConfigLocation = getSetting("baseConfigLocation", () -> "classpath:http.xml");
		final ProtocolStackConfigurator protocolStackConfigurator = getProtocolStackConfigurator(baseConfigLocation, protocolParameters);
		return protocolStackConfigurator;
	}

	private Map<String, String> getProtocolParameters() {
		final String prefix = "protocol.";
		final Map<String, String> protocolParameters = new HashMap<>();
		final Enumeration<String> initParameterNames = getServletConfig().getInitParameterNames();
		while (initParameterNames.hasMoreElements()) {
			final String parameterName = initParameterNames.nextElement();
			if (parameterName.startsWith(prefix)) {
				final String key = parameterName.substring(prefix.length());
				final String value = getServletConfig().getInitParameter(parameterName);
				protocolParameters.put(key, value);
			}
		}
		return protocolParameters;
	}

	public ProtocolStackConfigurator getProtocolStackConfigurator(final String baseConfigLocation, final Map<String, String> protocolParameters) {
		final Document doc = loadBaseConfig(baseConfigLocation);

		final NodeList protocolElements = doc.getDocumentElement().getChildNodes();
		for (int i = 0; i < protocolElements.getLength(); i++) {
			final Node n = protocolElements.item(i);
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element element = (Element) n;
			applyProtocolParameters(element, protocolParameters);
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("Protocol stack configuration:\n\n{}", toString(doc));
		}

		try {
			return XmlConfigurator.getInstance(doc.getDocumentElement());
		} catch (final Exception e) {
			throw new RuntimeException("Error parsing JGroups xml", e);
		}

	}

	private Document loadBaseConfig(final String baseConfigLocation) {
		if (baseConfigLocation == null || baseConfigLocation.isEmpty()) {
			throw new IllegalArgumentException();
		}

		try (InputStream in = newInputStream(baseConfigLocation)) {
			return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
		} catch (SAXException | IOException | ParserConfigurationException e) {
			throw new IllegalArgumentException("Error loading configuration: " + baseConfigLocation, e);
		}
	}

	private void applyProtocolParameters(Element element, Map<String, String> protocolParameters) {
		final String protocolName = element.getNodeName();

		final String prefix = protocolName + ".";

		for (final Entry<String, String> e : protocolParameters.entrySet()) {
			final String key = e.getKey();
			if (key.startsWith(prefix)) {
				final String property = key.substring(prefix.length());
				element.setAttribute(property, e.getValue());
			}
		}
	}

	private InputStream newInputStream(final String configLocation) throws IOException {
		if (configLocation.startsWith("classpath:")) {
			final String classpathLocation = configLocation.substring("classpath:".length());
			final InputStream in = getClass().getClassLoader().getResourceAsStream(classpathLocation);
			if (in == null) {
				throw new IllegalArgumentException("Configuration not found on classpath: " + classpathLocation);
			}
			return in;
		} else {
			try {
				return new URI(configLocation).toURL().openStream();
			} catch (URISyntaxException | MalformedURLException e) {
				throw new IllegalArgumentException("Illegal configuration location: " + configLocation, e);
			}
		}
	}

	private Object toString(final Document doc) {
		try {
			final Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			final StreamResult result = new StreamResult(new StringWriter());
			final DOMSource source = new DOMSource(doc);
			transformer.transform(source, result);
			return result.getWriter().toString();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	private JChannel createChannel(final ProtocolStackConfigurator configurator, @Nullable String channelName) throws ServletException {
		JChannel channel;
		try {
			channel = new JChannel(configurator);
		} catch (final Exception e) {
			throw new ServletException("Error creating channel", e);
		}

		if (channelName != null) {
			channel.setName(channelName);
		}

		getServletContext().setAttribute(CHANNEL_ATTRIBUTE, channel);

		final HTTP httpProtocol = (HTTP) channel.getProtocolStack().getTransport();
		if (httpProtocol == null) {
			throw new IllegalStateException("HTTP protocol not found in channel protocol stack");
		}
		getServletContext().setAttribute(RECEIVER_ATTRIBUTE, httpProtocol);

		onChannelCreated(channel);
		return channel;
	}

	private void connectChannel(final String clusterName, JChannel channel) {
		// Using a custom thread here (instead of ForkJoinPool.commonPool() in previous
		// implementation) to be in the right Thread and context class loader hierarchy.
		// Other threads and thread pools may be initialized lazy by this call. We want
		// them all to be children of our current thread and use the same context class
		// loader.
		final Thread connectThread = new Thread(() -> {
			try {
				channel.connect(clusterName);
				onChannelConnected(channel, clusterName);
			} catch (final Throwable e) {
				onChannelConnectError(channel, clusterName, e);
			}
		});
		connectThread.setName("jgroups-channel-connect");
		connectThread.start();
	}

	private String getSetting(final String name, final Supplier<String> def) {
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
		if (def == null) {
			throw new IllegalArgumentException("Missing required setting system property '" + systemPropertyName + "' or init parameter '" + name + "'");
		} else {
			value = def.get();
			LOG.debug("Using fallback value for setting '{}': '{}'", name, value);
		}
		return value;
	}

	@Override
	public void destroy() {
		final JChannel channel = (JChannel) getServletContext().getAttribute(CHANNEL_ATTRIBUTE);
		if (channel != null) {
			getServletContext().removeAttribute(CHANNEL_ATTRIBUTE);
			try {
				onChannelClose(channel);
			} catch (final Exception e) {
				LOG.warn("Error in onChannelClose callback", e);
			}
			Closeables.closeUnchecked(channel);
		}
	}

	@Override
	protected void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		LOG.debug("Service: {}", request.getMethod(), request.getRequestURL());

		final HttpReceiver receiver = (HttpReceiver) getServletContext().getAttribute(RECEIVER_ATTRIBUTE);
		if (receiver == null) {
			throw new IllegalStateException("No receiver");
		}

		final int maxContentLength = (int) getServletContext().getAttribute(MAX_CONTENT_LENGTH_ATTRIBUTE);

		final AsyncContext asyncContext = request.startAsync();
		final ServletInputStream inputStream = request.getInputStream();
		try {
			inputStream.setReadListener(new JGroupsReadListener(asyncContext, receiver, maxContentLength));
		} catch (final BadRequestException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.toString());
			asyncContext.complete();
		} catch (final Exception e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			asyncContext.complete();
		}
	}

	/**
	 * Callback method for sub-classes. Default implementation does nothing.
	 * @param channel The channel that was created
	 */
	protected void onChannelCreated(final JChannel channel) {
	}

	/**
	 * Callback method for sub-classes. Default implementation does nothing.
	 * @param channel The channel that connected the cluster
	 * @param clusterName The name of the cluster that was connected
	 */
	protected void onChannelConnected(final JChannel channel, final String clusterName) {
	}

	/**
	 * Callback method for sub-classes. Default implementation logs the error.
	 * @param channel The channel that should have connected the cluster
	 * @param clusterName The name of the cluster that was tried to connect
	 * @param e The error
	 */
	protected void onChannelConnectError(final JChannel channel, final String clusterName, final Throwable e) {
		LOG.error("Error connecting to cluster '{}'", clusterName, e);
	}

	/**
	 * Callback method for sub-classes. Default implementation does nothing.
	 * Called before the channel is actually closed.
	 * @param channel The channel about to be closed
	 */
	protected void onChannelClose(final JChannel channel) {
	}
}
