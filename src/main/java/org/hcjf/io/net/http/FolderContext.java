package org.hcjf.io.net.http;

import org.hcjf.encoding.MimeType;
import org.hcjf.errors.Errors;
import org.hcjf.properties.SystemProperties;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * This class publish some local folder in the web environment.
 * @author javaito
 */
public class FolderContext extends Context {

    private final Path baseFolder;
    private final String name;
    private final String defaultFile;
    private final String[] names;
    private final MessageDigest messageDigest;

    public FolderContext(String name, Path baseFolder, String defaultFile) {
        super(START_CONTEXT + URI_FOLDER_SEPARATOR + name + END_CONTEXT);

        if(baseFolder == null) {
            throw new NullPointerException(Errors.getMessage(Errors.ORG_HCJF_IO_NET_HTTP_1));
        }

        if(!baseFolder.toFile().exists()) {
            throw new IllegalArgumentException(Errors.getMessage(Errors.ORG_HCJF_IO_NET_HTTP_2));
        }

        this.name = name;
        this.baseFolder = baseFolder;
        this.defaultFile = defaultFile;
        this.names = name.split(URI_FOLDER_SEPARATOR);
        try {
            this.messageDigest = MessageDigest.getInstance(
                    SystemProperties.get(SystemProperties.Net.Http.DEFAULT_FILE_CHECKSUM_ALGORITHM));
        } catch (Exception ex) {
            throw new IllegalArgumentException(Errors.getMessage(Errors.ORG_HCJF_IO_NET_HTTP_9), ex);
        }
    }

    public FolderContext(String name, Path baseFolder) {
        this(name, baseFolder, null);
    }

    /**
     * This method is called when there comes a http package addressed to this
     * context.
     *
     * @param request All the request information.
     * @return Return an object with all the response information.
     */
    @Override
    public HttpResponse onContext(HttpRequest request) {
        List<String> elements = request.getPathParts();
        for(String forbidden : SystemProperties.getList(SystemProperties.Net.Http.Folder.FORBIDDEN_CHARACTERS)) {
            for(String element : elements) {
                if (element.contains(forbidden)) {
                    throw new IllegalArgumentException(Errors.getMessage(Errors.ORG_HCJF_IO_NET_HTTP_3, forbidden, request.getContext()));
                }
            }
        }

        //The first value is a empty value, and the second value is the base public context.
        Path path = baseFolder.toAbsolutePath();
        boolean emptyElement = true;
        for(String element : elements) {
            if(!element.isEmpty() && Arrays.binarySearch(names, element) < 0) {
                path = path.resolve(element);
                emptyElement = false;
            }
        }
        if(emptyElement && defaultFile != null) {
            path = path.resolve(defaultFile);
        }

        HttpResponse response = new HttpResponse();

        File file = path.toFile();
        if(file.exists()) {
            if (file.isDirectory()) {
                StringBuilder list = new StringBuilder();
                for(File subFile : file.listFiles()) {
                    list.append(String.format(SystemProperties.get(SystemProperties.Net.Http.Folder.DEFAULT_HTML_ROW),
                            path.relativize(baseFolder).resolve(request.getContext()).resolve(subFile.getName()).toString(),
                            subFile.getName()));
                }
                String htmlBody = String.format(SystemProperties.get(SystemProperties.Net.Http.Folder.DEFAULT_HTML_BODY), list.toString());
                String document = String.format(SystemProperties.get(SystemProperties.Net.Http.Folder.DEFAULT_HTML_DOCUMENT), file.getName(), htmlBody);
                byte[] body = document.getBytes();
                response.addHeader(new HttpHeader(HttpHeader.CONTENT_LENGTH, Integer.toString(body.length)));
                response.addHeader(new HttpHeader(HttpHeader.CONTENT_TYPE, MimeType.HTML));
                response.setResponseCode(HttpResponseCode.OK);
                response.setBody(body);
            } else {
                byte[] body;
                String checksum;
                try {
                    body = Files.readAllBytes(file.toPath());
                    synchronized (this) {
                        checksum = new String(Base64.getEncoder().encode(messageDigest.digest(body)));
                        messageDigest.reset();
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(Errors.getMessage(Errors.ORG_HCJF_IO_NET_HTTP_4, Paths.get(request.getContext(), file.getName())), ex);
                }

                Integer responseCode = HttpResponseCode.OK;
                HttpHeader ifNonMatch = request.getHeader(HttpHeader.IF_NONE_MATCH);
                if(ifNonMatch != null) {
                    if(checksum.equals(ifNonMatch.getHeaderValue())) {
                        responseCode = HttpResponseCode.NOT_MODIFIED;
                    }
                }

                String[] nameExtension = file.getName().split(SystemProperties.get(SystemProperties.Net.Http.Folder.FILE_EXTENSION_REGEX));
                String extension = nameExtension.length == 2 ? nameExtension[1] : MimeType.BIN;
                response.setResponseCode(responseCode);
                MimeType mimeType = MimeType.fromSuffix(extension);
                response.addHeader(new HttpHeader(HttpHeader.CONTENT_TYPE, mimeType == null ? MimeType.BIN : mimeType.toString()));
                response.addHeader(new HttpHeader(HttpHeader.E_TAG, checksum));
                response.addHeader(new HttpHeader(HttpHeader.LAST_MODIFIED,
                        SystemProperties.getDateFormat(SystemProperties.Net.Http.RESPONSE_DATE_HEADER_FORMAT_VALUE).
                                format(new Date(file.lastModified()))));

                if(responseCode.equals(HttpResponseCode.OK)) {
                    HttpHeader acceptEncodingHeader = request.getHeader(HttpHeader.ACCEPT_ENCODING);
                    if(acceptEncodingHeader != null) {
                        boolean notAcceptable = true;
                        for(String group : acceptEncodingHeader.getGroups()) {
                            if (group.equalsIgnoreCase(HttpHeader.GZIP) || group.equalsIgnoreCase(HttpHeader.DEFLATE)) {
                                try (ByteArrayOutputStream out = new ByteArrayOutputStream(); GZIPOutputStream gzipOutputStream = new GZIPOutputStream(out)) {
                                    gzipOutputStream.write(body);
                                    gzipOutputStream.flush();
                                    gzipOutputStream.finish();
                                    body = out.toByteArray();
                                    response.addHeader(new HttpHeader(HttpHeader.CONTENT_ENCODING, HttpHeader.GZIP));
                                    notAcceptable = false;
                                    break;
                                } catch (Exception ex) {
                                    //TODO: Log.w();
                                }
                            } else if (group.equalsIgnoreCase(HttpHeader.IDENTITY)) {
                                response.addHeader(new HttpHeader(HttpHeader.CONTENT_ENCODING, HttpHeader.IDENTITY));
                                notAcceptable = false;
                                break;
                            }
                        }

                        if (notAcceptable) {
                            response.setResponseCode(HttpResponseCode.NOT_ACCEPTABLE);
                        }
                    }

                    if(responseCode.equals(HttpResponseCode.OK)) {
                        response.addHeader(new HttpHeader(HttpHeader.CONTENT_LENGTH, Integer.toString(body.length)));
                        response.setBody(body);
                    }
                }
            }
        } else {
            throw new IllegalArgumentException(Errors.getMessage(Errors.ORG_HCJF_IO_NET_HTTP_5, request.getContext()));
        }

        return response;
    }

}
