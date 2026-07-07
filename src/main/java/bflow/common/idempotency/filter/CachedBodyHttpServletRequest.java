package bflow.common.idempotency.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/** Wraps a request so its body can be read multiple times. */
class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    /**
    * Cached request body stored as a byte array to enable
    * multiple reads of the HTTP request input stream.
    */
    private final byte[] cachedBody;

    CachedBodyHttpServletRequest(final HttpServletRequest request)
    throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream buffer = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override public boolean isFinished() {
                return buffer.available() == 0;
            }
            @Override public boolean isReady() {
                return true;
            }
            @Override public void setReadListener(final ReadListener l) { }
            @Override public int read() {
                return buffer.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }
}
