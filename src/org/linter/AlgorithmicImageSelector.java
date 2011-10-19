package org.linter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Source;

/*
 * Determine the best Preview Image
 * Algorithm scans HTML and filters out the most likely preview images
 * from a variety of factors
 */
public class AlgorithmicImageSelector {

	static protected Logger logger = Logger.getLogger( AlgorithmicImageSelector.class );
	
	// Minimum size for preview image dimensions
	static final int MIN_PREVIEW_IMAGE_DIM = 75;
	
	// Minimum allowable aspect ratio, prevent skyscrapers
	static final float MIN_ASPECT_RATIO = 0.2f; // 1:5
	
	// Maximum allowable aspect ratio, prevent banners
	static final float MAX_ASPECT_RATIO = 5.0f; // 5:1
	
	// Maximum allowable file size for preview images
	static final int MAX_FILE_SIZE = 100 * 1024;
	
	// Provider for fixing relative URLs (e.g. http://www.facebook.com)
	String _providerUrl;
	
	// Jericho Parser Source
	Source _source;
	
	// Potential set of usable images
	ArrayList<AlgorithmicImageItem> _potentialSet;	
	
	// Logger Prefix
	String _logPrefix;
	
	/*
	 * Constructor
	 * @param source Jericho source
	 * @param providerUrl URL provider (e.g. http://www.facebook.com)
	 */
	public AlgorithmicImageSelector(Source source, String providerUrl, String logPrefix ) {
		_providerUrl = providerUrl;
		_source = source;
		_logPrefix = logPrefix;
	}

	/*
	 * Get Preview URL
	 * Run the algorithmic preview image selector
	 */
	public String getPreviewUrl() {
		logger.trace( "Algorithmically selecting preview image" );
		long timeStart = System.currentTimeMillis();
				
		// Parse URL, Width, Height, Id, Class for each image in the document
		parseAllImages();
		logger.trace( _logPrefix + "Initial potential set length: " + _potentialSet.size() );
		
		// Remove any images with a blacklisted URL (e.g. ad.doubleclick.net)
		removeBlacklistedImages();
		
		// Reduce score for image with dimensions identical to 2+ other images (i.e. thumbnails of the same size)		
		removeMatchingDimensions();
		
		// Reduce score for images matching a standard advertisement size
		reduceScoreByDimension();
		
		// Reduce score for images containing a likely miss name (e.g. button, icon, avatar, logo)
		reduceScoreByMissName();		
		
		// Increase score for images containing a likely hit name (e.g. photo, full, main)
		increaseScoreByHitName();
		
		// Increase score for preferred image formats
		increaseScoreByFormat();		
		
		// Increase score for image with the largest image dimensions
		increaseLargestImageScore();
		
		logger.trace( _logPrefix + "Filtered potential set length: " + _potentialSet.size() );		
	
		// Verify the highest scoring image exists and is larger than the minimum preview dimensions
		String imageUrl = "";
		for( int i = 0; i < 2; i++ ) {
			
			// Pick image with the highest score		
			AlgorithmicImageItem highestScoredImage = getHighestScoredImage();
			
			if( highestScoredImage != null ) {
				
				// Download the image if we do not have any width information
				if( highestScoredImage.getWidth() == AlgorithmicImageItem.UNSPECIFIED_IMAGE_DIM || highestScoredImage.getHeight() == AlgorithmicImageItem.UNSPECIFIED_IMAGE_DIM ) {
					highestScoredImage.downloadImage();
				}
								
				// Verify that the top image is larger than the minimum preview dimensions
				float aspectRatio = highestScoredImage.getAspectRatio();
				if( highestScoredImage.getWidth() > MIN_PREVIEW_IMAGE_DIM && 
					highestScoredImage.getHeight() > MIN_PREVIEW_IMAGE_DIM &&
					aspectRatio >= MIN_ASPECT_RATIO &&
					aspectRatio <= MAX_ASPECT_RATIO &&
					highestScoredImage.getFileSize() <= MAX_FILE_SIZE ) {
					
					// Image is good!
					imageUrl = highestScoredImage.getUrl();
					logger.trace( _logPrefix + "AlgorithmicimageSelector: Selected image with url: " + imageUrl );
					break;
				} else {
					// Image is not good. Retry
					logger.trace( _logPrefix + "AlgorithmicimageSelector: Image has invalid characteristics. Width: " + highestScoredImage.getWidth() + " Height: " + highestScoredImage.getHeight() + " Size: " + highestScoredImage.getFileSize() + " Url: " + highestScoredImage.getUrl() );
					_potentialSet.remove( highestScoredImage );
					
				}
			}
		}
		
		long timeDelta = System.currentTimeMillis() - timeStart;
		logger.trace( _logPrefix + "AlgorithmicimageSelector: Preview image select time: " + timeDelta + "ms" );
		
		return imageUrl;
	}
	
