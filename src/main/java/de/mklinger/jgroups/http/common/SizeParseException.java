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

public class SizeParseException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public SizeParseException() {
	}

	public SizeParseException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public SizeParseException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public SizeParseException(final String message) {
		super(message);
	}

	public SizeParseException(final Throwable cause) {
		super(cause);
	}
}
