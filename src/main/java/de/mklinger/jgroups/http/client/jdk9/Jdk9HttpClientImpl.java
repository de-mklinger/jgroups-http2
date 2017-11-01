package de.mklinger.jgroups.http.client.jdk9;

import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Optional;
import java.util.Properties;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.jgroups.http.client.Request;
import de.mklinger.jgroups.http.common.Keystores;
import jdk.incubator.http.HttpClient;

public class Jdk9HttpClientImpl implements de.mklinger.jgroups.http.client.HttpClient {
	private static final Logger LOG = LoggerFactory.getLogger(Jdk9HttpClientImpl.class);

	private HttpClient httpClient;
	private SSLContext sslContext;

	@Override
	public void configure(final Properties clientProperties) {
		if (httpClient != null) {
			throw new IllegalStateException("Client already started");
		}

		sslContext = newSslContext(clientProperties);
	}

	private SSLContext newSslContext(final Properties clientProperties) {
		try {
			
			KeyManager[] keyManagers = null;
			final String keystoreLocation = clientProperties.getProperty(KEYSTORE_LOCATION);
			if (keystoreLocation != null) {
				LOG.info("Using HTTP client keystore from '{}'", keystoreLocation);
				Optional<String> keystorePassword = Optional.ofNullable(clientProperties.getProperty(KEYSTORE_PASSWORD));
				KeyStore keystore = Keystores.load(keystoreLocation, keystorePassword);
				char[] keyPassword = Optional.ofNullable(clientProperties.getProperty(KEY_PASSWORD, keystorePassword.orElse(null)))
						.map(s -> s.toCharArray())
						.orElse(null);

				KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509");
				keyManagerFactory.init(keystore, keyPassword);
				keyManagers = keyManagerFactory.getKeyManagers();
			}

			TrustManager[] trustManagers = null;
			final String truststoreLocation = clientProperties.getProperty(TRUSTSTORE_LOCATION);
			if (truststoreLocation != null) {
				LOG.info("Using HTTP client truststore from '{}'", truststoreLocation);
				Optional<String> truststorePassword = Optional.ofNullable(clientProperties.getProperty(TRUSTSTORE_PASSWORD));
				KeyStore truststore = Keystores.load(truststoreLocation, truststorePassword);

				TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
				trustManagerFactory.init(truststore);
				trustManagers = trustManagerFactory.getTrustManagers();
			}

			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(keyManagers, trustManagers, null);
			return sslContext;

		} catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException | KeyManagementException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void start() {
		if (httpClient != null) {
			throw new IllegalStateException("Client already started");
		}
		
		httpClient = HttpClient.newBuilder()
				.sslContext(sslContext)
				.build();
	}

	@Override
	public void close() {
		httpClient = null;
	}

	@Override
	public Request newRequest(String url) {
		return new Jdk9Request(httpClient, URI.create(url));
	}
}
