package org.hcjf.io.net.http;

import org.hcjf.log.Log;
import org.hcjf.properties.SystemProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * This class represents all the king of packages between server and
 * client side in a http communication.
 * @author javaito
 */
public abstract class HttpPackage {

    private static final byte LINE_SEPARATOR_CR = '\r';
    private static final byte LINE_SEPARATOR_LF = '\n';
    public static final String STRING_LINE_SEPARATOR = "\r\n";
    public static final String LINE_FIELD_SEPARATOR = " ";
    public static final String HTTP_FIELD_START = "?";
    public static final String HTTP_FIELD_SEPARATOR = "&";
    public static final String HTTP_FIELD_ASSIGNATION = "=";
    public static final String HTTP_CONTEXT_SEPARATOR = "/";

    private HttpProtocol protocol;
    private String httpVersion;
    private final Map<String, HttpHeader> headers;
    private byte[] body;

    //This fields are only for internal parsing.
    private List<String> lines;
    private boolean onBody;
    private boolean complete;
    private ByteArrayOutputStream currentBody;

    public HttpPackage() {
        this.httpVersion = HttpVersion.VERSION_1_1;
        this.headers = new HashMap<>();
        this.body = new byte[0];
        this.protocol = HttpProtocol.HTTP;
    }

    protected HttpPackage(HttpPackage httpPackage) {
        this.httpVersion = httpPackage.httpVersion;
        this.headers = httpPackage.headers;
        this.body = httpPackage.body;
        this.protocol = httpPackage.protocol;
    }

    /**
     * Return the http protocol.
     * @return Http protocol
     */
    public HttpProtocol getProtocol() {
        return protocol;
    }

    /**
     * Set the http protocol.
     * @param protocol Http protocol
     */
    public void setProtocol(HttpProtocol protocol) {
        this.protocol = protocol;
    }

    /**
     * Return the body of the package.
     * @return Body.
     */
    public final byte[] getBody() {
        return body;
    }

    /**
     * Set the body of the package.
     * @param body Body.
     */
    public final void setBody(byte[] body) {
        this.body = body;
    }

    /**
     * Return the version of the http protocol
     * @return Http protocol version.
     */
    public final String getHttpVersion() {
        return httpVersion;
    }

    /**
     * Set the version of the http protocol
     * @param httpVersion Version of the http protocol
     */
    public final void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    /**
     * Return true if the http package is complete.
     * @return Trus if the package is complete and false in the otherwise.
     */
    public final boolean isComplete() {
        return complete;
    }

    /**
     * Add a new header into a package.
     * @param header New header.
     */
    public final void addHeader(HttpHeader header) {
        if(header == null) {
            throw new NullPointerException("Null header");
        }
        headers.put(header.getHeaderName(), header);
    }

    /**
     * Return in a unmodificable list, all the headers contained
     * into the package.
     * @return List of the headers.
     */
    public final Collection<HttpHeader> getHeaders() {
        return Collections.unmodifiableCollection(headers.values());
    }

    /**
     * Return the header instance with the specific name.
     * @param headerName Name of the founded header.
     * @return Founded header or null if there are'nt any header with this name.
     */
    public final HttpHeader getHeader(String headerName) {
        HttpHeader result = null;
        for(String name : headers.keySet()) {
            if(name.equalsIgnoreCase(headerName)) {
                result = headers.get(name);
            }
        }
        return result;
    }

    /**
     * Verify if the package contains any header with the specific name.
     * @param headerName Finding header name.
     * @return Return true if there are any header with the specific name and false in the otherwise.
     */
    public final boolean containsHeader(String headerName) {
        return getHeader(headerName) != null;
    }

    /**
     * Add a portion of data into the package.
     * @param data Portion of data.
     */
    public final synchronized void addData(byte[] data) {
        if(!complete) {
            if (currentBody == null) {
                currentBody = new ByteArrayOutputStream();
                lines = new ArrayList<>();
                onBody = false;
                complete = false;
            }

            if (onBody) {
                try {
                    currentBody.write(data);
                } catch (IOException ex) {
                }
            } else {
                String line;
                for (int i = 0; i < data.length - 1; i++) {
                    if (data[i] == LINE_SEPARATOR_CR && data[i + 1] == LINE_SEPARATOR_LF) {
                        if (currentBody.size() == 0) {
                            //The previous line is empty
                            //Start body, because there are two CRLF together
                            currentBody.reset();
                            currentBody.write(data, i + 2, data.length - (i + 2));
                            onBody = true;
                            for(int j = 1; j < lines.size(); j++) {
                                addHeader(new HttpHeader(lines.get(j)));
                            }
                            break;
                        } else {
                            //The current body is a new line
                            line = new String(currentBody.toByteArray()).trim();
                            if(!line.isEmpty()) {
                                lines.add(line);
                            }
                            currentBody.reset();
                            i++;
                        }
                    } else {
                        currentBody.write(data[i]);
                    }
                }
            }

            if (onBody) {
                if (bodyDone()) {
                    setBody(currentBody.toByteArray());
                    processFirstLine(lines.get(0));
                    processBody(getBody());
                    currentBody = null;
                    complete = true;
                }
            }
        } else {
            Log.d(SystemProperties.get(SystemProperties.Net.Http.LOG_TAG), "Trying to add data into a complete http package.");
        }
    }

    /**
     * Verify if the body of the package is complete.
     * @return Return true if the body is complete or false in the otherwise
     */
    protected boolean bodyDone() {
        int length = 0;
        HttpHeader contentLengthHeader = getHeader(HttpHeader.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            length = Integer.parseInt(contentLengthHeader.getHeaderValue().trim());
        }

        return currentBody.size() >= length;
    }

    /**
     * Return the body trimmed.
     * @param body Raw body.
     * @return Trimmed body
     */
    protected byte[] trimBody(byte[] body) {
        return body;
    }

    /**
     * This method must be implemented to process the body information.
     * @param body Body information.
     */
    protected abstract void processBody(byte[] body);

    /**
     * This method must be implemented to process the first line of the package.
     * @param firstLine First line of the package.
     */
    protected abstract void processFirstLine(String firstLine);

    /**
     * Return the bytes that represent the string of the protocol name.
     * @return Protocol name bytes.
     */
    public abstract byte[] getProtocolHeader();

    /**
     * Enum with the http protocols
     */
    public enum HttpProtocol {

        HTTP,

        HTTPS

    }

}
