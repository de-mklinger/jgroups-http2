package de.mklinger.jgroups.http.client.jdk9;

import static de.mklinger.jgroups.http.client.HttpClient.*;
import static de.mklinger.jgroups.http.client.HttpClient.KEY_PASSWORD;
import static de.mklinger.jgroups.http.client.HttpClient.TRUSTSTORE_LOCATION;
import static de.mklinger.jgroups.http.client.HttpClient.TRUSTSTORE_PASSWORD;

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

import de.mklinger.jgroups.http.common.Keystores;

public class Jdk9HttpClientSslContext {
	private static final Logger LOG = LoggerFactory.getLogger(Jdk9HttpClientSslContext.class);
	
	public static SSLContext newSslContext(Optional<String> keystoreLocation, Optional<String> keystorePassword, 
			Optional<String> keyPassword, Optional<String> truststoreLocation, Optional<String> truststorePassword) {
		
		try {
			
			KeyManager[] keyManagers = null;
			if (keystoreLocation.isPresent()) {
				LOG.info("Using HTTP client keystore from '{}'", keystoreLocation.get());
				KeyStore keystore = Keystores.load(keystoreLocation.get(), keystorePassword);
				char[] keyPasswordArr = keyPassword
						.map(s -> s.toCharArray())
						.orElse(null);

				KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				keyManagerFactory.init(keystore, keyPasswordArr);
				keyManagers = keyManagerFactory.getKeyManagers();
			}

			TrustManager[] trustManagers = null;
			if (truststoreLocation != null) {
				LOG.info("Using HTTP client truststore from '{}'", truststoreLocation);
				KeyStore truststore = Keystores.load(truststoreLocation.get(), truststorePassword);

				TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
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

	public static SSLContext newSslContext(Properties clientProperties) {
		Optional<String> keystoreLocation = Optional.ofNullable(clientProperties.getProperty(KEYSTORE_LOCATION));
		Optional<String> keystorePassword = Optional.ofNullable(clientProperties.getProperty(KEYSTORE_PASSWORD));
		Optional<String> keyPassword = Optional.ofNullable(clientProperties.getProperty(KEY_PASSWORD, keystorePassword.orElse(null)));
		Optional<String> truststoreLocation = Optional.ofNullable(clientProperties.getProperty(TRUSTSTORE_LOCATION));
		Optional<String> truststorePassword = Optional.ofNullable(clientProperties.getProperty(TRUSTSTORE_PASSWORD));
		return newSslContext(keystoreLocation, keystorePassword, keyPassword, truststoreLocation, truststorePassword);
	}
}
