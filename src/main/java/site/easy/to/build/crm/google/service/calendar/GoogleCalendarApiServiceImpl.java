package site.easy.to.build.crm.google.service.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.google.model.calendar.*;
import site.easy.to.build.crm.google.util.GoogleApiHelper;
import site.easy.to.build.crm.service.lead.LeadService;
import site.easy.to.build.crm.service.user.OAuthUserService;
import site.easy.to.build.crm.google.util.TimeDateUtil;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GoogleCalendarApiServiceImpl implements GoogleCalendarApiService {

    private static final String API_BASE_URL = "https://www.googleapis.com/calendar/v3/calendars/";

    private final OAuthUserService oAuthUserService;
    private final ObjectMapper objectMapper;
    private final LeadService leadService;

    @Autowired
    public GoogleCalendarApiServiceImpl(OAuthUserService oAuthUserService, ObjectMapper objectMapper, LeadService leadService) {
        this.oAuthUserService = oAuthUserService;
        this.objectMapper = objectMapper;
        this.leadService = leadService;
    }

    public EventDisplayList getEvents(String calendarId, OAuthUser oauthUser) throws IOException, GeneralSecurityException {
        System.out.println("📅 DEBUG: GoogleCalendarApiService.getEvents() called");
        System.out.println("   Calendar ID: " + calendarId);
        System.out.println("   OAuth user: " + (oauthUser != null ? oauthUser.getEmail() : "null"));
        
        String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oauthUser);
        System.out.println("   Access token obtained: " + (accessToken != null ? "[PRESENT]" : "[NULL]"));

        HttpRequestFactory requestFactory = GoogleApiHelper.createRequestFactory(accessToken);
        System.out.println("   HTTP request factory created");

        GenericUrl eventsUrl = new GenericUrl(API_BASE_URL + calendarId + "/events");
        System.out.println("   Calendar API URL: " + eventsUrl.toString());

        String nowInRfc3339 = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        System.out.println("   Time filter (timeMin): " + nowInRfc3339);

        eventsUrl.put("timeMin", nowInRfc3339);
        eventsUrl.put("singleEvents", "true");
        eventsUrl.put("orderBy", "startTime");
        System.out.println("   Final API URL with params: " + eventsUrl.toString());

        System.out.println("   Making HTTP request to Google Calendar API...");
        HttpRequest request = requestFactory.buildGetRequest(eventsUrl);
        HttpResponse response = request.execute();
        System.out.println("   HTTP response status: " + response.getStatusCode());
        System.out.println("   HTTP response content type: " + response.getContentType());
        String jsonResponse = response.parseAsString();
        System.out.println("   Raw JSON response length: " + jsonResponse.length());
        System.out.println("   Parsing JSON response to EventList...");
        
        EventList eventList = objectMapper.readValue(jsonResponse, EventList.class);
        System.out.println("   Parsed " + (eventList.getItems() != null ? eventList.getItems().size() : 0) + " events from API");

        // Convert Event objects to EventDisplay objects
        List<EventDisplay> eventDisplays = eventList.getItems().stream()
                .map(event -> {
                    EventDateTime start = event.getStart();
                    EventDateTime end = event.getEnd();
                    Map<String, String> startDateTimeParts = TimeDateUtil.extractDateTime(start.getDateTime());
                    Map<String, String> endDateTimeParts = TimeDateUtil.extractDateTime(end.getDateTime());

                    return new EventDisplay(
                            event.getId(),
                            event.getSummary(),
                            startDateTimeParts.get("date"),
                            startDateTimeParts.get("time"),
                            endDateTimeParts.get("date"),
                            endDateTimeParts.get("time"),
                            startDateTimeParts.get("timeZone"),
                            event.getAttendees()
                    );
                })
                .collect(Collectors.toList());

        System.out.println("   Converted to " + eventDisplays.size() + " EventDisplay objects");
        System.out.println("✅ Google Calendar API call completed successfully");
        
        return new EventDisplayList(eventDisplays);
    }

    public String createEvent(String calendarId, OAuthUser oauthUser, Event event) throws IOException, GeneralSecurityException {
        String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oauthUser);

        HttpRequestFactory requestFactory = GoogleApiHelper.createRequestFactory(accessToken);

        GenericUrl createEventUrl = new GenericUrl(API_BASE_URL + calendarId + "/events?sendUpdates=all");
        String eventJson = objectMapper.writeValueAsString(event);

        HttpContent content = new ByteArrayContent("application/json", eventJson.getBytes(StandardCharsets.UTF_8));
        HttpRequest request = requestFactory.buildPostRequest(createEventUrl, content);
        HttpResponse response = request.execute();

        String jsonResponse = response.parseAsString();
        Event createdEvent = objectMapper.readValue(jsonResponse, Event.class);
        return createdEvent.getId();
    }

    public Event updateEvent(String calendarId, OAuthUser oauthUser, String eventId, Event updatedEvent) throws IOException, GeneralSecurityException {
        String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oauthUser);

        updateLead(oauthUser, eventId, "Meeting updated");

        HttpRequestFactory requestFactory = GoogleApiHelper.createRequestFactory(accessToken);

        GenericUrl updateEventUrl = new GenericUrl(API_BASE_URL + calendarId + "/events/" + eventId + "?sendUpdates=all");
        String eventJson = objectMapper.writeValueAsString(updatedEvent);

        HttpContent content = new ByteArrayContent("application/json", eventJson.getBytes(StandardCharsets.UTF_8));
        HttpRequest request = requestFactory.buildPutRequest(updateEventUrl, content);
        HttpResponse response = request.execute();

        String jsonResponse = response.parseAsString();
        return objectMapper.readValue(jsonResponse, Event.class);
    }

    public void deleteEvent(String calendarId, OAuthUser oauthUser, String eventId) throws IOException, GeneralSecurityException {
        String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oauthUser);

        updateLead(oauthUser, eventId, "Meeting canceled");

        HttpRequestFactory requestFactory = GoogleApiHelper.createRequestFactory(accessToken);

        GenericUrl deleteEventUrl = new GenericUrl(API_BASE_URL + calendarId + "/events/" + eventId + "?sendUpdates=all");
        HttpRequest request = requestFactory.buildDeleteRequest(deleteEventUrl);
        HttpResponse response = request.execute();
    }

    public EventDisplay getEvent(String calendarId, OAuthUser oauthUser, String eventId) throws IOException, GeneralSecurityException {
        String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oauthUser);

        HttpRequestFactory requestFactory = GoogleApiHelper.createRequestFactory(accessToken);

        GenericUrl eventsUrl = new GenericUrl(API_BASE_URL + calendarId + "/events/" + eventId);


        HttpRequest request = requestFactory.buildGetRequest(eventsUrl);
        HttpResponse response = request.execute();
        String jsonResponse = response.parseAsString();
        Event event = objectMapper.readValue(jsonResponse, Event.class);

        EventDateTime start = event.getStart();
        EventDateTime end = event.getEnd();
        Map<String, String> startDateTimeParts = TimeDateUtil.extractDateTime(start.getDateTime());
        Map<String, String> endDateTimeParts = TimeDateUtil.extractDateTime(end.getDateTime());

        return new EventDisplay(
                event.getId(),
                event.getSummary(),
                startDateTimeParts.get("date"),
                startDateTimeParts.get("time"),
                endDateTimeParts.get("date"),
                endDateTimeParts.get("time"),
                startDateTimeParts.get("timeZone"),
                event.getAttendees()
        );
    }

    private void updateLead(OAuthUser oAuthUser, String eventId, String status) {
        Lead lead = leadService.findByMeetingId(eventId);
        if (lead != null) {
            lead.setEmployee(oAuthUser.getUser());
            lead.setStatusValue(status); // Use setStatusValue for string conversion
            if(status.equals("Meeting canceled")){
                lead.setMeetingId("");
            } else {
                lead.setMeetingId(eventId);
            }
            leadService.save(lead);
        }
    }

}
