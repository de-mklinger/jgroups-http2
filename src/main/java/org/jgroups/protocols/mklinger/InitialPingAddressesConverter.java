package org.jgroups.protocols.mklinger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.jgroups.PhysicalAddress;
import org.jgroups.conf.PropertyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitialPingAddressesConverter implements PropertyConverter {
	private static final Logger LOG = LoggerFactory.getLogger(InitialPingAddressesConverter.class);

	@Override
	public Object convert(final Object obj, final Class<?> propertyFieldType, final String propertyName, final String propertyValue, final boolean check_scope) throws Exception {
		return toResolveableInitialPingAddresses(propertyValue);
	}

	private static List<PhysicalAddress> toResolveableInitialPingAddresses(final String initialPingAddresses) {
		final List<PhysicalAddress> physicalAddresses = new ArrayList<>();

		final StringTokenizer st = new StringTokenizer(initialPingAddresses, ",");
		while (st.hasMoreTokens()) {
			final String inetSocketAddress = st.nextToken().trim();
			try {
				physicalAddresses.add(new HostAddress(inetSocketAddress));
			} catch (final Exception e) {
				LOG.warn("Ignoring initial ping address '{}': {}", inetSocketAddress, e.toString());
			}
		}

		LOG.info("Using resolveable initial ping addresses: '{}'", physicalAddresses);
		return physicalAddresses;
	}


	@Override
	public String toString(final Object value) {
		return ((Collection<?>) value).stream()
				.map(PhysicalAddress.class::cast)
				.map(PhysicalAddress::printIpAddress)
				.collect(Collectors.joining(","));
	}
}
