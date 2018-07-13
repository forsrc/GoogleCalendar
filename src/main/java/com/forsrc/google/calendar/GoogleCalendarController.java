package com.forsrc.google.calendar;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.forsrc.tools.GoogleTools;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;

@RestController
public class GoogleCalendarController {

    @Autowired
    private GoogleTools googleTools;

    @Bean
    public GoogleTools googleTools() throws IOException {
        return new GoogleTools(GoogleTools.getDetails("/credentials.json"));
    }

    @RequestMapping(value = "/login/{email}")
    public RedirectView login(@PathVariable("email") String email) throws Exception {
        String url = googleTools.getAuthorizeUrl(email);
        return new RedirectView(url);
    }

    @RequestMapping(value = "/name/list/{email}")
    public ResponseEntity<List<String>> list(@PathVariable("email") String email)
            throws GeneralSecurityException, IOException, InterruptedException, ExecutionException, TimeoutException {

        List<String> list = new ArrayList<>();

        DateTime now = new DateTime(System.currentTimeMillis());
        Calendar calendar = googleTools.getCalendar(email);
        List<Event> items = googleTools.list(calendar, now);
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

    @RequestMapping(value = "/list/{email}")
    public ResponseEntity<List<Event>> listeEmail(@PathVariable("email") String email, //
            @RequestParam(name = "min", required = false) String min, //
            @RequestParam(name = "max", required = false) String max) throws GeneralSecurityException, IOException,
            InterruptedException, ExecutionException, TimeoutException, ParseException {

        List<String> list = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        DateTime minDateTime = min == null ? new DateTime(new Date()) : new DateTime(sdf.parse(min));
        DateTime maxDateTime = max == null ? null : new DateTime(sdf.parse(max));

        Calendar calendar = googleTools.getCalendar(email);
        List<Event> items = googleTools.list(calendar, email, minDateTime, maxDateTime);
        for (Event event : items) {
            DateTime start = event.getStart().getDateTime();
            if (start == null) {
                start = event.getStart().getDate();
            }
            System.out.printf("%s (%s)\n", event.getSummary(), start);
            list.add(String.format("%s -> %s", event.getSummary(), start));
        }
        return new ResponseEntity<>(items, HttpStatus.OK);
    }

    @RequestMapping(value = "/rm/{email}")
    public ResponseEntity<String> rm(@PathVariable("email") String email) {
        boolean rm = googleTools.rm(email);
        return new ResponseEntity<>(String.format("rm on %s: %s", new Date(), rm), HttpStatus.OK);
    }
}
