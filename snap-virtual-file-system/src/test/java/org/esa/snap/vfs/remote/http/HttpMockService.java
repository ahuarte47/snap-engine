package org.esa.snap.vfs.remote.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assume.assumeTrue;

class HttpMockService {

    private HttpServer mockServer;

    HttpMockService(URL serviceAddress, Path serviceRootPath) throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(serviceAddress.getPort()), 0);
        mockServer.createContext(serviceAddress.getPath(), new HTTPMockServiceHandler(serviceRootPath));
    }

    void start() {
        mockServer.start();
    }

    void stop() {
        mockServer.stop(1);
    }

    private class HTTPMockServiceHandler implements HttpHandler {

        static final String htmlFile = "index.html";
        private Path serviceRootPath;

        HTTPMockServiceHandler(Path serviceRootPath) {
            assumeTrue(Files.exists(serviceRootPath));
            this.serviceRootPath = serviceRootPath;
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            byte[] response;
            try {
                String urlPath = httpExchange.getRequestURI().getPath();
                Path responsePath = serviceRootPath.resolve(urlPath.replaceAll("^/", ""));
                if (Files.isDirectory(responsePath)) {
                    responsePath = responsePath.resolve(htmlFile);
                }
                if (Files.exists(responsePath)) {
                    response = readFile(responsePath);
                    httpExchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
                } else {
                    response = "Not Found".getBytes();
                    httpExchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, response.length);
                }
            } catch (Exception ex) {
                response = ex.getMessage().getBytes();
                httpExchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, response.length);
            }
            httpExchange.getResponseBody().write(response);
            httpExchange.close();
        }

        private byte[] readFile(Path inputFile) throws IOException {
            InputStream is = Files.newInputStream(inputFile);
            byte data[] = new byte[is.available()];
            is.read(data);
            is.close();
            return data;
        }
    }

}
