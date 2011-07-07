package com.linter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;

import org.apache.log4j.Logger;

public class Linter {
	static private Logger logger = Logger.getLogger(Linter.class);
	
	public static final int HTTP_CONNECT_TIMEOUT = 5000;	// 5 sec
	
	
	/***
	 * Run Linter as a command-line to optionally test URLs via CLI
	 * @param args The URLs to resolve
	 */
	public static void main(String[] args) {		
		if (args.length == 0) {
			System.out.println("Invalid number of arguments. You must include at least one URL to process.");
			System.exit(1);
		}
		
		logger.info("Running Linter");
		
		LintedPage lp;
		for (int i = 0; i < args.length; i++)
		{
			lp = Linter.processUrl(args[i]);
			System.out.println(lp.toDebugString());
		}
	}
	
	public static final LintedPage processUrl(String url) {
		LintedPage lp = new LintedPage(url);
		lp.process();
		return lp;
	}
	
	/***
	 * Follows URLs starting from address and returns a list of aliases (hops), with the last one being the destination. If the returned
	 * ArrayList of aliases has no entries, then the source address is the destination address. This method will return null
	 * on an error.
	 * @param address - The source URL 
	 * @return an ArrayList of aliases from the source URL to the destination (including the destination)
	 */
	public static ArrayList<String> expandShortenedUrls(String address) {
		ArrayList<String> aliases = new ArrayList<String>();
		
		String locationRedirect = address;
		
		while (locationRedirect != null) {
			try {
				URL url = new URL(locationRedirect);
				
				logger.trace("Following " + locationRedirect + "...");
				
				HttpURLConnection connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
				connection.setInstanceFollowRedirects(false);
				connection.setRequestMethod("HEAD");
				connection.setConnectTimeout(Linter.HTTP_CONNECT_TIMEOUT);
				connection.connect();
				
				locationRedirect = connection.getHeaderField("Location");
				if (locationRedirect != null) {
					logger.debug("Discovered redirect to " + locationRedirect);
					aliases.add(locationRedirect);
				} else {
					logger.debug("URL resolved to its destination");
				}
				connection.disconnect();
			} catch (MalformedURLException ex) {
				logger.error("Invalid URL [" + locationRedirect + "]: " + ex.getMessage());
				return null;
			} catch (IOException ioe) {
				logger.error("IO Exception [" + locationRedirect + "]: " + ioe.getMessage());
				return null;
			}
		}
		
		return aliases;
	}
}
