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

import java.net.URI;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public interface Request {
	Request method(String method);

	Request header(String name, String value);

	Request content(ContentProvider contentProvider);

	void send(CompleteListener completeListener);

	URI getUri();
}
