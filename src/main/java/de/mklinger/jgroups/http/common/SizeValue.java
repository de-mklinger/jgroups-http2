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

import java.util.Locale;

public class SizeValue {
	private final long size;
	private final SizeUnit sizeUnit;

	public SizeValue(final long singles) {
		this(singles, SizeUnit.SINGLE);
	}

	public SizeValue(final long size, final SizeUnit sizeUnit) {
		if (size < 0) {
			throw new IllegalArgumentException("size in SizeValue may not be negative");
		}
		this.size = size;
		this.sizeUnit = sizeUnit;
	}

	public long singles() {
		return sizeUnit.toSingles(size);
	}

	public long getSingles() {
		return singles();
	}

	public long kilo() {
		return sizeUnit.toKilo(size);
	}

	public long getKilo() {
		return kilo();
	}

	public long mega() {
		return sizeUnit.toMega(size);
	}

	public long getMega() {
		return mega();
	}

	public long giga() {
		return sizeUnit.toGiga(size);
	}

	public long getGiga() {
		return giga();
	}

	public long tera() {
		return sizeUnit.toTera(size);
	}

	public long getTera() {
		return tera();
	}

	public long peta() {
		return sizeUnit.toPeta(size);
	}

	public long getPeta() {
		return peta();
	}

	public double kiloFrac() {
		return ((double) singles()) / SizeUnit.C1;
	}

	public double getKiloFrac() {
		return kiloFrac();
	}

	public double megaFrac() {
		return ((double) singles()) / SizeUnit.C2;
	}

	public double getMegaFrac() {
		return megaFrac();
	}

	public double gigaFrac() {
		return ((double) singles()) / SizeUnit.C3;
	}

	public double getGigaFrac() {
		return gigaFrac();
	}

	public double teraFrac() {
		return ((double) singles()) / SizeUnit.C4;
	}

	public double getTeraFrac() {
		return teraFrac();
	}

	public double petaFrac() {
		return ((double) singles()) / SizeUnit.C5;
	}

	public double getPetaFrac() {
		return petaFrac();
	}

	@Override
	public String toString() {
		final long singles = singles();
		double value = singles;
		String suffix = "";
		if (singles >= SizeUnit.C5) {
			value = petaFrac();
			suffix = "p";
		} else if (singles >= SizeUnit.C4) {
			value = teraFrac();
			suffix = "t";
		} else if (singles >= SizeUnit.C3) {
			value = gigaFrac();
			suffix = "g";
		} else if (singles >= SizeUnit.C2) {
			value = megaFrac();
			suffix = "m";
		} else if (singles >= SizeUnit.C1) {
			value = kiloFrac();
			suffix = "k";
		}

		return String.format(Locale.US, "%.1f%s", value, suffix);
	}

	public static SizeValue parseSizeValue(final String sValue) throws SizeParseException {
		return parseSizeValue(sValue, null);
	}

	public static SizeValue parseSizeValue(final String sValue, final SizeValue defaultValue) throws SizeParseException {
		if (sValue == null) {
			return defaultValue;
		}
		long singles;
		try {
			if (sValue.endsWith("b")) {
				singles = Long.parseLong(sValue.substring(0, sValue.length() - 1));
			} else if (sValue.endsWith("k") || sValue.endsWith("K")) {
				singles = (long) (Double.parseDouble(sValue.substring(0, sValue.length() - 1)) * SizeUnit.C1);
			} else if (sValue.endsWith("m") || sValue.endsWith("M")) {
				singles = (long) (Double.parseDouble(sValue.substring(0, sValue.length() - 1)) * SizeUnit.C2);
			} else if (sValue.endsWith("g") || sValue.endsWith("G")) {
				singles = (long) (Double.parseDouble(sValue.substring(0, sValue.length() - 1)) * SizeUnit.C3);
			} else if (sValue.endsWith("t") || sValue.endsWith("T")) {
				singles = (long) (Double.parseDouble(sValue.substring(0, sValue.length() - 1)) * SizeUnit.C4);
			} else if (sValue.endsWith("p") || sValue.endsWith("P")) {
				singles = (long) (Double.parseDouble(sValue.substring(0, sValue.length() - 1)) * SizeUnit.C5);
			} else {
				singles = Long.parseLong(sValue);
			}
		} catch (final NumberFormatException e) {
			throw new SizeParseException("failed to parse '" + sValue + "'", e);
		}
		return new SizeValue(singles, SizeUnit.SINGLE);
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final SizeValue sizeValue = (SizeValue) o;

		if (size != sizeValue.size) {
			return false;
		}
		if (sizeUnit != sizeValue.sizeUnit) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int result = Long.hashCode(size);
		result = 31 * result + (sizeUnit != null ? sizeUnit.hashCode() : 0);
		return result;
	}
}