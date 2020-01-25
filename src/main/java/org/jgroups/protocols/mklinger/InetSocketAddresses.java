package org.jgroups.protocols.mklinger;

import java.net.InetSocketAddress;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public class InetSocketAddresses {
	/** No instantiation */
	private InetSocketAddresses() {}

	/**
	 * Parse a string in the form &lt;host>:&lt;port> and return the
	 * corresponding {@link InetSocketAddress} instance.
	 */
	public static InetSocketAddress of(final String inetSocketAddress) {
		final int idx = inetSocketAddress.lastIndexOf(":");
		if (idx <= 0 || idx == inetSocketAddress.length() - 1) {
			throw new IllegalArgumentException("Cannot parse inet socket address string: '" + inetSocketAddress + "'");
		}
		final String hostOrIp = inetSocketAddress.substring(0, idx);
		final String port = inetSocketAddress.substring(idx + 1);
		return new InetSocketAddress(InetAddresses.of(hostOrIp), Integer.parseInt(port));
	}
}
