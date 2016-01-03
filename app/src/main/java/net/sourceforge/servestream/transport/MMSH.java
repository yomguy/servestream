package net.sourceforge.servestream.transport;

import net.sourceforge.servestream.bean.UriBean;

public class MMSH extends MMS {

	private static final String PROTOCOL = "mmsh";
	
	public MMSH() {
		super();
	}
	
	public MMSH(UriBean uri) {
		super(uri);
	}
	
	public static String getProtocolName() {
		return PROTOCOL;
	}	
	
	protected String getPrivateProtocolName() {
		return PROTOCOL;
	}
}
