package com.github.taymindis.paas;

import javax.servlet.ServletException;
import javax.servlet.jsp.PageContext;
import java.io.IOException;
import java.util.concurrent.*;


/**
 * dispatching async between web container
 */
public class EventFuture extends Paas implements Event {
    private Future<Void> f;
    private Object result;

    protected EventFuture(PageContext pc) {
        super(pc);
        this.f = null;
        this.result = null;
    }


    /**
     * dispatching first between the file via web container, get the result at the end of request
     *
     * @param jspPath resource path
     * @return DispatchFuture
     * @throws IOException      IOException
     * @throws ServletException ServletException
     */
    @Override
    public synchronized EventFuture dispatch(final String jspPath) throws Exception {
        if (isDispatchFutureEnabled()) {
            throw new Exception("Background Task feature is not enabled");
        }
        if (f != null) {
            throw new Exception("Process has been executed");
        }
        clearPreviousStatus();
        final PageContext $pc = this._pageContext;
        final EventFuture df = this;
        f = getBgExecutor().submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                $pc.include(resourcePath + jspPath.replace(splitter, "/") + suffix);
                return null;
            }
        });


        return this;
    }

    @Override
    public boolean isDone() {
        return this.f.isDone();
    }

    @Override
    public boolean isCancelled() {
        return this.f.isCancelled();
    }



    @Override
    public void setResult(Object rs) {
        this.result = rs;
    }

    @Override
    public <T extends Object> T  getResult() {
        if(this.result == null) {
            try {
                f.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        return (T) result;
    }

    @Override
    public <T extends Object> T  getResult(long timeout, TimeUnit unit)  {
        if(this.result == null) {
            try {
                f.get(timeout, unit);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        }
        return (T) result;
    }

}
