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
package de.mklinger.jgroups.http.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jgroups.stack.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mklinger.jgroups.http.common.SizeValue;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public class JGroupsReadListener implements ReadListener {
	private static final Logger LOG = LoggerFactory.getLogger(JGroupsReadListener.class);

	private final AsyncContext asyncContext;
	private final IpAddress sender;
	private final HttpReceiver receiver;
	private final int maxContentLength;
	private final ByteArrayOutputStream data;
	private final byte[] buf = new byte[1024];

	public JGroupsReadListener(final AsyncContext asyncContext, final HttpReceiver receiver, final int maxContentLength) throws BadRequestException {
		this.asyncContext = asyncContext;
		this.receiver = receiver;
		this.maxContentLength = maxContentLength;
		final HttpServletRequest request = (HttpServletRequest) asyncContext.getRequest();
		this.sender = getSender(request);
		this.data = new ByteArrayOutputStream(getBufferSize(request));
	}

	private IpAddress getSender(final HttpServletRequest request) throws BadRequestException {
		try {
			final String senderAddress = Objects.requireNonNull(request.getHeader("X-Sender"), "Missing header 'X-Sender'");
			LOG.debug("Sender: {}", senderAddress);
			return new IpAddress(senderAddress);
		} catch (final Exception e) {
			throw new BadRequestException(e);
		}
	}

	private int getBufferSize(final HttpServletRequest request) {
		final long contentLengthLong = request.getContentLengthLong();
		if (contentLengthLong == -1) {
			LOG.info("No Content-Length header available");
			return 1024;
		} else if (contentLengthLong > maxContentLength) {
			throw new IllegalArgumentException("Content too large: " + new SizeValue(contentLengthLong));
		} else {
			return (int) contentLengthLong;
		}
	}

	@Override
	public void onDataAvailable() throws IOException {
		final ServletInputStream inputStream = asyncContext.getRequest().getInputStream();
		try {
			while (inputStream.isReady()) {
				final int len = inputStream.read(buf);
				if (len == -1) {
					return;
				}
				LOG.debug("Read {} bytes async", len);
				if (data.size() + len > maxContentLength) {
					throw new IllegalArgumentException("Content too large");
				}
				data.write(buf, 0, len);
			}
		} catch (final Exception e) {
			LOG.error("Error in onDataAvailable()", e);
			throw e;
		}
	}

	@Override
	public void onAllDataRead() throws IOException {
		try {
			final byte[] messageData = data.toByteArray();
			LOG.debug("Message read, calling receive()");
			receiver.receive(sender, messageData, 0, messageData.length);
			asyncContext.complete();
		} catch (final Exception e) {
			LOG.error("Error in onAllDataRead()", e);
			throw e;
		}
	}

	@Override
	public void onError(final Throwable t) {
		LOG.error("Error", t);
		try {
			final HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
			response.reset();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.toString());
		} catch (final IOException e) {
			LOG.error("Error trying to send error response", e);
		} finally {
			asyncContext.complete();
		}
	}
}