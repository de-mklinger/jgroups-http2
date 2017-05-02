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
package de.mklinger.jgroups.http.client;

import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public class BytesContentProvider implements ContentProvider {
	private final String contentType;
	private final byte[] bytes;
	private final int offset;
	private final int length;

	public BytesContentProvider(final String contentType, final byte[] bytes, final int offset, final int length) {
		this.contentType = contentType;
		this.bytes = bytes;
		this.offset = offset;
		this.length = length;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public long getLength() {
		return length;
	}

	@Override
	public Iterator<ByteBuffer> iterator() {
		return new Iterator<ByteBuffer>() {
			private boolean empty;

			@Override
			public boolean hasNext() {
				return !empty;
			}

			@Override
			public ByteBuffer next() {
				empty = true;
				return ByteBuffer.wrap(bytes, offset, length);
			}
		};
	}
}
