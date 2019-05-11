package de.mklinger.jgroups.http;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public class FreePort {
	public static int get() {
		return get(0);
	}

	public static int get(final int preferred) {
		try (ServerSocket ss = new ServerSocket(preferred)) {
			return ss.getLocalPort();
		} catch (final IOException e) {
			if (preferred == 0) {
				throw new RuntimeException("Error getting free port", e);
			} else {
				return get(0);
			}
		}
	}
}
