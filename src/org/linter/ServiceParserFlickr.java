package org.linter;

import java.util.regex.Pattern;

import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.Source;

public class ServiceParserFlickr extends ServiceParserAlgorithmic {

	public ServiceParserFlickr() {
	}	

	public Pattern getServicePattern() {
		return Pattern.compile( "http://www.flickr.com/.*" );
	}
	
	public boolean parse() {
		super.parse();
		
		Source source = getJerichoSource();
		
		Element imageElement = source.getFirstElement("rel", "image_src", false);
		if( imageElement != null ) {
			String previewImageUrl = imageElement.getAttributeValue( "href" );
			getMetaData().put( "preview-image-url", previewImageUrl );					
		}
		
		return parseWithSuccessor();
	}
		
}
