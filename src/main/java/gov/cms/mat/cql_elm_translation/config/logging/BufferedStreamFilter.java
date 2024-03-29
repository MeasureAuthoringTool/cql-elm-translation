package gov.cms.mat.cql_elm_translation.config.logging;

import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Slf4j
public class BufferedStreamFilter implements Filter {
  @Slf4j
  public static class NonBufferingRequestWrapper extends HttpServletRequestWrapper {
    private final String body;

    public NonBufferingRequestWrapper(HttpServletRequest request) {
      super(request);

      StringBuilder stringBuilder = new StringBuilder();
      BufferedReader bufferedReader = null;

      try {
        InputStream inputStream = request.getInputStream();

        if (inputStream != null) {
          bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

          char[] charBuffer = new char[80000];
          int bytesRead;

          while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
            stringBuilder.append(charBuffer, 0, bytesRead);
          }
        }
      } catch (IOException ioe) {
        log.error("Error buffering input stream.", ioe);
      } finally {
        if (bufferedReader != null) {
          try {
            bufferedReader.close();
          } catch (IOException ioe) {
            log.error("Error closing reader.", ioe);
          }
        }
      }
      body = stringBuilder.toString();
    }

    @Override
    public ServletInputStream getInputStream() {
      final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes());
      return new ServletInputStream() {
        public boolean isFinished() {
          return false;
        }

        public boolean isReady() {
          return true;
        }

        public void setReadListener(ReadListener readListener) {
          throw new UnsupportedOperationException();
        }

        public int read() {
          return byteArrayInputStream.read();
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream()));
    }
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    chain.doFilter(new NonBufferingRequestWrapper((HttpServletRequest) request), response);
  }

  @Override
  public void destroy() {}
}
