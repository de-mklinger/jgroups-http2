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
package de.mklinger.jgroups.http.client.jetty;

import java.security.KeyStore;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.jgroups.http.client.Request;
import de.mklinger.jgroups.http.common.Keystores;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
public class JettyHttpClientImpl implements de.mklinger.jgroups.http.client.HttpClient {
	private static final Logger LOG = LoggerFactory.getLogger(JettyHttpClientImpl.class);

	private HttpClient client;

	private KeyStore keystore;
	private String keystorePassword;
	private String keyPassword;
	private KeyStore truststore;
	private String truststorePassword;

	@Override
	public void configure(final Properties clientProperties) {
		if (client != null) {
			throw new IllegalStateException("Client already started");
		}

		final String keystoreLocation = clientProperties.getProperty(KEYSTORE_LOCATION);
		if (keystoreLocation != null) {
			LOG.info("Using HTTP client keystore from '{}'", keystoreLocation);
			this.keystorePassword = clientProperties.getProperty(KEYSTORE_PASSWORD);
			this.keystore = Keystores.load(keystoreLocation, Optional.ofNullable(this.keystorePassword));
			this.keyPassword = clientProperties.getProperty(KEY_PASSWORD, this.keystorePassword);
		}

		final String truststoreLocation = clientProperties.getProperty(TRUSTSTORE_LOCATION);
		if (truststoreLocation != null) {
			LOG.info("Using HTTP client truststore from '{}'", truststoreLocation);
			this.truststorePassword = clientProperties.getProperty(TRUSTSTORE_PASSWORD);
			this.truststore = Keystores.load(truststoreLocation,  Optional.ofNullable(this.truststorePassword));
		}
	}

	@Override
	public void start() {
		try {
			final HttpClientTransportOverHTTP2 clientTransport = new HttpClientTransportOverHTTP2(new HTTP2Client());
			final SslContextFactory sslContextFactory = new SslContextFactory(false);
			if (keystore != null) {
				sslContextFactory.setKeyStore(keystore);
				sslContextFactory.setKeyStorePassword(keyPassword);
			}
			if (truststore != null) {
				sslContextFactory.setTrustStore(truststore);
				sslContextFactory.setTrustStorePassword(truststorePassword);
			}
			client = new HttpClient(clientTransport, sslContextFactory);
			client.start();
		} catch (final Exception e) {
			final RuntimeException rte = new RuntimeException("Error starting jetty http client", e);
			try {
				close();
			} catch (final Exception e2) {
				rte.addSuppressed(e2);
			}
			throw rte;
		}
	}

	@Override
	public void close() {
		try {
			if (client != null) {
				client.stop();
			}
		} catch (final Exception e) {
			throw new RuntimeException("Error stopping jetty http client", e);
		} finally {
			client = null;
		}
	}

	@Override
	public Request newRequest(final String url) {
		Objects.requireNonNull(client, "Client not started");
		return new JettyRequest(client.newRequest(url));
	}
}
