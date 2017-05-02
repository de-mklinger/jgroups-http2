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

import org.eclipse.jetty.client.api.Result;

import de.mklinger.jgroups.http.client.Response;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public class JettyResult implements de.mklinger.jgroups.http.client.Result {
	private final Result result;

	public JettyResult(final Result result) {
		this.result = result;
	}

	@Override
	public boolean isFailed() {
		return result.isFailed();
	}

	@Override
	public Throwable getFailure() {
		return result.getFailure();
	}

	@Override
	public Response getResponse() {
		return new JettyResponse(result.getResponse());
	}
}
