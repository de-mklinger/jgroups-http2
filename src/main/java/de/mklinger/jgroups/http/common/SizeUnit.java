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
 *
 */
public enum SizeUnit {
	SINGLE {
		@Override
		public long toSingles(final long size) {
			return size;
		}

		@Override
		public long toKilo(final long size) {
			return size / (C1 / C0);
		}

		@Override
		public long toMega(final long size) {
			return size / (C2 / C0);
		}

		@Override
		public long toGiga(final long size) {
			return size / (C3 / C0);
		}

		@Override
		public long toTera(final long size) {
			return size / (C4 / C0);
		}

		@Override
		public long toPeta(final long size) {
			return size / (C5 / C0);
		}
	},
	KILO {
		@Override
		public long toSingles(final long size) {
			return x(size, C1 / C0, MAX / (C1 / C0));
		}

		@Override
		public long toKilo(final long size) {
			return size;
		}

		@Override
		public long toMega(final long size) {
			return size / (C2 / C1);
		}

		@Override
		public long toGiga(final long size) {
			return size / (C3 / C1);
		}

		@Override
		public long toTera(final long size) {
			return size / (C4 / C1);
		}

		@Override
		public long toPeta(final long size) {
			return size / (C5 / C1);
		}
	},
	MEGA {
		@Override
		public long toSingles(final long size) {
			return x(size, C2 / C0, MAX / (C2 / C0));
		}

		@Override
		public long toKilo(final long size) {
			return x(size, C2 / C1, MAX / (C2 / C1));
		}

		@Override
		public long toMega(final long size) {
			return size;
		}

		@Override
		public long toGiga(final long size) {
			return size / (C3 / C2);
		}

		@Override
		public long toTera(final long size) {
			return size / (C4 / C2);
		}

		@Override
		public long toPeta(final long size) {
			return size / (C5 / C2);
		}
	},
	GIGA {
		@Override
		public long toSingles(final long size) {
			return x(size, C3 / C0, MAX / (C3 / C0));
		}

		@Override
		public long toKilo(final long size) {
			return x(size, C3 / C1, MAX / (C3 / C1));
		}

		@Override
		public long toMega(final long size) {
			return x(size, C3 / C2, MAX / (C3 / C2));
		}

		@Override
		public long toGiga(final long size) {
			return size;
		}

		@Override
		public long toTera(final long size) {
			return size / (C4 / C3);
		}

		@Override
		public long toPeta(final long size) {
			return size / (C5 / C3);
		}
	},
	TERA {
		@Override
		public long toSingles(final long size) {
			return x(size, C4 / C0, MAX / (C4 / C0));
		}

		@Override
		public long toKilo(final long size) {
			return x(size, C4 / C1, MAX / (C4 / C1));
		}

		@Override
		public long toMega(final long size) {
			return x(size, C4 / C2, MAX / (C4 / C2));
		}

		@Override
		public long toGiga(final long size) {
			return x(size, C4 / C3, MAX / (C4 / C3));
		}

		@Override
		public long toTera(final long size) {
			return size;
		}

		@Override
		public long toPeta(final long size) {
			return size / (C5 / C0);
		}
	},
	PETA {
		@Override
		public long toSingles(final long size) {
			return x(size, C5 / C0, MAX / (C5 / C0));
		}

		@Override
		public long toKilo(final long size) {
			return x(size, C5 / C1, MAX / (C5 / C1));
		}

		@Override
		public long toMega(final long size) {
			return x(size, C5 / C2, MAX / (C5 / C2));
		}

		@Override
		public long toGiga(final long size) {
			return x(size, C5 / C3, MAX / (C5 / C3));
		}

		@Override
		public long toTera(final long size) {
			return x(size, C5 / C4, MAX / (C5 / C4));
		}

		@Override
		public long toPeta(final long size) {
			return size;
		}
	};

	static final long C0 = 1L;
	static final long C1 = C0 * 1000L;
	static final long C2 = C1 * 1000L;
	static final long C3 = C2 * 1000L;
	static final long C4 = C3 * 1000L;
	static final long C5 = C4 * 1000L;

	static final long MAX = Long.MAX_VALUE;

	/**
	 * Scale d by m, checking for overflow.
	 * This has a short name to make above code more readable.
	 */
	static long x(final long d, final long m, final long over) {
		if (d > over) {
			return Long.MAX_VALUE;
		}
		if (d < -over) {
			return Long.MIN_VALUE;
		}
		return d * m;
	}


	public abstract long toSingles(long size);

	public abstract long toKilo(long size);

	public abstract long toMega(long size);

	public abstract long toGiga(long size);

	public abstract long toTera(long size);

	public abstract long toPeta(long size);
}