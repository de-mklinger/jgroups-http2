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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Optional;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
public class Keystores {
	private static final String CLASSPATH_PREFIX = "classpath:";

	public static KeyStore load(String location, Optional<String> password) {
		try {
			final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			try(InputStream in = newInputStream(location)) {
				keyStore.load(in, toCharArray(password));
			}
			return keyStore;
		} catch (IOException e) {
			throw new UncheckedIOException("Error loading keystore from location: " + location, e);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
			throw new RuntimeException("Error loading keystore from location: " + location, e);
		}
	}

	private static InputStream newInputStream(String location) throws IOException {
		if (location.startsWith(CLASSPATH_PREFIX)) {
			String classpathLocation = location.substring(CLASSPATH_PREFIX.length());
			InputStream in = Keystores.class.getClassLoader().getResourceAsStream(classpathLocation);
			if (in == null) {
				in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathLocation);
			}
			if (in == null) {
				throw new FileNotFoundException("Classpath resource not found: " + classpathLocation);
			}
			return in;
		} else if (location.startsWith("/") || location.startsWith("./")) {
			return new FileInputStream(location);
		} else {
			return URI.create(location).toURL().openStream();
		}
	}

	private static char[] toCharArray(Optional<String> password) {
		final char[] pwd;
		if (password.isPresent()) {
			pwd = password.get().toCharArray();
		} else {
			pwd = null;
		}
		return pwd;
	}
}
