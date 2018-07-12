package com.forsrc.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.Throwables;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

public class GoogleTools {

    private static final String APPLICATION_NAME = "Google API";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String CREDENTIALS_FOLDER = "google/credentials"; // Directory to store user credentials.
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final String CLIENT_SECRET_DIR = "/credentials.json";

    private static MyLocalServerReceiver myLocalServerReceiver;

    private static GoogleTools INSTANCE;

    private GoogleTools() {

    }

    public static GoogleTools getInstance() {
        if (INSTANCE == null) {
            synchronized (GoogleTools.class) {
                if (INSTANCE == null) {
                    INSTANCE = new GoogleTools();
                    return INSTANCE;
                }
            }
        }
        return INSTANCE;
    }

    private Credential getCredential(final CompletableFuture<MyLocalServerReceiver> localServerReceiverFuture,
            final NetHttpTransport netHttpTransport) throws IOException {
        stop();
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = getFlow(netHttpTransport);
        MyLocalServerReceiver localServerReceiver = new MyLocalServerReceiver("localhost", 18888, "/Callback", null,
                null);
        myLocalServerReceiver = localServerReceiver;
        // System.out.println(localServerReceiver.getRedirectUri());
        localServerReceiverFuture.complete(localServerReceiver);
        return new AuthorizationCodeInstalledApp(flow, localServerReceiver).authorize("user");
    }

