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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletContext;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;

// requires
// -Xbootclasspath/p:${system_property:user.home}/.m2/repository/org/mortbay/jetty/alpn/alpn-boot/8.1.9.v20160720/alpn-boot-8.1.9.v20160720.jar
public class JettyHttpServerImpl implements AutoCloseable {
	private final InetSocketAddress httpsBindAddress;
	private final Server server;
	private final ServletContextHandler servletHandler;

	public JettyHttpServerImpl(final String bindHost, final int bindPort, final int maxPortIncrements) {
		httpsBindAddress = getBindAddress(bindHost, bindPort, maxPortIncrements);

		final Server server = createServer(httpsBindAddress, null);

		servletHandler = createServletHandler();

		final HandlerWrapper gzipHandler = createGzipHandler();
		gzipHandler.setHandler(servletHandler);
		server.setHandler(gzipHandler);

		this.server = server;
	}

	public void start() {
		try {
			server.start();
		} catch (final Exception e) {
			throw new RuntimeException("Error starting Jetty", e);
		}
	}

	public ServletContext getServletContext() {
		return servletHandler.getServletContext();
	}

	public ServletContextHandler getServletHandler() {
		return servletHandler;
	}

	public InetSocketAddress getHttpsBindAddress() {
		return httpsBindAddress;
	}

	private Server createServer(final InetSocketAddress sslListenAddress, final InetSocketAddress plainListenAddress) {
		final HttpConfiguration config = createHttpConfiguration(sslListenAddress);
		final HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(config);
		final HTTP2ServerConnectionFactory http2ConnectionFactory = new HTTP2ServerConnectionFactory(config);
		final ALPNServerConnectionFactory alpnConnectionFactory = createAlpnConnectionFactory(httpConnectionFactory);
		final SslConnectionFactory sslConnectionFactory = createSslConnectionFactory(alpnConnectionFactory);

		final Server server = new Server();
		//server.setRequestLog(new AsyncNCSARequestLog());

		final ServerConnector sslConnector = new ServerConnector(server,
				sslConnectionFactory,
				alpnConnectionFactory,
				http2ConnectionFactory,
				httpConnectionFactory);
		sslConnector.setHost(sslListenAddress.getHostString());
		sslConnector.setPort(sslListenAddress.getPort());
		server.addConnector(sslConnector);

		if (plainListenAddress != null) {
			final ServerConnector plainHttpConnector = new ServerConnector(server, httpConnectionFactory);
			plainHttpConnector.setHost(plainListenAddress.getHostString());
			plainHttpConnector.setPort(plainListenAddress.getPort());
			server.addConnector(plainHttpConnector);
		}

		return server;
	}

	private InetSocketAddress getBindAddress(final String bindHost, final int startPort, final int maxIncrements) {
		for (int port = startPort; port <= startPort + maxIncrements; port++) {
			try {
				try (ServerSocket ss = new ServerSocket()) {
					if (bindHost == null) {
						ss.bind(new InetSocketAddress(port));
					} else {
						ss.bind(new InetSocketAddress(bindHost, port));
					}
					return (InetSocketAddress) ss.getLocalSocketAddress();
				}
			} catch (final IOException e) {
				// ignore
			}
		}
		throw new IllegalStateException("No free port found between " + startPort + " and " + (startPort + maxIncrements));
	}

	private GzipHandler createGzipHandler() {
		final GzipHandler gzipHandler = new GzipHandler();
		gzipHandler.setIncludedPaths("/*");
		gzipHandler.setMinGzipSize(0);
		gzipHandler.setIncludedMimeTypes(getGzipMimeTypes());
		return gzipHandler;
	}

	private String[] getGzipMimeTypes() {
		final Set<String> includedMimeTypes = new HashSet<>();
		//		JacksonMediaTypes.ALL.forEach((m) -> includedMimeTypes.add(m.getFullType()));
		includedMimeTypes.add("text/plain");
		includedMimeTypes.add("text/html");
		return includedMimeTypes.toArray(new String[includedMimeTypes.size()]);
	}

	private ServletContextHandler createServletHandler() {
		final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
		context.setContextPath("/");
		return context;
	}

	private ALPNServerConnectionFactory createAlpnConnectionFactory(final HttpConnectionFactory httpConnectionFactory) {
		final ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
		alpn.setDefaultProtocol(httpConnectionFactory.getProtocol());
		return alpn;
	}

	private SslConnectionFactory createSslConnectionFactory(final ALPNServerConnectionFactory alpn) {
		final SslContextFactory sslContextFactory = new SslContextFactory();
		sslContextFactory.setKeyStoreResource(Resource.newResource(getClass().getResource("test-keystore.jks")));
		sslContextFactory.setKeyStorePassword("changeit");
		sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
		sslContextFactory.setUseCipherSuitesOrder(true);
		final SslConnectionFactory ssl = new SslConnectionFactory(sslContextFactory, alpn.getProtocol());
		return ssl;
	}

	private HttpConfiguration createHttpConfiguration(final InetSocketAddress sslListenAddress) {
		final HttpConfiguration config = new HttpConfiguration();
		config.setSecureScheme("https");
		config.setSecurePort(sslListenAddress.getPort());
		config.setSendXPoweredBy(false);
		config.setSendServerVersion(false);
		config.addCustomizer(new SecureRequestCustomizer());
		return config;
	}

	@Override
	public void close() {
		try {
			server.stop();
		} catch (final Exception e) {
			throw new RuntimeException("Error stopping Jetty", e);
		}
	}
}
