package net.sourceforge.servestream.transport;

import net.sourceforge.servestream.bean.UriBean;

public class MMST extends MMS {

	private static final String PROTOCOL = "mmst";
	
	public MMST() {
		super();
	}
	
	public MMST(UriBean uri) {
		super(uri);
	}
	
	public static String getProtocolName() {
		return PROTOCOL;
	}	
	
	protected String getPrivateProtocolName() {
		return PROTOCOL;
	}
}
