package de.mklinger.jgroups.http.client.jdk9;

import jdk.incubator.http.HttpResponse;

public class Jdk9Response implements de.mklinger.jgroups.http.client.Response {
	private HttpResponse<?> response;
	
	public Jdk9Response(HttpResponse<?> response) {
		this.response = response;
	}

	@Override
	public int getStatus() {
		return response.statusCode();
	}
}
