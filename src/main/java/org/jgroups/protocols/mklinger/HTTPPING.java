
package org.jgroups.protocols.mklinger;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.PhysicalAddress;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.ManagedOperation;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.Discovery;
import org.jgroups.protocols.PingData;
import org.jgroups.protocols.PingHeader;
import org.jgroups.util.BoundedList;
import org.jgroups.util.NameCache;
import org.jgroups.util.Responses;
import org.jgroups.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The TCPPING protocol defines a static cluster membership. The cluster members are retrieved by
 * directly contacting the members listed in initial_hosts, sending point-to-point discovery requests.
 * <p/>
 * The TCPPING protocol defines a static configuration, which requires that you to know in advance where to find all
 * of the members of your cluster.
 *
 * @author Bela Ban
 */
public class HTTPPING extends Discovery {
	private static final Logger LOG = LoggerFactory.getLogger(HTTPPING.class);

	/* -----------------------------------------    Properties     --------------------------------------- */

	@Property(name="initial_ping_addresses", description="Comma delimited list of host:port pairs to be contacted for initial membership. " +
			"Ideally, all members should be listed. If this is not possible, send_cache_on_join and / or return_entire_cache " +
			"can be set to true",
			converter=InitialPingAddressesConverter.class)
	protected List<PhysicalAddress> initial_ping_addresses = Collections.emptyList();

	@Property(description="max number of hosts to keep beyond the ones in initial_hosts")
	protected int max_dynamic_hosts=2000;
	/* --------------------------------------------- Fields ------------------------------------------------------ */


	/** https://jira.jboss.org/jira/browse/JGRP-989 */
	protected BoundedList<PhysicalAddress> dynamic_hosts;

	static {
		ClassConfigurator.addProtocol((short)2001, HTTPPING.class);
	}

	public HTTPPING() {
	}

	@Override
	public void start() throws Exception {
		LOG.info("Using initial_ping_addresses: {}", initial_ping_addresses);
		super.start();
	}

	@Override
	public boolean isDynamic() {
		return false;
	}

	@ManagedAttribute
	public String getDynamicHostList() {
		return dynamic_hosts.toString();
	}

	@ManagedOperation
	public void clearDynamicHostList() {
		dynamic_hosts.clear();
	}

	@Override
	public void init() throws Exception {
		super.init();
		dynamic_hosts=new BoundedList<>(max_dynamic_hosts);
	}

	@Override
	public Object down(final Event evt) {
		final Object retval=super.down(evt);
		switch(evt.getType()) {
		case Event.VIEW_CHANGE:
			for(final Address logical_addr: view.getMembersRaw()) {
				final PhysicalAddress physical_addr=(PhysicalAddress)down_prot.down(new Event(Event.GET_PHYSICAL_ADDRESS, logical_addr));
				if(physical_addr != null && !initial_ping_addresses.contains(physical_addr)) {
					dynamic_hosts.addIfAbsent(physical_addr);
				}
			}
			break;
		case Event.ADD_PHYSICAL_ADDRESS:
			final Tuple<Address,PhysicalAddress> tuple=evt.getArg();
			final PhysicalAddress physical_addr=tuple.getVal2();
			if(physical_addr != null && !initial_ping_addresses.contains(physical_addr)) {
				dynamic_hosts.addIfAbsent(physical_addr);
			}
			break;
		}
		return retval;
	}

	@Override
	public void discoveryRequestReceived(final Address sender, final String logical_name, final PhysicalAddress physical_addr) {
		super.discoveryRequestReceived(sender, logical_name, physical_addr);
		if(physical_addr != null && !initial_ping_addresses.contains(physical_addr)) {
			dynamic_hosts.addIfAbsent(physical_addr);
		}
	}

	@Override
	public void findMembers(final List<Address> members, final boolean initial_discovery, final Responses responses) {
		PingData        data=null;
		PhysicalAddress physical_addr=null;

		if(!use_ip_addrs || !initial_discovery) {
			physical_addr=(PhysicalAddress)down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));

			// https://issues.jboss.org/browse/JGRP-1670
			data=new PingData(local_addr, false, NameCache.get(local_addr), physical_addr);
			if(members != null && members.size() <= max_members_in_discovery_request) {
				data.mbrs(members);
			}
		}

		final List<PhysicalAddress> cluster_members=new ArrayList<>(initial_ping_addresses.size() + (dynamic_hosts != null? dynamic_hosts.size() : 0) + 5);
		initial_ping_addresses.stream().filter(phys_addr -> !cluster_members.contains(phys_addr)).forEach(cluster_members::add);

		if(dynamic_hosts != null) {
			dynamic_hosts.stream().filter(phys_addr -> !cluster_members.contains(phys_addr)).forEach(cluster_members::add);
		}

		if(use_disk_cache) {
			// this only makes sense if we have PDC below us
			final Collection<PhysicalAddress> list=(Collection<PhysicalAddress>)down_prot.down(new Event(Event.GET_PHYSICAL_ADDRESSES));
			if(list != null) {
				list.stream().filter(phys_addr -> !cluster_members.contains(phys_addr)).forEach(cluster_members::add);
			}
		}

		final PingHeader hdr=new PingHeader(PingHeader.GET_MBRS_REQ).clusterName(cluster_name).initialDiscovery(initial_discovery);
		for(final PhysicalAddress addr: cluster_members) {
			if(addr.equals(physical_addr)) {
				continue;
			}

			// the message needs to be DONT_BUNDLE, see explanation above
			final Message msg=new Message(addr).setFlag(Message.Flag.INTERNAL, Message.Flag.DONT_BUNDLE, Message.Flag.OOB)
					.putHeader(this.id,hdr);
			if(data != null) {
				msg.setBuffer(marshal(data));
			}

			if(async_discovery_use_separate_thread_per_request) {
				timer.execute(() -> sendDiscoveryRequest(msg), sends_can_block);
			} else {
				sendDiscoveryRequest(msg);
			}
		}
	}

	protected void sendDiscoveryRequest(final Message req) {
		try {
			log.trace("%s: sending discovery request to %s", local_addr, req.getDest());
			down_prot.down(req);
		}
		catch(final Throwable t) {
			log.trace("sending discovery request to %s failed: %s", req.dest(), t);
		}
	}
}

