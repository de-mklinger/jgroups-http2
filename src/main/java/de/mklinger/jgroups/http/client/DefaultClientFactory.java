package de.mklinger.jgroups.http.client;

import static de.mklinger.jgroups.http.client.ClientConstants.*;

import java.security.KeyStore;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.commons.httpclient.HttpClient;
import de.mklinger.commons.httpclient.HttpClient.Builder;
import de.mklinger.micro.keystores.KeyStores;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public class DefaultClientFactory implements ClientFactory {
	private static final Logger LOG = LoggerFactory.getLogger(DefaultClientFactory.class);

	@Override
	public HttpClient newClient(final Properties clientProperties) {
		final Builder clientBuilder = HttpClient.newBuilder();
		configureClient(clientBuilder, clientProperties);
		return clientBuilder.build();
	}

	protected void configureClient(final Builder clientBuilder, final Properties clientProperties) {
		applyKeyStore(clientBuilder, clientProperties);
		applyTrustStore(clientBuilder, clientProperties);
		applyConnectTimeout(clientBuilder, clientProperties);
	}

	protected void applyKeyStore(final Builder clientBuilder, final Properties clientProperties) {
		final String keystoreLocation = clientProperties.getProperty(KEYSTORE_LOCATION);
		if (keystoreLocation != null) {
			LOG.info("Using HTTP client keystore from '{}'", keystoreLocation);
			final String keystorePassword = clientProperties.getProperty(KEYSTORE_PASSWORD);
			final KeyStore keyStore = KeyStores.load(keystoreLocation, keystorePassword);
			final String keyPassword = clientProperties.getProperty(KEY_PASSWORD, keystorePassword);
			clientBuilder.keyStore(keyStore, keyPassword);
		}
	}

	protected void applyTrustStore(final Builder clientBuilder, final Properties clientProperties) {
		final String truststoreLocation = clientProperties.getProperty(TRUSTSTORE_LOCATION);
		if (truststoreLocation != null) {
			LOG.info("Using HTTP client truststore from '{}'", truststoreLocation);
			final String truststorePassword = clientProperties.getProperty(TRUSTSTORE_PASSWORD);
			final KeyStore trustStore = KeyStores.load(truststoreLocation, truststorePassword);
			clientBuilder.trustStore(trustStore);
		}
	}

	protected void applyConnectTimeout(final Builder clientBuilder, final Properties clientProperties) {
		final String connectTimeout = clientProperties.getProperty(CONNECT_TIMEOUT);
		if (connectTimeout != null) {
			final Duration d = toDuration(connectTimeout);
			LOG.info("Using HTTP client connect timeout {}", d);
			clientBuilder.connectTimeout(d);
		}
	}

	private Duration toDuration(final String connectTimeout) {
		try {
			return Duration.parse(connectTimeout);
		} catch (final DateTimeParseException e) {
			// fall back to millis
			try {
				return Duration.ofMillis(Long.parseLong(connectTimeout));
			} catch (final NumberFormatException e2) {
				e.addSuppressed(e2);
				throw e;
			}
		}
	}
}
