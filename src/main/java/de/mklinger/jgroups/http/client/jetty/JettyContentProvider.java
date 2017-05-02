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

import java.nio.ByteBuffer;
import java.util.Iterator;

import org.eclipse.jetty.client.api.ContentProvider;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public class JettyContentProvider implements ContentProvider.Typed {
	private final de.mklinger.jgroups.http.client.ContentProvider contentProvider;

	public JettyContentProvider(final de.mklinger.jgroups.http.client.ContentProvider contentProvider) {
		this.contentProvider = contentProvider;
	}

	@Override
	public Iterator<ByteBuffer> iterator() {
		return contentProvider.iterator();
	}

	@Override
	public long getLength() {
		return contentProvider.getLength();
	}

	@Override
	public String getContentType() {
		return contentProvider.getContentType();
	}
}
