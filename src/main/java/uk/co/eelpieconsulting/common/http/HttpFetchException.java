package uk.co.eelpieconsulting.common.http;

public class HttpFetchException extends Exception {

	private static final long serialVersionUID = 1L;
	
	private Exception cause;
	
	public HttpFetchException(Exception cause) {
		this.cause = cause;
	}

	public Exception getCause() {
		return cause;
	}
	
}
