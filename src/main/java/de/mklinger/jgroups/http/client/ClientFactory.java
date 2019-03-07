package de.mklinger.jgroups.http.client;

import java.util.Properties;

import de.mklinger.commons.httpclient.HttpClient;

/**
 * @author Marc Klinger - mklinger[at]mklinger[dot]de
 */
public interface ClientFactory {
	HttpClient newClient(Properties clientProperties);
}