    private GoogleAuthorizationCodeFlow getFlow(final NetHttpTransport netHttpTransport) throws IOException {

        // Load client secrets.
        InputStream in = GoogleTools.class.getResourceAsStream(CLIENT_SECRET_DIR);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        clientSecrets = new GoogleClientSecrets().setWeb(clientSecrets.getDetails());
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(netHttpTransport, JSON_FACTORY,
                clientSecrets, SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(CREDENTIALS_FOLDER)))
                        .setAccessType("offline").build();
        return flow;
    }

    private NetHttpTransport getNetHttpTransport() throws GeneralSecurityException, IOException {
        // return new NetHttpTransport.Builder().setProxy(new Proxy(Proxy.Type.HTTP, new
        // InetSocketAddress("192.168.10.1", 3128))).build();
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    private String getAuthorizeUrl(final GoogleAuthorizationCodeFlow flow, final String redirectURI) throws Exception {
        AuthorizationCodeRequestUrl authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectURI);
        System.out.println("cal authorizationUrl->" + authorizationUrl);
        return authorizationUrl.build();
    }

    public String getAuthorizeUrl() throws Exception {

        CompletableFuture<MyLocalServerReceiver> localServerReceiverFuture = new CompletableFuture<>();
        final NetHttpTransport netHttpTransport = getNetHttpTransport();
        GoogleAuthorizationCodeFlow flow = getFlow(netHttpTransport);

        CompletableFuture.runAsync(() -> {
            try {
                getCredential(localServerReceiverFuture, netHttpTransport);
            } catch (IOException e) {
                e.printStackTrace();
                stop();
            }
        });
        // String url = getAuthorizeUrl(flow,
        // localServerReceiverFuture.get().getRedirectUri());
        MyLocalServerReceiver localServerReceiver = localServerReceiverFuture.get();
        int count = 30;
        while (localServerReceiver.getPort() < 0) {
            if (count-- <= 0) {
                stop();
                throw new RuntimeException(String.format("Timeout localServerReceiver: %s:%s",
                        localServerReceiver.getHost(), localServerReceiver.getPort()));
            }
            System.out.println(String.format("%s localServerReceiver: %s:%s", count, localServerReceiver.getHost(),
                    localServerReceiver.getPort()));
            TimeUnit.SECONDS.sleep(1);
        }
        String url = getAuthorizeUrl(flow,
                String.format("http://%s:%s/Callback", localServerReceiver.getHost(), localServerReceiver.getPort()));
        return url;
    }

    public void stop() {
        try {
            stop(myLocalServerReceiver);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop(MyLocalServerReceiver myLocalServerReceiver) throws IOException {
        if (myLocalServerReceiver != null) {
            myLocalServerReceiver.stop();
            myLocalServerReceiver = null;
        }
    }

    public boolean rm() {
        File file = new File(CREDENTIALS_FOLDER + "/StoredCredential");
        return file.delete();
    }

    public Calendar getCalendar() throws IOException, GeneralSecurityException {
        CompletableFuture<MyLocalServerReceiver> localServerReceiverFuture = new CompletableFuture<>();
        NetHttpTransport netHttpTransport = getNetHttpTransport();
        Credential credential = getCredential(localServerReceiverFuture, netHttpTransport);
        Calendar calendar = new Calendar.Builder(netHttpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME).build();

        return calendar;
    }

    public List<Event> list(Calendar calendar, DateTime min, DateTime max) throws IOException {
        Events events = calendar //
                .events() //
                .list("primary") //
                .setMaxResults(100) //
                .setTimeMin(min) //
                .setTimeMax(max) //
                .setOrderBy("startTime") //
                .setSingleEvents(true) //
                .execute();
        List<Event> items = events.getItems();
        return items;
    }

    public List<Event> list(Calendar calendar, DateTime min) throws IOException {
        Events events = calendar //
                .events() //
                .list("primary") //
                .setMaxResults(100) //
                .setTimeMin(min) //
                .setOrderBy("startTime") //
                .setSingleEvents(true) //
                .execute();
        List<Event> items = events.getItems();
        return items;
    }

    public static class MyLocalServerReceiver implements VerificationCodeReceiver {

        private static final String LOCALHOST = "localhost";

        private static final String CALLBACK_PATH = "/Callback";

        /** Server or {@code null} before {@link #getRedirectUri()}. */
        private Server server;

        /** Verification code or {@code null} for none. */
        String code;

        /** Error code or {@code null} for none. */
        String error;

        /** To block until receiving an authorization response or stop() is called. */
        final Semaphore waitUnlessSignaled = new Semaphore(0 /* initially zero permit */);

        /**
         * Port to use or {@code -1} to select an unused port in
         * {@link #getRedirectUri()}.
         */
        private int port;

        /** Host name to use. */
        private final String host;

        /** Callback path of redirect_uri */
        private final String callbackPath;

        /**
         * URL to an HTML page to be shown (via redirect) after successful login. If
         * null, a canned default landing page will be shown (via direct response).
         */
        private String successLandingPageUrl;

        /**
         * URL to an HTML page to be shown (via redirect) after failed login. If null, a
         * canned default landing page will be shown (via direct response).
         */
        private String failureLandingPageUrl;

        /**
         * Constructor that starts the server on {@link #LOCALHOST} and an unused port.
         *
         * <p>
         * Use {@link Builder} if you need to specify any of the optional parameters.
         * </p>
         */
        public MyLocalServerReceiver() {
            this(LOCALHOST, -1, CALLBACK_PATH, null, null);
        }

        /**
         * Constructor.
         *
         * @param host
         *            Host name to use
         * @param port
         *            Port to use or {@code -1} to select an unused port
         */
        public MyLocalServerReceiver(String host, int port, String successLandingPageUrl,
                String failureLandingPageUrl) {
            this(host, port, CALLBACK_PATH, successLandingPageUrl, failureLandingPageUrl);
        }

        /**
         * Constructor.
         *
         * @param host
         *            Host name to use
         * @param port
         *            Port to use or {@code -1} to select an unused port
         */
        public MyLocalServerReceiver(String host, int port, String callbackPath, String successLandingPageUrl,
                String failureLandingPageUrl) {
            this.host = host;
            this.port = port;
            this.callbackPath = callbackPath;
            this.successLandingPageUrl = successLandingPageUrl;
            this.failureLandingPageUrl = failureLandingPageUrl;
        }

        @Override
        public String getRedirectUri() throws IOException {
            server = new Server(port != -1 ? port : 0);
            Connector connector = server.getConnectors()[0];
            connector.setHost(host);
            server.addHandler(new CallbackHandler());
            try {
                server.start();
                port = connector.getLocalPort();
            } catch (Exception e) {
                Throwables.propagateIfPossible(e);
                throw new IOException(e);
            }
            return "http://" + host + ":" + port + callbackPath;
        }

        /**
         * Blocks until the server receives a login result, or the server is stopped by
         * {@link #stop()}, to return an authorization code.
         *
         * @return authorization code if login succeeds; may return {@code null} if the
         *         server is stopped by {@link #stop()}
         * @throws IOException
         *             if the server receives an error code (through an HTTP request
         *             parameter {@code error})
         */
        @Override
        public String waitForCode() throws IOException {
            waitUnlessSignaled.acquireUninterruptibly();
            if (error != null) {
                throw new IOException("User authorization failed (" + error + ")");
            }
            return code;
        }

        @Override
        public void stop() throws IOException {
            waitUnlessSignaled.release();
            if (server != null) {
                try {
                    server.stop();
                } catch (Exception e) {
                    Throwables.propagateIfPossible(e);
                    throw new IOException(e);
                }
                server = null;
            }
        }

        /** Returns the host name to use. */
        public String getHost() {
            return host;
        }

        /**
         * Returns the port to use or {@code -1} to select an unused port in
         * {@link #getRedirectUri()}.
         */
        public int getPort() {
            return port;
        }

        /**
         * Returns callback path used in redirect_uri.
         */
        public String getCallbackPath() {
            return callbackPath;
        }

        /**
         * Builder.
         *
         * <p>
         * Implementation is not thread-safe.
         * </p>
         */
        public static final class Builder {

            /** Host name to use. */
            private String host = LOCALHOST;

            /** Port to use or {@code -1} to select an unused port. */
            private int port = -1;

            private String successLandingPageUrl;
            private String failureLandingPageUrl;

            private String callbackPath = CALLBACK_PATH;

            /** Builds the {@link LocalServerReceiver}. */
            public MyLocalServerReceiver build() {
                return new MyLocalServerReceiver(host, port, callbackPath, successLandingPageUrl,
                        failureLandingPageUrl);
            }

            /** Returns the host name to use. */
            public String getHost() {
                return host;
            }

            /** Sets the host name to use. */
            public Builder setHost(String host) {
                this.host = host;
                return this;
            }

            /** Returns the port to use or {@code -1} to select an unused port. */
            public int getPort() {
                return port;
            }

            /** Sets the port to use or {@code -1} to select an unused port. */
            public Builder setPort(int port) {
                this.port = port;
                return this;
            }

            /** Returns the callback path of redirect_uri */
            public String getCallbackPath() {
                return callbackPath;
            }

            /** Set the callback path of redirect_uri */
            public Builder setCallbackPath(String callbackPath) {
                this.callbackPath = callbackPath;
                return this;
            }

            public Builder setLandingPages(String successLandingPageUrl, String failureLandingPageUrl) {
                this.successLandingPageUrl = successLandingPageUrl;
                this.failureLandingPageUrl = failureLandingPageUrl;
                return this;
            }
        }

        /**
         * Jetty handler that takes the verifier token passed over from the OAuth
         * provider and stashes it where {@link #waitForCode} will find it.
         */
        class CallbackHandler extends AbstractHandler {

            @Override
            public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                    throws IOException {
                if (!CALLBACK_PATH.equals(target)) {
                    return;
                }

                try {
                    ((Request) request).setHandled(true);
                    error = request.getParameter("error");
                    code = request.getParameter("code");

                    if (error == null && successLandingPageUrl != null) {
                        response.sendRedirect(successLandingPageUrl);
                    } else if (error != null && failureLandingPageUrl != null) {
                        response.sendRedirect(failureLandingPageUrl);
                    } else {
                        writeLandingHtml(response);
                    }
                    response.flushBuffer();
                } finally {
                    waitUnlessSignaled.release();
                }
            }

            private void writeLandingHtml(HttpServletResponse response) throws IOException {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/html");

                PrintWriter doc = response.getWriter();
                doc.println("<html>");
                doc.println("<head><title>OAuth 2.0 Authentication Token Received</title></head>");
                doc.println("<body>");
                doc.println("Received verification code. You may now close this window.");
                doc.println("</body>");
                doc.println("</html>");
                doc.flush();
            }
        }
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {

        Calendar calendar = GoogleTools.getInstance().getCalendar();
        List<Event> events = GoogleTools.getInstance().list(calendar, new DateTime(System.currentTimeMillis()));
        for (Event event : events) {
            System.out.println(event);
        }
    }
}
