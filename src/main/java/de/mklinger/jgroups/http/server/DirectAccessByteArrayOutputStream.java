package de.mklinger.jgroups.http.server;

import java.io.ByteArrayOutputStream;

public class DirectAccessByteArrayOutputStream extends ByteArrayOutputStream {
	public DirectAccessByteArrayOutputStream() {
	}

	public DirectAccessByteArrayOutputStream(int size) {
		super(size);
	}

	public byte[] getBuffer() {
		return buf;
	}
}
