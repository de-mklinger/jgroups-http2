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
package de.mklinger.jgroups.http.common;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
public class Closeables {
	public static void closeUnchecked(final AutoCloseable... closeables) {
		try {
			close(closeables);
		} catch (final Exception e) {
			if (e instanceof RuntimeException) {
				throw (RuntimeException)e;
			} else {
				throw new RuntimeException("Error on close", e);
			}
		}
	}

	public static void close(final AutoCloseable... closeables) throws Exception {
		Exception error = null;
		for (final AutoCloseable closeable : closeables) {
			if (closeable != null) {
				try {
					closeable.close();
				} catch (final Exception e) {
					if (error == null) {
						error = e;
					} else {
						error.addSuppressed(e);
					}
				}
			}
		}
		if (error != null) {
			throw error;
		}
	}
}
