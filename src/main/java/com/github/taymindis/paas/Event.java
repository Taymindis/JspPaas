package com.github.taymindis.paas;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import java.util.concurrent.TimeUnit;

public interface Event {

   Event dispatch(String jspPathAndParam) throws Exception;

   void setResult(Object rs);

   <T extends Object> T  getResult();

   <T extends Object> T getResult(long timeout, TimeUnit unit);

   boolean isDone();

   boolean isCancelled();

   /**
    This is same with request.setAttribute(...)
    retrievable via @attr hook
    * @param key key
    * @param val value
    */
   void set(String key, Object val);

   /**
    This is same with request.getAttribute(...)
    retrievable via @attr hook
    * @param key key
    * @return value
    */
   Object get(String key);

   Boolean isStatus(EventStatus status);
   Boolean isStatus(EventStatus... statuses);

   void setStatus(EventStatus status);

   EventStatus getStatus();

   String getStatusMessage();

   void setStatusMessage(String statusMessage);

   void setStatusMessage(Exception exception);

   JSONObject getJsonPayload();
   void setJsonBody(String jsonBody) throws ParseException;

   public void setJsonBody(JSONObject json);

   boolean hasJson();

   HttpServletRequest getServletRequest();
   HttpServletResponse getServletResponse();
   HttpSession getSession();
   JspWriter getOut();
   PageContext getPageContext();
}
