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
import java.util.Optional;
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

	@Override
	public void init() throws ServletException {
		final String clusterName = getSetting("cluster.name", Optional.of(() -> "jgroupscluster"));

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

		final SizeValue maxContentSize = SizeValue.parseSizeValue(getSetting("maxContentSize", Optional.of(() -> "500k")));
		if (maxContentSize.singles() > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Max content size too large: " + maxContentSize);
		}
		this.maxContentLength = (int)maxContentSize.singles();

		final String baseConfigLocation = getSetting("baseConfigLocation", Optional.of(() -> "classpath:http.xml"));
		final ProtocolStackConfigurator protocolStackConfigurator = getProtocolStackConfigurator(baseConfigLocation, protocolParameters);
		initChannel(clusterName, protocolStackConfigurator);
	}

	public ProtocolStackConfigurator getProtocolStackConfigurator(final String baseConfigLocation, final Map<String, String> protocolParameters) {
		if (baseConfigLocation == null || baseConfigLocation.isEmpty()) {
			throw new IllegalArgumentException();
		}

		Document doc;
		try (InputStream in = newInputStream(baseConfigLocation)) {
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
		} catch (SAXException | IOException | ParserConfigurationException e) {
			throw new IllegalArgumentException("Error loading configuration: " + baseConfigLocation, e);
		}

		final NodeList protocolElements = doc.getDocumentElement().getChildNodes();
		for (int i = 0; i < protocolElements.getLength(); i++) {
			final Node n = protocolElements.item(i);
			if (n.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element element = (Element) n;
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

		if (LOG.isDebugEnabled()) {
			LOG.debug("Protocol stack configuration:\n\n{}", toString(doc));
		}

		try {
			return XmlConfigurator.getInstance(doc.getDocumentElement());
		} catch (final Exception e) {
			throw new RuntimeException("Error parsing JGroups xml", e);
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

	private void initChannel(final String clusterName, final ProtocolStackConfigurator configurator) throws ServletException {
		try {
			this.channel = new JChannel(configurator);
		} catch (final Exception e) {
			throw new ServletException("Error creating channel", e);
		}
		getServletContext().setAttribute(CHANNEL_ATTRIBUTE, this.channel);
		onChannelCreated(this.channel);

		final HTTP httpProtocol = (HTTP) channel.getProtocolStack().getTransport();
		this.receiver = httpProtocol;

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
		LOG.debug("Service: {}", request.getMethod(), request.getRequestURL());
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
}
