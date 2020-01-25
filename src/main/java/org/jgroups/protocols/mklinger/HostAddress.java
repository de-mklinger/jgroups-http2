package org.jgroups.protocols.mklinger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.function.Supplier;

import org.jgroups.Global;
import org.jgroups.stack.IpAddress;

public class HostAddress extends IpAddress {
	public HostAddress() {
	}

	public HostAddress(final IpAddress ipAddress) {
		super(ipAddress.getIpAddress(), ipAddress.getPort());
	}

	public HostAddress(final InetAddress i, final int p) {
		super(i, p);
	}

	public HostAddress(final InetSocketAddress sock_addr) {
		super(sock_addr);
	}

	/** e.g. 192.168.1.5:7800 or localhost/127.0.0.1:8080 */
	public HostAddress(final String addr_port) throws Exception {
		final int lastColonIdx = addr_port.lastIndexOf(':');
		String withoutPort;
		if(lastColonIdx == -1) {
			withoutPort = addr_port;
		} else {
			withoutPort = addr_port.substring(0, lastColonIdx);
			port = Integer.valueOf(addr_port.substring(lastColonIdx+1));
		}

		String hostName;
		String ip;
		final int firstSlashIdx = withoutPort.indexOf('/');
		if (firstSlashIdx == -1) {
			hostName = null;
			ip = withoutPort;
		} else {
			hostName = withoutPort.substring(0, firstSlashIdx);
			if (hostName.isEmpty()) {
				hostName = null;
			}
			ip = withoutPort.substring(firstSlashIdx + 1);
		}

		final byte[] ipBytes = InetAddresses.ipStringToBytes(ip);
		if (ipBytes == null) {
			ip_addr = InetAddress.getByName(ip);
		} else {
			ip_addr = InetAddress.getByAddress(hostName, ipBytes);
		}
	}

	@Override
	public Supplier<? extends IpAddress> create() {
		return HostAddress::new;
	}

	@Override
	public HostAddress copy() {
		return new HostAddress(ip_addr, port);
	}

	@Override
	public String printIpAddress() {
		return Objects.toString(ip_addr, "<null>") + ":" + port;
	}

	@Override
	public String toString() {
		return printIpAddress();
	}

	@Override
	public void writeTo(final DataOutput out) throws Exception {
		writeHostName(out);
		writeIp(out);
		writePort(out);
	}

	private void writeHostName(final DataOutput out) throws IOException {
		final String hostName = getHostName();
		if (hostName == null) {
			out.writeInt(0);
		} else {
			out.writeInt(hostName.length());
			out.writeChars(hostName);
		}
	}

	public String getHostName() {
		final String s = ip_addr.toString();
		final int idx = s.indexOf('/');
		if (idx > 0) {
			return s.substring(0, idx);
		} else {
			return null;
		}
	}

	private void writeIp(final DataOutput out) throws IOException {
		if(ip_addr != null) {
			final byte[] address=ip_addr.getAddress();  // 4 bytes (IPv4) or 16 bytes (IPv6)
			out.writeByte(address.length); // 1 byte
			out.write(address, 0, address.length);
			if(ip_addr instanceof Inet6Address) {
				out.writeInt(((Inet6Address)ip_addr).getScopeId());
			}
		}
		else {
			out.writeByte(0);
		}
	}

	private void writePort(final DataOutput out) throws IOException {
		out.writeShort(port);
	}

	@Override
	public void readFrom(final DataInput in) throws Exception {
		readHostNameAndIp(in);
		readPort(in);
	}

	private void readHostNameAndIp(final DataInput in) throws IOException {
		final String hostName = doReadHostName(in);

		final int len=in.readByte();
		if(len > 0 && (len != Global.IPV4_SIZE && len != Global.IPV6_SIZE)) {
			throw new IOException("length has to be " + Global.IPV4_SIZE + " or " + Global.IPV6_SIZE + " bytes (was " +
					len + " bytes)");
		}
		final byte[] a = new byte[len]; // 4 bytes (IPv4) or 16 bytes (IPv6)
		in.readFully(a);
		if(len == Global.IPV6_SIZE) {
			final int scope_id=in.readInt();
			this.ip_addr=Inet6Address.getByAddress(hostName, a, scope_id);
		}
		else {
			this.ip_addr=InetAddress.getByAddress(hostName, a);
		}
	}

	private String doReadHostName(final DataInput in) throws IOException {
		final int len = in.readInt();
		if (len == 0) {
			return null;
		}
		final char[] buf = new char[len];
		for (int i = 0; i < buf.length; i++) {
			buf[i] = in.readChar();
		}
		return new String(buf);
	}

	private void readPort(final DataInput in) throws IOException {
		// changed from readShort(): we need the full 65535, with a short we'd only get up to 32K !
		port=in.readUnsignedShort();
	}

	@Override
	public int serializedSize() {
		// length (1 bytes) + 4 bytes for port
		int tmp_size=Global.BYTE_SIZE+ Global.SHORT_SIZE;
		if(ip_addr != null) {
			// 4 bytes for IPv4, 20 for IPv6 (16 + 4 for scope-id)
			tmp_size+=(ip_addr instanceof Inet4Address)? 4 : 20;
		}
		return tmp_size;
	}
}
