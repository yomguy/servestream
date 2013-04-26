package net.sourceforge.servestream.media;

public class Metadata {

	private String mTitle;
	private String mAlbum;
	private String mArtist;
	private String mDuration;
	private byte [] mArtwork;
	
	/**
	 * Default constructor
	 */
	public Metadata() {
		
	}

	/**
	 * @return the title
	 */
	public String getTitle() {
		return mTitle;
	}

	/**
	 * @param title the title to set
	 */
	public void setTitle(String title) {
		mTitle = title;
	}

	/**
	 * @return the album
	 */
	public String getAlbum() {
		return mAlbum;
	}

	/**
	 * @param album the album to set
	 */
	public void setAlbum(String album) {
		mAlbum = album;
	}

	/**
	 * @return the artist
	 */
	public String getArtist() {
		return mArtist;
	}

	/**
	 * @param artist the artist to set
	 */
	public void setArtist(String artist) {
		mArtist = artist;
	}

	/**
	 * @return the duration
	 */
	public String getDuration() {
		return mDuration;
	}

	/**
	 * @param duration the duration to set
	 */
	public void setDuration(String duration) {
		mDuration = duration;
	}

	/**
	 * @return the artwork
	 */
	public byte [] getArtwork() {
		return mArtwork;
	}

	/**
	 * @param artwork the artwork to set
	 */
	public void setArtwork(byte [] artwork) {
		mArtwork = artwork;
	}
}