	/*
	 * Parse All Images
	 * Find all images from the Jericho source and add to the potential image set
	 */
	public void parseAllImages() {
		_potentialSet = new ArrayList<AlgorithmicImageItem>();
		
		List<Element> imageElements = _source.getAllElements( HTMLElementName.IMG );
		for( Element element : imageElements ) {
			AlgorithmicImageItem image = new AlgorithmicImageItem();
			image.setId( element.getAttributeValue( "id" ) );
			image.setClass( element.getAttributeValue( "class" ) );
			image.setUrl( element.getAttributeValue( "src" ),  _providerUrl  );
			image.setWidth( element.getAttributeValue( "width" ) );
			image.setHeight( element.getAttributeValue( "height" ) );
			_potentialSet.add( image );			
		}			
	}	
	
	/* 
	 * Remove Blacklisted Images
	 * Remove any URLs matching a blacklist. Obvious advertisements.
	 */
	public void removeBlacklistedImages() {		
		
		// A few choices from massive list on http://someonewhocares.org/hosts/
		final String[] URL_BLACKLIST = {						
			"adfarm.mediaplex.com",
			"adserver.com",
			"ak.imgfarm.com",
			"apmebf.com",
			"click.linksynergy.com",
			"doubleclick.net",
			"fastclick.net",
			"global.msads.net",
			"intellitxt.com",
			"lads.myspace.com",
			"refer.ccbill.com",
			"rmads.msn.com",
			"tkqlhce.com",
			"transfer.go.com",									
			
			"http://ad.",
			"http://ads.",
			"http://banner.",
			"http://banners."									
		};
		
		ArrayList<AlgorithmicImageItem> removeList = new ArrayList<AlgorithmicImageItem>();
		
		for( AlgorithmicImageItem image : _potentialSet ) {
			for( int i = 0; i < URL_BLACKLIST.length; i++ ) {
				if( image.getUrl().indexOf( URL_BLACKLIST[i] ) != -1 ) {
					removeList.add( image );
				}
			}
		}
		
		for( AlgorithmicImageItem image : removeList ) {
			_potentialSet.remove( image );
		}
	}
	
	/*
	 * Remove Images with Matching Dimensions
	 * Remove any images from the potential set if there are 3+ other images
	 * with the same dimensions. These are often preview images for other pages.
	 */
	public void removeMatchingDimensions() {
		
		// Threshold of matching image dimensions where it's safe to start removing
		final int MATCHING_IMAGE_COUNT_THRESHOLD = 3;
		
		// Search through each potential image to get a count for each
		// image dimension. Do not include images with unknown dimensions.
		HashMap<String, Integer> dimensionCount = new HashMap<String, Integer>();
		for( AlgorithmicImageItem image : _potentialSet ) {
			
			int width = image.getWidth();
			int height = image.getHeight();
			
			if( width >= 0 && height >= 0 ) {

				// Add or increment matching count in hash
				String key = getDimensionKey( width, height );
				if( dimensionCount.containsKey( key ) ) {
					Integer value = dimensionCount.get( key );
					dimensionCount.put( key, ++value );	// Increment
				} else {
					dimensionCount.put( key, 1 ); // Add new key
				}
				
			}
		}
		
		// Remove any images with dimensions matching 2+ others
		ArrayList<AlgorithmicImageItem> removeList = new ArrayList<AlgorithmicImageItem>();
		Set<String> keySet = dimensionCount.keySet();
		for( String key : keySet ) {
			if( dimensionCount.get( key ) >= MATCHING_IMAGE_COUNT_THRESHOLD ) {
				for( AlgorithmicImageItem image : _potentialSet ) {
					if( getDimensionKey( image.getWidth(), image.getHeight() ).compareTo( key ) == 0 ) {
						removeList.add( image );
					}
				}
			}				
		}
		
		for( AlgorithmicImageItem image : removeList ) {
			_potentialSet.remove( image );
		}
	}
	
	
	/*
	 * Reduce Score by Dimension
	 * Reduce the score of all potential images matching certain dimensions.
	 * Most are standard advertising image sizes. Also reduces score if smaller
	 * than the minimum preview dimensions. Images with undefined sizes are ignored
	 */
	public void reduceScoreByDimension() {
		// Compare image to standard advertisement sizes
		// http://en.wikipedia.org/wiki/Web_banner
		final int STANDARD_AD_SIZES[][] = { 
				// { width, height }
				
				// Rectangles, Pop-Ups
				{ 300, 250 },	// Medium Rectangle
				{ 250, 250 },	// Square Pop-Up
				{ 240, 400 },	// Vertical Rectangle
				{ 336, 280 },	// Large Rectangle
				{ 180, 150 },	// Rectangle
				{ 300, 100 },	// 3:1 Rectangle
				{ 720, 300 },	// Pop-Under
				
				// Banners and Buttons
				{ 468, 60  },	// Full Banner
				{ 234, 60  },	// Half Banner
				{ 88,  31  },	// Micro Bar
				{ 120, 90  },	// Button 1
				{ 120, 60  },	// Button 2
				{ 120, 240 },	// Vertical Banner
				{ 125, 125 },	// Square Button
				{ 728, 90  },	// Leaderboard
				
				// Skyscrapers
				{ 160, 600 },	// Wide Skyscraper
				{ 120, 600 },	// Skyscraper
				{ 300, 600 }	// Half-Page Ad
		};

		// Loop through each image and change score
		for( AlgorithmicImageItem image : _potentialSet ) {
			for( int i = 0; i < STANDARD_AD_SIZES.length; i++ ) {
				int width = image.getWidth();
				int height = image.getHeight();
				
				if( width == STANDARD_AD_SIZES[i][0] &&
					height == STANDARD_AD_SIZES[i][1] ) {
					image.addToScore( -0.1f );
				} else if( width > 0 && height > 0 && width < MIN_PREVIEW_IMAGE_DIM && height < MIN_PREVIEW_IMAGE_DIM ) {
					image.addToScore( -0.1f );
				}
			}
		}
	}
	
