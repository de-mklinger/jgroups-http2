package de.mklinger.jgroups.http.client.jdk9;

import java.net.URI;
import java.util.Properties;

import javax.net.ssl.SSLContext;

import de.mklinger.jgroups.http.client.Request;
import jdk.incubator.http.HttpClient;

public class Jdk9HttpClientImpl implements de.mklinger.jgroups.http.client.HttpClient {
	private HttpClient httpClient;
	private SSLContext sslContext;

	@Override
	public void configure(final Properties clientProperties) {
		if (httpClient != null) {
			throw new IllegalStateException("Client already started");
		}

		sslContext = Jdk9HttpClientSslContext.newSslContext(clientProperties);
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
