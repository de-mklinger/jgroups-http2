package de.mklinger.jgroups.http;

import org.jgroups.protocols.mklinger.HostAddress;
import org.jgroups.protocols.mklinger.InetAddresses;
import org.junit.Assert;
import org.junit.Test;

public class HostAddressTest {
	@Test
	public void testLocalhost() throws Exception {
		final HostAddress localhost = new HostAddress("localhost");
		Assert.assertEquals("localhost", localhost.getHostName());
		Assert.assertEquals(0, localhost.getPort());
	}

	@Test
	public void testLocalhostWithPort() throws Exception {
		final HostAddress localhost = new HostAddress("localhost:1234");
		Assert.assertEquals("localhost", localhost.getHostName());
		Assert.assertEquals(1234, localhost.getPort());
	}

	@Test
	public void testPhantasy() throws Exception {
		final HostAddress localhost = new HostAddress("localhost/99.88.77.66");
		Assert.assertEquals("localhost", localhost.getHostName());
		Assert.assertArrayEquals(InetAddresses.ipStringToBytes("99.88.77.66"), localhost.getIpAddress().getAddress());
		Assert.assertEquals(0, localhost.getPort());
	}

	@Test
	public void testPhantasyWithPort() throws Exception {
		final HostAddress localhost = new HostAddress("localhost/99.88.77.66:123");
		Assert.assertEquals("localhost", localhost.getHostName());
		Assert.assertArrayEquals(InetAddresses.ipStringToBytes("99.88.77.66"), localhost.getIpAddress().getAddress());
		Assert.assertEquals(123, localhost.getPort());
	}
}
