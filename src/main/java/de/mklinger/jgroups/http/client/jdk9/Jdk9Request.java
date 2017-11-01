package de.mklinger.jgroups.http.client.jdk9;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.jgroups.http.client.ContentProvider;
import de.mklinger.jgroups.http.client.Request;
import de.mklinger.jgroups.http.client.Response;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import jdk.incubator.http.HttpClient;

public class Jdk9Request implements Request {
	private static final Logger LOG = LoggerFactory.getLogger(Jdk9Request.class);
	
	private HttpRequest.Builder builder;

	private HttpClient httpClient;
	private URI uri;
	private String method = "GET";
	private HttpRequest.BodyProcessor bodyProcessor;

	public Jdk9Request(HttpClient httpClient, URI uri) {
		this.httpClient = httpClient;
		this.uri = uri;
		this.builder = HttpRequest.newBuilder().uri(uri);
	}

	@Override
	public Request method(String method) {
		this.method = method;
		return this;
	}

	@Override
	public Request header(String name, String value) {
		builder.header(name, value);
		return this;
	}

	@Override
	public Request content(ContentProvider contentProvider) {
		long len = 0;
		for (ByteBuffer bb : contentProvider) {
			len = Math.addExact(len, bb.remaining());
		}
		final long contentLength = len;
		
		bodyProcessor = new HttpRequest.BodyProcessor() {
			private volatile Flow.Publisher<ByteBuffer> delegate;

			@Override
			public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
				for (ByteBuffer bb : contentProvider) {
					bb.remaining();
				}
				delegate = new PullPublisher<>(contentProvider);
				delegate.subscribe(subscriber);
			}

	        @Override
	        public long contentLength() {
	        	return contentLength;
	        }
		};

		return this;
	}

	@Override
	public CompletableFuture<Response> send() {
		CompletableFuture<Response> cf = new CompletableFuture<>();
		HttpRequest request = builder.method(method, bodyProcessor).build();
		httpClient.sendAsync(request, HttpResponse.BodyHandler.discard(null))
		.thenAccept(response -> {
				LOG.info("jdk9 http client response complete using {}", response.version());
				cf.complete(new Jdk9Response(response));
			})
		.exceptionally(failure -> {
			cf.completeExceptionally(failure);
			return null;
		});
		return cf;
	}

	@Override
	public URI getUri() {
		return uri;
	}

	@Override
	public String getMethod() {
		return method;
	}
}
