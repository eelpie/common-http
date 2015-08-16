package uk.co.eelpieconsulting.common.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

public class HttpFetcher {
	
	private static final Logger log = Logger.getLogger(HttpFetcher.class);
	
	private static final String UTF_8 = "UTF-8";
	private static final String GZIP = "gzip";
	private static final String ACCEPT_ENCODING = "Accept-Encoding";
	private static final int HTTP_TIMEOUT = 15000;
	
	private final PoolingClientConnectionManager connectionManager;
	private final HttpClient client;

	private final String characterEncoding;

	public HttpFetcher() {
		this(UTF_8);
	}
	
	public HttpFetcher(String characterEncoding) {
	    final SchemeRegistry registry = new SchemeRegistry();
	    registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	    
	    SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();	    
	    sslSocketFactory.setHostnameVerifier(new AllowAllHostnameVerifier());
		registry.register(new Scheme("https", sslSocketFactory, 443));
	    
	    final PoolingClientConnectionManager poolingClientConnectionManager = new PoolingClientConnectionManager(registry);
	    poolingClientConnectionManager.setDefaultMaxPerRoute(5);
	    poolingClientConnectionManager.setMaxTotal(10);
		connectionManager = poolingClientConnectionManager;
		
		client = setupHttpClient();
		this.characterEncoding = characterEncoding;
	}
	
	public String get(String url) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {
		log.debug("Executing GET to: " + url);
		return executeRequestAndReadResponseBody(new HttpGet(url));
	}
	
	public String get(URI uri) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {
		log.debug("Executing GET to: " + uri.toString());
		return executeRequestAndReadResponseBody(new HttpGet(uri));
	}
	
	public String get(String url, Map<String, String> headers) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {
		log.debug("Executing GET to: " + url + " with headers: " + headers);
		HttpGet get = new HttpGet(url);
		for (String header : headers.keySet()) {
			get.addHeader(new BasicHeader(header, headers.get(header)));
		}
		return executeRequestAndReadResponseBody(get);
	}
	
	public String post(HttpPost post) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {
		log.debug("Executing POST to: " + post.getURI());
		return executeRequestAndReadResponseBody(post);		
	}
	public String put(HttpPut put) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {
		log.debug("Executing PUT to: " + put.getURI());
		return executeRequestAndReadResponseBody(put);		
	}
		
	public String delete(HttpDelete delete) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {
		log.debug("Executing DELETE to: " + delete.getURI());
		return executeRequestAndReadResponseBody(delete);		
	}
	
	public byte[] getBytes(String url) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {
		log.debug("Executing GET to: " + url);
		return executeRequestAndReadBytes(new HttpGet(url));
	}
	
	private String executeRequestAndReadResponseBody(final HttpRequestBase request) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {		
		try {
			final byte[] responseBytes = executeRequestAndReadBytes(request);		
			return new String(responseBytes, characterEncoding);
			
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	private byte[] executeRequestAndReadBytes(final HttpRequestBase request) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException  {
		log.debug("Connection stats: " + connectionManager.getTotalStats().toString());
		try {
			final HttpResponse response = executeRequest(request);
			final int statusCode = response.getStatusLine().getStatusCode();
			log.debug("Http response status code is: " + statusCode);

			if (statusCode == HttpStatus.SC_OK) {
				final byte[] byteArray = EntityUtils.toByteArray(response.getEntity());
				return byteArray;
			}
			
			final String responseBody = EntityUtils.toString(response.getEntity());
			if (statusCode == HttpStatus.SC_NOT_FOUND) {
				throw new HttpNotFoundException(responseBody);
				
			} else if (statusCode == HttpStatus.SC_BAD_REQUEST) {
				throw new HttpBadRequestException(responseBody);
				
			} else if (statusCode == HttpStatus.SC_FORBIDDEN) {
				throw new HttpForbiddenException(responseBody);
			}
			
			throw new HttpFetchException(responseBody);
			
		} catch (UnknownHostException e) {	
			log.info("Caught unknown host exception");
			throw new HttpFetchException(e);
			
		} catch (IOException e) {
			log.warn("Throwing general http fetch io exception", e);
			throw new HttpFetchException(e);
		}
	}
	
	private HttpResponse executeRequest(HttpRequestBase request) throws IOException, ClientProtocolException {
		request.addHeader(new BasicHeader(ACCEPT_ENCODING, GZIP));
		return client.execute(request);
	}
	
	private HttpClient setupHttpClient() {
	    HttpClient client = new DefaultHttpClient(connectionManager);	    
		((AbstractHttpClient) client)
				.addRequestInterceptor(new HttpRequestInterceptor() {
					public void process(final HttpRequest request,
							final HttpContext context) throws HttpException,
							IOException {
						if (!request.containsHeader(ACCEPT_ENCODING)) {
							request.addHeader(ACCEPT_ENCODING, GZIP);
						}
					}
				});
		
		((AbstractHttpClient) client).addResponseInterceptor(new HttpResponseInterceptor() {
			public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
				HttpEntity entity = response.getEntity();
				Header ceheader = entity.getContentEncoding();
				if (ceheader != null) {
					HeaderElement[] codecs = ceheader.getElements();
					for (int i = 0; i < codecs.length; i++) {
						if (codecs[i].getName().equalsIgnoreCase(GZIP)) {
							response.setEntity(new GzipDecompressingEntity(response.getEntity()));
							return;
						}
					}
				}
			}
		});
		
		client.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, new Integer(HTTP_TIMEOUT));
		client.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, new Integer(HTTP_TIMEOUT));
		return client;
	}

	private static class GzipDecompressingEntity extends HttpEntityWrapper {

		public GzipDecompressingEntity(final HttpEntity entity) {
			super(entity);
		}

		@Override
		public InputStream getContent() throws IOException, IllegalStateException {
			InputStream wrappedin = wrappedEntity.getContent();
			return new GZIPInputStream(wrappedin);
		}
		
		@Override
		public long getContentLength() {
			return this.wrappedEntity.getContentLength();
		}
	}

}
