package com.github.taymindis.paas;

import com.github.taymindis.paas.annotation.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.naming.NamingException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyContent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class Paas implements Event {
    private static ThreadPoolExecutor bgExecutor = null;
    public static String resourcePath = "";
    public static String suffix = "";
    public static String splitter = "^"; // default prevent any replacement
    protected PageContext _pageContext;
    //    protected Object _prevPage;

    private EventStatus evStatus; // this is event process result status
    private String statusMessage;
    private JSONObject jsonPayload;

    //    private static final Map<String, MethodParams> cacheArgFields = new HashMap<>();
    private static final Map<String, MethodParams> cacheMethodParams = new HashMap<>();
    private boolean hasJson;

    public Paas(PageContext pc) {
        this._pageContext = pc;
        evStatus = EventStatus.UNSET;
        statusMessage = null;
        hasJson = false;
    }

    private static Event newEvent(PageContext pc) {
        Event ev = new EventSync(pc);
        pc.setAttribute("$_ev_ctx", ev, PageContext.REQUEST_SCOPE);
        return ev;
    }

    private static Event newFutureEvent(PageContext pc) {
        Event ev = new EventFuture(pc);
        pc.setAttribute("$_ev_ctx", ev, PageContext.REQUEST_SCOPE);
        return ev;
    }

//    private static EventTransaction newTransactionEvent(PageContext pc, String jndiResource) throws NamingException {
//        return newTransactionEvent(pc, jndiResource, null);
//    }

    private static EventTransaction newTransactionEvent(PageContext pc, String jndiResource, EventTransactionLogger log) throws NamingException {
        EventTransaction ev = new EventTransactionImpl(pc, jndiResource, log);
        pc.setAttribute("$_ev_ctx", ev, PageContext.REQUEST_SCOPE);
//        marshallingArgs(pc.getPage(), ev);
        return ev;
    }

    public static void serve(final PageContext pc) {
        serve(pc, false);
    }

    public static void serve(final PageContext pc, final boolean hasJsonPayload) {
        serve(pc, hasJsonPayload, false);
    }

    public static void serve(final PageContext pc, final boolean hasJsonPayload, final boolean async) {
        if (hasJsonPayload) {
            JSONObject jsonPayload = null;
            try {
                String body = getRequestBody((HttpServletRequest) pc.getRequest());
                jsonPayload = (JSONObject) new JSONParser().parse(body);
            } catch (ParseException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            serve(pc, jsonPayload, async);
        } else {
            serve(pc, null, async);
        }
    }

    public static void serve(final PageContext pc, final JSONObject jsonPayload, final boolean async) {
        Event ev = async ? newFutureEvent(pc) : newEvent(pc);
        if (jsonPayload != null) {
            ev.setJsonBody(jsonPayload);
        }
        marshallingParam(pc, pc.getPage(), ev, true);
    }


    public static void serveJta(final PageContext pc, final String jndiResource) {
        serveJta(pc, jndiResource, false);
    }

    public static void serveJta(final PageContext pc, final String jndiResource, final boolean hasJsonPayload) {
        serveJta(pc, jndiResource, hasJsonPayload, new DefaultTransactionLogger());
    }

    public static void serveJta(final PageContext pc, final String jndiResource, final boolean hasJsonPayload, final EventTransactionLogger logger) {
        if (hasJsonPayload) {
            JSONObject jsonPayload = null;
            try {
                String body = getRequestBody((HttpServletRequest) pc.getRequest());
                jsonPayload = (JSONObject) new JSONParser().parse(body);
            } catch (ParseException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            serveJta(pc, jndiResource, jsonPayload, logger);
        } else {
            serveJta(pc, jndiResource, null, logger);
        }
    }

    public static void serveJta(final PageContext pc, final String jndiResource, final JSONObject jsonPayload, final EventTransactionLogger logger) {
        try {
            EventTransaction ev = newTransactionEvent(pc, jndiResource, logger);
            if (jsonPayload != null) {
                ev.setJsonBody(jsonPayload);
            }
            marshallingParam(pc, pc.getPage(), ev, true);
        } catch (NamingException e) {
            logger.log(e.getExplanation());
        }
    }

    public static void init(String $resourcePath, String $suffix, String $splitter, int nWorkerThread) {
        if (null == bgExecutor && nWorkerThread > 0) {
            bgExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(nWorkerThread);
        }
        if (null != $resourcePath) {
            Paas.resourcePath = $resourcePath;
        }
        if (null != $suffix) {
            Paas.suffix = $suffix;
        }
        if (null != $splitter) {
            Paas.splitter = $splitter;
        }
    }

    @Deprecated
    public static <T extends Object> T getEvent(PageContext pc) {
        Event ev = (Event) pc.getAttribute("$_ev_ctx", PageContext.REQUEST_SCOPE);
        return (T) ev;
    }

    public static void hook(PageContext pc) {
        Event ev = (Event) pc.getAttribute("$_ev_ctx", PageContext.REQUEST_SCOPE);
        marshallingParam(pc, pc.getPage(), /*ev.get_prevPage(),*/ ev, false);
    }

    public static Object directResult(String resourcePath, Event $ev) throws Exception {
        return $ev.dispatch(resourcePath).getResult();
    }


    //    public static Object directResult(String resourcePath, Event $ev,
//                                      Map<Byte, Object> params) throws Exception {
//        for (Map.Entry<Byte, Object> entry : params.entrySet()) {
//            $ev.set(entry.getKey(), entry.getValue());
//        }
//        return $ev.dispatch(resourcePath).getResult();
//    }
//
//
//    public static Object directResult(String resourcePath, Event $ev,
//                                      Object... params) throws Exception {
//        for (int i = 0, sz = params.length; i < sz; i++) {
//            if (i % 2 == 1) {
//                $ev.set((Byte) params[i - 1], params[i]);
//            }
//        }
//
//        return $ev.dispatch(resourcePath).getResult();
//    }
    public static boolean isDispatchFutureEnabled() {
        return bgExecutor == null;
    }

    public static ThreadPoolExecutor getBgExecutor() {
        return bgExecutor;
    }

    public static void ResetNewThreadSize(int nThread, long nSecsToWait) {
        if (nThread > 0) {
            ThreadPoolExecutor _shutdownExecutor = bgExecutor;
            try {
                /** Hazard Pointer **/
                bgExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(nThread);
                if (_shutdownExecutor != null) {
                    _shutdownExecutor.shutdown();
                    if (nSecsToWait == 0 || !_shutdownExecutor.awaitTermination(nSecsToWait, TimeUnit.SECONDS)) {
                        _shutdownExecutor.shutdownNow();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void ShutDownBackgroundTask(long nSecsToWait) {
        try {
            bgExecutor.shutdown();
            if (nSecsToWait == 0 || !bgExecutor.awaitTermination(nSecsToWait, TimeUnit.SECONDS)) {
                bgExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setJsonBody(String jsonBody) {
        try {
            jsonPayload = (JSONObject) new JSONParser().parse(jsonBody);
            hasJson = true;
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public void setJsonBody(JSONObject json) {
        jsonPayload = json;
        hasJson = true;
    }

    public boolean hasJson() {
        return hasJson;
    }

    public JSONObject getJsonPayload() {
        return jsonPayload;
    }

    public void set(String key, Object val) {
        this._pageContext.setAttribute(key, val, PageContext.REQUEST_SCOPE);
    }

    public Object get(String key) {
        return this._pageContext.getAttribute(key, PageContext.REQUEST_SCOPE);
    }

    protected void clearPreviousStatus() {
        setResult(null);
        setStatus(EventStatus.UNSET);
        try {
            BodyContent bodyContent = this._pageContext.pushBody();
            bodyContent.clearBody();
            bodyContent.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Boolean isStatus(EventStatus status) {
        return this.evStatus == status;
    }

    public Boolean isStatus(EventStatus... statuses) {
        for (EventStatus s : statuses) {
            if (this.evStatus == s) {
                return true;
            }
        }
        return false;
    }

    public void setStatus(EventStatus status) {
        this.evStatus = status;
    }

    public EventStatus getStatus() {
        return evStatus;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public void setStatusMessage(Exception ex) {
        this.statusMessage = ex.getMessage();
    }


    @Override
    public HttpServletRequest getServletRequest() {
        return (HttpServletRequest) this._pageContext.getRequest();
    }

    @Override
    public HttpServletResponse getServletResponse() {
        return (HttpServletResponse) this._pageContext.getResponse();
    }

    @Override
    public HttpSession getSession() {
        return this._pageContext.getSession();
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return this._pageContext.getResponse().getWriter();
    }

    @Override
    public PageContext getPageContext() {
        return this._pageContext;
    }

//    protected static void marshallingArgs(Object pageObj, Event ev) {
//        Class<?> clazz = pageObj.getClass();
////        String pkgName = clazz.getPackage().getName();
//        String name = clazz.getCanonicalName();
//        int hashCode = clazz.hashCode();
//        boolean rebuild;
//        Field[] annotated;
//        Map<String, Field> argsData;
//
//        MethodParams cf = cacheArgFields.get(name);
//        if (cf == null || cf.getHashCode() != hashCode) {
//            cf = new MethodParams(name, hashCode);
//            rebuild = true;
//        } else {
//            rebuild = false;
//        }
//        if (rebuild) {
//            Field[] fields = clazz.getDeclaredFields();
//            List<Field> fList = new ArrayList();
//            for (Field $f : fields) {
//                if ($f.isAnnotationPresent(WiredArg.class)) {
//                    $f.setAccessible(true);
//                    fList.add($f);
//                }
//            }
//            annotated = fList.toArray(new Field[0]);
//            cf.setFs(annotated);
//            cacheArgFields.put(name, cf);
//        } else {
//            annotated = cf.getFs();
//        }
//        argsData = ev.getArgsData();
//        for (Field f : annotated) {
////            Class<?> clz = f.getType();
//            String key = f.getName();
//            if (!argsData.containsKey(key)) {
//                argsData.put(key, f);
//            }
//        }
//    }

    private static Object parseType(Object v) {
        if (String.class.isInstance(v)) {
            return String.valueOf(v);
        } else if (Integer.class.isInstance(v)) {
            return (Integer) v;
        } else if (Float.class.isInstance(v)) {
            return (Integer) v;
        } else if (Double.class.isInstance(v)) {
            return (Double) v;
        } else if (char.class.isInstance(v)) {
            return (char) v;
        } else {
            return v;
        }
    }

    private static String getRequestBody(HttpServletRequest req) throws IOException {
        BufferedReader br = req.getReader();
        String buf;
        StringBuilder sb = new StringBuilder();
        while ((buf = br.readLine()) != null) {
            sb.append(buf);
        }

        return sb.toString();
    }

    private static void marshallingParam(PageContext pc, Object pageObj,/* Object _prevObj, */Event ev, boolean finalizing) {
        Class<?> clazz = pageObj.getClass();
        String name = clazz.getCanonicalName();
        int hashCode = clazz.hashCode();
        boolean rebuild;
        Annotation[][] paramAnnotation;
        Class<?>[] parameterTypes;

        MethodParams mprms = cacheMethodParams.get(name);
        if (mprms == null || mprms.getHashCode() != hashCode) {
            mprms = new MethodParams(name, hashCode);
            rebuild = true;
        } else {
            rebuild = false;
        }
        if (rebuild) {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method m : methods) {
                if (m.isAnnotationPresent(hook.class)) {
//                    if(!isQualifyMethod(m)){
//                      // TODO if not qualify, throw e
//                    }
                    m.setAccessible(true);
                    mprms.setM(m);
                    mprms.setParamAnnotation(m.getParameterAnnotations());
                    break;
                }
            }
            // find the first param will do
            paramAnnotation = mprms.getParamAnnotation();
            cacheMethodParams.put(name, mprms);
        } else {
            paramAnnotation = mprms.getParamAnnotation();
        }
//        argsData = ev.getArgsData();
        ServletRequest req = pc.getRequest();
        Object[] args;
        JSONObject jsonPayload = ev.getJsonPayload();
        boolean hasJson = ev.hasJson();
        if (req.getParameterMap().size() > 0 || req.getAttributeNames().hasMoreElements()) {
            args = new Object[paramAnnotation.length];
            Annotation a;
            Class<?> c;
            for (int i = 0, j = 0, sz = paramAnnotation.length; j < sz; i++, j++) {
                Annotation[] as = paramAnnotation[j];
                if (as.length > 0 && (a = as[0]) != null) {
                    Class<?> atype = a.annotationType();
                    if (atype.isAssignableFrom(param.class)) {
                        param p = (param) a;
                        args[i] = req.getParameter(p.value());
                    } else if (atype.isAssignableFrom(attr.class)) {
                        attr attr = (com.github.taymindis.paas.annotation.attr) a;
                        args[i] = req.getAttribute(attr.value());
                    } else if (atype.isAssignableFrom(json.class)) {
                        if (hasJson) {
                            args[i] = jsonPayload;
                        }
                    } else if (atype.isAssignableFrom(event.class)) {
                        args[i] = ev;
                    } else if (atype.isAssignableFrom(any.class)) {
                        any any = (any) a;
                        String key = any.value();
                        if (req.getAttribute(key) != null) {
                            args[i] = req.getAttribute(key);
                        } else if (hasJson && jsonPayload.get(key) != null) {
                            args[i] = jsonPayload.get(key);
                        } else {
                            args[i] = req.getParameter(key);
                        }
                    }
                }
//                else if ((c = parameterTypes[j]).isAssignableFrom(EventTransaction.class) || c.isAssignableFrom(Event.class)) {
//                    args[i] = ev;
//                }
//                else if (c.isAssignableFrom(HttpServletRequest.class)) {
//                    args[i] = req;
//                } else if (c.isAssignableFrom(HttpServletResponse.class)) {
//                    args[i] = pc.getResponse();
//                } else if (c.isAssignableFrom(HttpSession.class)) {
//                    args[i] = pc.getSession();
//                } else if (c.isAssignableFrom(JspWriter.class)) {
//                    args[i] = pc.getOut();
//                }
            }
            boolean hasError = false;
            try {
                Object o = mprms.getM().invoke(pageObj, args);
                if (o != null) {
                    ev.setResult(o);
                }
            } catch (IllegalAccessException e) {
                hasError = true;
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                hasError = true;
                e.printStackTrace();
            } catch (Exception e) {
                hasError = true;
                throw e;
            } finally {
                if (finalizing) {
                    if (ev instanceof EventTransaction) {
                        EventTransaction evt = (EventTransaction) ev;
                        try {
                            if (hasError) {
                                try {
                                    evt.rollback();
                                } catch (SQLException throwables) {
                                    throwables.printStackTrace();
                                }
                                evt.release(false);
                            } else {
                                evt.release(true);
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

}
