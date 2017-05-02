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

import java.util.Properties;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public interface HttpClient extends AutoCloseable {
	String KEYSTORE_LOCATION = "ssl.key-store";
	String KEYSTORE_PASSWORD = "ssl.key-store-password";
	String KEY_PASSWORD = "ssl.key-password";
	String TRUSTSTORE_LOCATION = "ssl.trust-store";
	String TRUSTSTORE_PASSWORD = "ssl.trust-store-password";

	void configure(Properties clientProperties);

	void start();

	@Override
	void close();

	Request newRequest(String url);
}
