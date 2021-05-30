package com.github.taymindis.paas;

import com.github.taymindis.paas.annotation.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.yaml.snakeyaml.Yaml;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyContent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;
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
    private static final boolean DEFAULT_ROLLBACK_ON_ERROR = true;

    //    private static final Map<String, MethodParams> cacheArgFields = new HashMap<>();
    private static final Map<String, MethodParams> cacheMethodParams = new HashMap<>();
    private static Map<String, Object> paasConf = null;
    private boolean hasJson;

    public Paas(PageContext pc) {
        this._pageContext = pc;
        evStatus = EventStatus.UNSET;
        statusMessage = null;
        hasJson = false;
    }

    private static Event newEvent(PageContext pc) {
        Event ev = new SyncEvent(pc);
        pc.setAttribute("$_ev_ctx", ev, PageContext.REQUEST_SCOPE);
        return ev;
    }

    private static Event newFutureEvent(PageContext pc) {
        Event ev = new AsyncEvent(pc);
        pc.setAttribute("$_ev_ctx", ev, PageContext.REQUEST_SCOPE);
        return ev;
    }

//    private static EventTransaction newTransactionEvent(PageContext pc, String jndiResource) throws NamingException {
//        return newTransactionEvent(pc, jndiResource, null);
//    }

    private static JtaEvent newJtaEvent(PageContext pc, String jndiResource, JtaEventLogger log) throws NamingException {
        JtaEvent ev = new JtaEventImpl(pc, jndiResource, log);
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
        marshallingParam(pc, pc.getPage(), ev, true, serve.class);
    }

    public static void serveJta(final PageContext pc, final String jndiResource) {
        serveJta(pc, jndiResource, false, DEFAULT_ROLLBACK_ON_ERROR);
    }

    public static void serveJta(final PageContext pc, final String jndiResource, final JtaEventLogger logger) {
        serveJta(pc, jndiResource, false, logger, DEFAULT_ROLLBACK_ON_ERROR);
    }

    public static void serveJta(final PageContext pc, final String jndiResource, final JtaEventLogger logger, final boolean rollbackOnError) {
        serveJta(pc, jndiResource, false, logger, rollbackOnError);
    }

    public static void serveJta(final PageContext pc, final String jndiResource, final boolean hasJsonPayload) {
        serveJta(pc, jndiResource, hasJsonPayload, new DefaultJtaEventLogger(), DEFAULT_ROLLBACK_ON_ERROR);
    }

    public static void serveJta(final PageContext pc, final String jndiResource, final boolean hasJsonPayload, final boolean rollbackOnError) {
        serveJta(pc, jndiResource, hasJsonPayload, new DefaultJtaEventLogger(), rollbackOnError);
    }
    public static void serveJta(final PageContext pc, final String jndiResource, final boolean hasJsonPayload, final JtaEventLogger logger){
        serveJta(pc, jndiResource, hasJsonPayload, logger, DEFAULT_ROLLBACK_ON_ERROR);
    }

    public static void serveJta(final PageContext pc, final String jndiResource, final JSONObject jsonPayload, final JtaEventLogger logger) {
        serveJta(pc, jndiResource, jsonPayload, logger, DEFAULT_ROLLBACK_ON_ERROR);
    }

    public static void serveJta(final PageContext pc, final String jndiResource, final boolean hasJsonPayload, final JtaEventLogger logger, final boolean rollbackOnError) {
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
            serveJta(pc, jndiResource, jsonPayload, logger, rollbackOnError);
        } else {
            serveJta(pc, jndiResource, null, logger, rollbackOnError);
        }
    }

    public static void serveJta(final PageContext pc, final String jndiResource, final JSONObject jsonPayload, final JtaEventLogger logger, final boolean rollbackOnError) {
        try {
            JtaEvent ev = newJtaEvent(pc, jndiResource, logger);
            if (jsonPayload != null) {
                ev.setJsonBody(jsonPayload);
            }
            ev.setRollbackOnError(rollbackOnError);
            marshallingParam(pc, pc.getPage(), ev, true, serve.class);
        } catch (NamingException e) {
            e.printStackTrace();
        }
    }

    public static void init(String $resourcePath, String $suffix, String $splitter, int nWorkerThread
            , String ymlConfigurationFile) {
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

        if(ymlConfigurationFile == null) {
            ymlConfigurationFile = "paas.yml";
            if(Paas.class.getResource(ymlConfigurationFile) == null) {
                /** If default configuration path not exist, just create a empty paasConf **/
                paasConf = new HashMap<>();
                return;
            }
        }

        InputStream inputStream = null;
        try {
            Yaml yaml = new Yaml();
            inputStream  = Paas.class
                    .getClassLoader()
                    .getResourceAsStream(ymlConfigurationFile);
            paasConf = yaml.load(inputStream);
        } catch (Exception e) {
            paasConf = new HashMap<>();
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void init(String $resourcePath, String $suffix, String $splitter, int nWorkerThread) {
       init($resourcePath, $suffix, $splitter, nWorkerThread, null);
    }

    @Deprecated
    public static <T extends Object> T getEvent(PageContext pc) {
        Event ev = (Event) pc.getAttribute("$_ev_ctx", PageContext.REQUEST_SCOPE);
        return (T) ev;
    }

    public static void hook(PageContext pc) {
        Event ev = (Event) pc.getAttribute("$_ev_ctx", PageContext.REQUEST_SCOPE);
        marshallingParam(pc, pc.getPage(), /*ev.get_prevPage(),*/ ev, false, hook.class);
    }

    public static Object directResult(String resourcePath, Event $ev) throws Exception {
        return $ev.dispatch(resourcePath).getResult();
    }

    public static <E> E getPath(Map<String, Object> values, String path) {
        if(path.length() == 0) {
            return null;
        }
        String[] keys = path.split("(?<![\\\\])\\.");

        Object p = values.get(keys[0].replaceAll("\\\\.", "."));
        for(int i=1,sz=keys.length;i<sz;i++) {
            if(p == null) {
                return null;
            }
            if(p instanceof List) {
                p = ((List)p).get(Integer.parseInt(keys[i]));
            } else {
                p = ((Map)p).get(keys[i]);
            }
        }
        return (E) p;
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

//    private static String getRequestBody(HttpServletRequest req) throws IOException {
//        java.util.Scanner s = new java.util.Scanner(req.getInputStream(), "UTF-8").useDelimiter("\\A");
//        return s.hasNext() ? s.next() : "";
//    }

    private static void marshallingParam(PageContext pc, Object pageObj,/* Object _prevObj, */Event ev, boolean finalizing,
                                         Class<? extends Annotation> hookOrServe ) {
        Class<?> clazz = pageObj.getClass();
        String name = clazz.getCanonicalName();
        int hashCode = clazz.hashCode();
        boolean rebuild;

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
                if (m.isAnnotationPresent(hookOrServe)) {
//                    if(!isQualifyMethod(m)){
//                      // TODO if not qualify, throw e
//                    }
                    m.setAccessible(true);
                    Annotation[][] paramAnnotation = m.getParameterAnnotations();
                    List<ArgHandler> argsHandlerList = new ArrayList(paramAnnotation.length);
                    ArgHandler h;
                    Annotation a;
                    for (int i = 0, j = 0, sz = paramAnnotation.length; j < sz; i++, j++) {
                        Annotation[] as = paramAnnotation[j];
                        if (as.length > 0 && (a = as[0]) != null) {
                            Class<?> atype = a.annotationType();
                            if (atype.isAssignableFrom(param.class)) {
                                param p = (param) a;
                                argsHandlerList.add(new ArgHandler(ArgEnum.PARAM, p.value()));
                            } else if (atype.isAssignableFrom(attr.class)) {
                                attr $attr = (attr) a;
                                argsHandlerList.add(new ArgHandler(ArgEnum.ATTR, $attr.value()));
                            } else if (atype.isAssignableFrom(conf.class)) {
                                conf $conf = (conf) a;
                                argsHandlerList.add(new ArgHandler(ArgEnum.CONF, $conf.value()));
                            } else if (atype.isAssignableFrom(json.class)) {
                                json $json = (json) a;
                                String key = $json.value();
                                if (key.length() == 0) {
                                    h = new ArgHandler(ArgEnum.JSON);
                                } else {
                                    h = new ArgHandler(ArgEnum.JSONKEY, key);
                                }
                                argsHandlerList.add(h);
                            } else if (atype.isAssignableFrom(page.class)) {
                                argsHandlerList.add(new ArgHandler(ArgEnum.PAGECTX));
                            } else if (atype.isAssignableFrom(request.class)) {
                                argsHandlerList.add(new ArgHandler(ArgEnum.REQUEST));
                            } else if (atype.isAssignableFrom(response.class)) {
                                argsHandlerList.add(new ArgHandler(ArgEnum.RESPONSE));
                            } else if (atype.isAssignableFrom(event.class)) {
                                argsHandlerList.add(new ArgHandler(ArgEnum.EVENT));
                            } else if (atype.isAssignableFrom(writer.class)) {
                                argsHandlerList.add(new ArgHandler(ArgEnum.WRITER));
                            } else if (atype.isAssignableFrom(out.class)) {
                                argsHandlerList.add(new ArgHandler(ArgEnum.OUT));
                            } else if (atype.isAssignableFrom(any.class)) {
                                any $any = (any) a;
                                String key = $any.value();
                                argsHandlerList.add(new ArgHandler(ArgEnum.ANY, key));
                            }
                        }
                    }

                    mprms.setM(m);
                    mprms.setArgsHandlers(argsHandlerList.toArray(new ArgHandler[0]));
                    break;
                }
            }
            // find the first param will do
            cacheMethodParams.put(name, mprms);
        }

        boolean hasError = false;
        try {
            if (!cacheMethodParams.containsKey(name)) {
                return;
            }
            ArgHandler[] argsHandlers = mprms.getArgsHandlers();
            HttpServletRequest req = (HttpServletRequest) pc.getRequest();
            HttpServletResponse resp = (HttpServletResponse) pc.getResponse();
            Object[] args;
            JSONObject jsonPayload = ev.getJsonPayload();
            boolean hasJson = ev.hasJson();
            if (req.getParameterMap().size() > 0 || req.getAttributeNames().hasMoreElements()) {
                args = new Object[argsHandlers.length];
                ArgHandler h;
                String key;
                try {
                    for (int i = 0, sz = args.length; i < sz; i++) {
                        h = argsHandlers[i];
                        key = h.argKey;
                        switch (h.argEnum) {
                            case ATTR:
                                args[i] = req.getAttribute(key);
                                break;
                            case EVENT:
                                args[i] = ev;
                                break;
                            case JSON:
                                args[i] = jsonPayload;
                                break;
                            case JSONKEY:
                                args[i] = jsonPayload.get(key);
                                break;
                            case ANY:
                                if (req.getAttribute(key) != null) {
                                    args[i] = req.getAttribute(key);
                                } else if (hasJson && jsonPayload.get(key) != null) {
                                    args[i] = jsonPayload.get(key);
                                } else {
                                    args[i] = req.getParameter(key);
                                }
                                break;
                            case PAGECTX:
                                args[i] = pc;
                                break;
                            case PARAM:
                                args[i] = req.getParameter(key);
                                break;
                            case REQUEST:
                                args[i] = req;
                                break;
                            case RESPONSE:
                                args[i] = resp;
                                break;
                            case WRITER:
                                args[i] = resp.getWriter();
                                break;
                            case OUT:
                                args[i] = pc.getOut();
                                break;
                            case CONF:
                                args[i] = getPath(paasConf, key);
                                break;
                            default:
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

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
                }
            }
        } finally {
            if (finalizing) {
                if (ev instanceof JtaEvent) {
                    JtaEvent evt = (JtaEvent) ev;
                    try {
                        if (hasError && evt.isRollbackOnError()) {
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
