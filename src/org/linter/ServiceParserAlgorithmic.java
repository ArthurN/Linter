package org.linter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.htmlparser.jericho.CharacterReference;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Source;

/*
 * Basic Service Parser
 * Pulls basic meta data from all providers
 */
public class ServiceParserAlgorithmic extends ServiceParser {

	protected String _logPrefix;
	
	public ServiceParserAlgorithmic() {
	}
	
	/*
	 * Initialize ServiceParser
	 * Not included in constructor for simplicity when dynamically instantiating classes
	 */
	public void initialize(String url) {
		super.initialize( url );
		_logPrefix = "[" + _url + "] ";
	}
	
	/*
	 * Get generic service pattern
	 * @return Pattern matching anything
	 */
	@Override public Pattern getServicePattern() {
		return null;
	}	
	
	/*
	 * Parse meta data
	 * @return 
	 */
	@Override public boolean parse() {		
		Source source = getJerichoSource();
		
		parseTitle( source );
		parseDescription( source );
		parseFavIconUrl( source );	
		
		return true;
	}
	
	protected boolean parseTitle( Source source ) {
		logger.trace(_logPrefix + "Scraping page title...");
		
		// Page title
		String title = "";
		boolean success = false;
		
		try {
			Element titleElement = source.getFirstElement(HTMLElementName.TITLE);
			if (titleElement != null) {
				title = CharacterReference.decodeCollapseWhiteSpace(titleElement.getContent());
				logger.trace(_logPrefix + "TITLE: " + title);
			}
			else {
				logger.trace(_logPrefix + "Could not extract the page title");
			}
		} catch (Exception ex) {
			logger.error(_logPrefix + "Error extracting page title: ", ex);
		}
		
		getMetaData().put( "title", title );
		
		return success;
	}
	
	protected boolean parseDescription( Source source ) {
		logger.trace(_logPrefix + "Scraping description...");
		
		// Description
		// NOTE: we assume that the first element with attribute name="description" is the meta description tag; else this will fail
		String description = "";
		boolean success = false;
		
		try {
			Element descElement = source.getFirstElement("name", "description", false);
			if (descElement != null && descElement.getName().equalsIgnoreCase(HTMLElementName.META)) {
				String contentAttr = descElement.getAttributeValue("content");
				if (contentAttr != null)
					description = CharacterReference.decodeCollapseWhiteSpace(contentAttr);
			}
			
			if (description != null) {
				logger.trace(_logPrefix + "DESCRIPTION: " + description);
			} else {
				logger.trace(_logPrefix + "Could not extract the page description");
			}
		} catch (Exception ex) {
			logger.error(_logPrefix + "Error extracting page description: ", ex);
		}
				
		logger.trace(_logPrefix + "Scraping complete.");
		
		getMetaData().put( "description", description );
		
		return success;
	}
	
	protected boolean parseFavIconUrl( Source source ) {
		logger.trace(_logPrefix + "Scraping favicon URL...");
		
		// Favicon
		String favIconUrl = "";
		boolean success = false;
		
		try {
			// Get a list of all 'icon' and 'shortcut icon' elements
			List<Element> relIconElements = new ArrayList<Element>();
			relIconElements.addAll(source.getAllElements("rel", "icon", false));
			relIconElements.addAll(source.getAllElements("rel", "shortcut icon", false));
			
			for (Element element : relIconElements) {
				if (element.getName().equalsIgnoreCase(HTMLElementName.LINK)) {
					String hrefAttr = element.getAttributeValue("href");
					if (hrefAttr != null) {
						favIconUrl = CharacterReference.decodeCollapseWhiteSpace(hrefAttr);
						break;
					}
				}
			}
			
			if (favIconUrl != null) {
				if (!favIconUrl.contains(RELATIVE_URL_TEST)) {
					logger.trace("Relative URL for favicon. Prefixing provider URL: " + getProviderUrl());
					favIconUrl = getProviderUrl() + favIconUrl;
				}
				
				logger.trace(_logPrefix + "FAVICON URL: " + favIconUrl);
			} else {
				logger.trace("[" + _url + "] Could not extract the fav icon URL");
			}
		} catch (Exception ex) {
			logger.error(_logPrefix + "Error extracting fav icon URL: ", ex);
		}
		
		getMetaData().put( "favIconUrl", favIconUrl );
		
		return success;
	}
	
}
