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
package org.jgroups.protocols.mklinger;

import org.jgroups.conf.PropertyConverter;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public class ClassConverter implements PropertyConverter {
	@Override
	public Object convert(final Object obj, final Class<?> propertyFieldType, final String propertyName, final String propertyValue, final boolean check_scope) throws Exception {
		if (!Class.class.isAssignableFrom(propertyFieldType)) {
			throw new IllegalStateException("Property '" + propertyName + "' is not of type java.lang.Class but of type " + propertyFieldType);
		}
		return Class.forName(propertyValue);
	}

	@Override
	public String toString(final Object value) {
		return String.valueOf(value);
	}
}
