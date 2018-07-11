package com.forsrc.google.calendar;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.example.demo.CalendarQuickstart;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

@RestController
public class GoogleCalendarController {

    private static final String APPLICATION_NAME = "Google Calendar API";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String CREDENTIALS_FOLDER = "google/credentials"; // Directory to store user credentials.
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final String CLIENT_SECRET_DIR = "/credentials.json";

    private CompletableFuture<LocalServerReceiver> localServerReceiverFuture = new CompletableFuture<>();

    private Credential getCredentials(final NetHttpTransport netHttpTransport) throws IOException {

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = getFlow(netHttpTransport);
        LocalServerReceiver localServerReceiver = new LocalServerReceiver();
        localServerReceiverFuture.complete(localServerReceiver);
        return new AuthorizationCodeInstalledApp(flow, localServerReceiver).authorize("user");
    }

    private GoogleAuthorizationCodeFlow getFlow(final NetHttpTransport netHttpTransport) throws IOException {
        // Load client secrets.
        InputStream in = GoogleCalendarController.class.getResourceAsStream(CLIENT_SECRET_DIR);
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

    private String getAuthorizeUrl(GoogleAuthorizationCodeFlow flow, String redirectURI) throws Exception {
        AuthorizationCodeRequestUrl authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectURI);
        System.out.println("cal authorizationUrl->" + authorizationUrl);
        return authorizationUrl.build();
    }

    @RequestMapping(value = "/login")
    public RedirectView login() throws Exception {
        localServerReceiverFuture = new CompletableFuture<>();
        final NetHttpTransport netHttpTransport = getNetHttpTransport();
        GoogleAuthorizationCodeFlow flow = getFlow(netHttpTransport);
        CompletableFuture.runAsync(() -> {
            try {
                getCredentials(netHttpTransport);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        String url = getAuthorizeUrl(flow, localServerReceiverFuture.get().getRedirectUri());
        return new RedirectView(url);
    }

    @RequestMapping(value = "/list")
    public ResponseEntity<List<String>> list() throws GeneralSecurityException, IOException {
        List<String> list = new ArrayList<>();
        NetHttpTransport netHttpTransport = getNetHttpTransport();
        Calendar calendar = new Calendar.Builder(netHttpTransport, JSON_FACTORY, getCredentials(netHttpTransport))
                .setApplicationName(APPLICATION_NAME).build();

        DateTime now = new DateTime(System.currentTimeMillis());
        Events events = calendar.events().list("primary").setMaxResults(10).setTimeMin(now).setOrderBy("startTime")
                .setSingleEvents(true).execute();
        List<Event> items = events.getItems();
        for (Event event : items) {
            DateTime start = event.getStart().getDateTime();
            if (start == null) {
                start = event.getStart().getDate();
            }
            System.out.printf("%s (%s)\n", event.getSummary(), start);
            list.add(String.format("%s -> %s", event.getSummary(), start));
        }
        return new ResponseEntity<>(list, HttpStatus.OK);
    }
}