	/*
	 * Reduce Score by Miss Name
	 * Reduce score for any images with an ID or Class name matching
	 * terms commonly associated with non-preview images (e.g. buttons, banners, etc)
	 */
	private void reduceScoreByMissName() {
		
		final String[] MISS_NAMES = {
				"button", 
				"icon",
				"yt", 
				"uix", 
				"avatar", 
				"arrow", 
				"addto",
				"comment",
				"img",
				"author",
				"post",
				"uloaded",
				"imagecache",
				"watch",
				"border",
				"thumbnail",
				"sidebarimage",
				"attachment",
				"trail",
				"logo"				
		};
		
		for( AlgorithmicImageItem image : _potentialSet ) {
			for( int i = 0; i < MISS_NAMES.length; i++ ) {
				if( image.doAttributesContainString( MISS_NAMES[i] ) ) {
					image.addToScore( -0.1f );
				}
			}
		}
	}

	
	/*
	 * Increase Score by Hit Name
	 * Increase score for images with Id or Class names matching
	 * terms commonly associated with preview images
	 */
	private void increaseScoreByHitName() {
		
		String[] MISS_NAMES = {
				"photo",
				"full",
				"main"				
		};
		
		for( AlgorithmicImageItem image : _potentialSet ) {
			for( int i = 0; i < MISS_NAMES.length; i++ ) {
				if( image.doAttributesContainString( MISS_NAMES[i] ) ) {
					image.addToScore( 0.1f );
				}
			}
		}
	}

	/*
	 * Increase Score by Format
	 * Increase score for images matching preferred image extensions
	 */
	private void increaseScoreByFormat() {
		String[] PREFERRED_EXTENSIONS = {
				".jpg"
		};
		
		for( AlgorithmicImageItem image : _potentialSet ) {
			String extension = image.getExtension();
			for( int i = 0; i < PREFERRED_EXTENSIONS.length; i++ ) {
				if( extension.compareTo( PREFERRED_EXTENSIONS[i] ) == 0 ) {
					image.addToScore( 0.1f );
					break;
				}
			}
		}
	}
			
	/*
	 * Increase Largest Image Score
	 * Increase the score for the largest image
	 */
	private void increaseLargestImageScore() {
		AlgorithmicImageItem maxImage = null;
		for( AlgorithmicImageItem image : _potentialSet ) {
			if( maxImage == null || image.getImageArea() > maxImage.getImageArea() ) {
				maxImage = image;
			}
		}
		
		if( maxImage != null ) {
			maxImage.addToScore( 0.1f );
		}
	}

	/*
	 * Get the Highest Scored Image
	 * @return AlgorithmicImageItem with highest score
	 */
	private AlgorithmicImageItem getHighestScoredImage() {
		AlgorithmicImageItem maxImage = null;
		for( AlgorithmicImageItem image : _potentialSet ) {
			if( maxImage == null || image.getScore() > maxImage.getScore() ) {
				maxImage = image;
			}
		}
		return maxImage;
	}
	
	/*
	 * Get Dimension Key
	 * Key used for matching images with like dimensions
	 */
	private String getDimensionKey( int width, int height ) {
		return width + "_" + height;
	}
}
