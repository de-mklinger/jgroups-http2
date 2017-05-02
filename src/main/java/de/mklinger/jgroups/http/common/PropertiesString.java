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

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de - klingerm
 */
public class PropertiesString {
	public static Properties fromString(String props, String separator) {
		Objects.requireNonNull(props);
		Objects.requireNonNull(separator);
		String sepProps;
		if ("\n".equals(separator) || "\r\n".equals(separator)) {
			sepProps = props;
		} else {
			sepProps = props.replaceAll("\\s*" + Pattern.quote(separator) + "\\s*", "\n");
		}
		Properties properties = new Properties();
		try (StringReader sr = new StringReader(sepProps)) {
			properties.load(sr);
		} catch (IOException e) {
			// should never happen
			throw new UncheckedIOException(e);
		}
		return properties;
	}
}
