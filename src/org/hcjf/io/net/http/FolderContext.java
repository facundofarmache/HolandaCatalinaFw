package org.hcjf.io.net.http;

import org.hcjf.encoding.MimeType;
import org.hcjf.errors.Errors;
import org.hcjf.properties.SystemProperties;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * This class publish
 * @author javaito
 * @email javaito@gmail.com
 */
public class FolderContext extends Context {

    private static final String START_CONTEXT = "^";
    private static final String END_CONTEXT = ".*";
    private static final String URI_FOLDER_SEPARATOR = "/";
    private static final String[] FORBIDDEN_CHARACTERS = {"..", "~"};
    private static final String FILE_EXTENSION_REGEX = "\\.(?=[^\\.]+$)";
    private static final String FOLDER_DEFAULT_HTML_DOCUMENT = "<!DOCTYPE html><html><head><title>%s</title><body>%s</body></html></head>";
    private static final String FOLDER_DEFAULT_HTML_BODY = "<table>%s</table>";
    private static final String FOLDER_DEFAULT_HTML_ROW = "<tr><th><a href=\"%s\">%s</a></th></tr>";

    private final Path baseFolder;
    private final String name;
    private final String defaultFile;
    private final String[] names;

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
        String[] elements = request.getContext().split(URI_FOLDER_SEPARATOR);
        for(String forbidden : FORBIDDEN_CHARACTERS) {
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
                    list.append(String.format(FOLDER_DEFAULT_HTML_ROW,
                            path.relativize(baseFolder).resolve(request.getContext()).resolve(subFile.getName()).toString(),
                            subFile.getName()));
                }
                String htmlBody = String.format(FOLDER_DEFAULT_HTML_BODY, list.toString());
                String document = String.format(FOLDER_DEFAULT_HTML_DOCUMENT, file.getName(), htmlBody);
                byte[] body = document.getBytes();
                response.setReasonPhrase(file.getName());
                response.addHeader(new HttpHeader(HttpHeader.CONTENT_LENGTH, Integer.toString(body.length)));
                response.addHeader(new HttpHeader(HttpHeader.CONTENT_TYPE, MimeType.HTML));
                response.setResponseCode(HttpResponseCode.OK);
                response.setBody(body);
            } else {
                String[] nameExtension = file.getName().split(FILE_EXTENSION_REGEX);
                String extension = nameExtension.length == 2 ? nameExtension[1] : MimeType.BIN;
                long fileSize = file.length();
                response.setReasonPhrase(file.getName());
                response.addHeader(new HttpHeader(HttpHeader.CONTENT_LENGTH, Long.toString(fileSize)));
                MimeType mimeType = MimeType.fromSuffix(extension);
                response.addHeader(new HttpHeader(HttpHeader.CONTENT_TYPE, mimeType == null ? MimeType.BIN : mimeType.toString()));
                response.setResponseCode(HttpResponseCode.OK);

                if (fileSize > SystemProperties.getLong(SystemProperties.HTTP_STREAMING_LIMIT_FILE_SIZE)) {
                    try {
                        response.setBody(Files.readAllBytes(file.toPath()));
                    } catch (IOException ex) {
                        throw new RuntimeException(Errors.getMessage(Errors.ORG_HCJF_IO_NET_HTTP_4, Paths.get(request.getContext(), file.getName())), ex);
                    }
                } else {
                    try {
                        response.setBody(Files.readAllBytes(file.toPath()));
                    } catch (IOException ex) {
                        throw new RuntimeException(Errors.getMessage(Errors.ORG_HCJF_IO_NET_HTTP_4, Paths.get(request.getContext(), file.getName())), ex);
                    }
                }
            }
        } else {
            throw new IllegalArgumentException(Errors.getMessage(Errors.ORG_HCJF_IO_NET_HTTP_5, request.getContext()));
        }

        return response;
    }

}