package de.mklinger.jgroups.http.client;

import static de.mklinger.jgroups.http.client.ClientConstants.*;

import java.security.KeyStore;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.commons.httpclient.HttpClient;
import de.mklinger.commons.httpclient.HttpClient.Builder;
import de.mklinger.jgroups.http.common.Keystores;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public class ClientFactory {
	private static final Logger LOG = LoggerFactory.getLogger(ClientFactory.class);

	public static HttpClient newClient(final Properties clientProperties) {
		final Builder clientBuilder = HttpClient.newBuilder();
		configureClient(clientBuilder, clientProperties);
		return clientBuilder.build();
	}

	private static void configureClient(final Builder clientBuilder, final Properties clientProperties) {
		final String keystoreLocation = clientProperties.getProperty(KEYSTORE_LOCATION);
		if (keystoreLocation != null) {
			LOG.info("Using HTTP client keystore from '{}'", keystoreLocation);
			final String keystorePassword = clientProperties.getProperty(KEYSTORE_PASSWORD);
			final KeyStore keyStore = Keystores.load(keystoreLocation, Optional.ofNullable(keystorePassword));
			final String keyPassword = clientProperties.getProperty(KEY_PASSWORD, keystorePassword);
			clientBuilder.keyStore(keyStore, keyPassword);
		}

		final String truststoreLocation = clientProperties.getProperty(TRUSTSTORE_LOCATION);
		if (truststoreLocation != null) {
			LOG.info("Using HTTP client truststore from '{}'", truststoreLocation);
			final String truststorePassword = clientProperties.getProperty(TRUSTSTORE_PASSWORD);
			final KeyStore trustStore = Keystores.load(truststoreLocation,  Optional.ofNullable(truststorePassword));
			clientBuilder.trustStore(trustStore);
		}
	}
}
