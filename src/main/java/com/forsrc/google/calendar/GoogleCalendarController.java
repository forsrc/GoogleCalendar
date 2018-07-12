package com.forsrc.google.calendar;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.forsrc.tools.GoogleTools;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;

@RestController
public class GoogleCalendarController {

    @RequestMapping(value = "/login")
    public RedirectView login() throws Exception {
        String url = GoogleTools.getInstance().getAuthorizeUrl();
        return new RedirectView(url);
    }

    @RequestMapping(value = "/list")
    public ResponseEntity<List<String>> list() throws GeneralSecurityException, IOException {

        List<String> list = new ArrayList<>();

        DateTime now = new DateTime(System.currentTimeMillis());
        Calendar calendar = GoogleTools.getInstance().getCalendar();
        List<Event> items = GoogleTools.getInstance().list(calendar, now);
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

    @RequestMapping(value = "/stop")
    public ResponseEntity<String> stop() {
        String message = "stoped on " + new Date();
        GoogleTools.getInstance().stop();
        return new ResponseEntity<>(message, HttpStatus.OK);
    }

    @RequestMapping(value = "/rm")
    public ResponseEntity<String> rm() {
        boolean rm = GoogleTools.getInstance().rm();
        return new ResponseEntity<>(String.format("rm on %s: %s", new Date(), rm), HttpStatus.OK);
    }
}
