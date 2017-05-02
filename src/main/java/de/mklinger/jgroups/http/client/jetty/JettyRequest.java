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

import org.eclipse.jetty.client.api.Request;

import de.mklinger.jgroups.http.client.ContentProvider;

public class JettyRequest implements de.mklinger.jgroups.http.client.Request {
	private final Request request;

	public JettyRequest(final Request request) {
		this.request = request;
	}

	@Override
	public de.mklinger.jgroups.http.client.Request header(final String name, final String value) {
		request.header(name, value);
		return this;
	}

	@Override
	public de.mklinger.jgroups.http.client.Request content(final ContentProvider contentProvider) {
		request.content(new JettyContentProvider(contentProvider));
		return this;
	}

	@Override
	public void send(final de.mklinger.jgroups.http.client.CompleteListener completeListener) {
		request.send(new JettyCompleteListener(completeListener));
	}
}
