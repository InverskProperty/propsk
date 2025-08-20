package site.easy.to.build.crm.google.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.google.model.calendar.*;
import site.easy.to.build.crm.google.service.calendar.GoogleCalendarApiService;
import site.easy.to.build.crm.service.user.OAuthUserService;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Service("googleCalendarApiServiceFallback")
public class GoogleCalendarApiServiceImpl implements GoogleCalendarApiService {
    
    @Autowired
    private OAuthUserService oAuthUserService;
    
    @Override
    public EventDisplayList getEvents(String calendarId, OAuthUser oAuthUser) 
            throws IOException, GeneralSecurityException {
        // Minimal implementation to prevent NPE
        return new EventDisplayList(new java.util.ArrayList<>());
    }
    
    @Override
    public EventDisplay getEvent(String calendarId, OAuthUser oAuthUser, String eventId) 
            throws IOException, GeneralSecurityException {
        return new EventDisplay();
    }
    
    @Override
    public String createEvent(String calendarId, OAuthUser oAuthUser, Event event) 
            throws IOException, GeneralSecurityException {
        return "temp-event-id";
    }
    
    @Override
    public Event updateEvent(String calendarId, OAuthUser oAuthUser, String eventId, Event event) 
            throws IOException, GeneralSecurityException {
        return new Event();
    }
    
    @Override
    public void deleteEvent(String calendarId, OAuthUser oAuthUser, String eventId) 
            throws IOException, GeneralSecurityException {
        // No-op for now
    }
}