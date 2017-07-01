/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.warps.monitor.crawl.protocol.http;

import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.warps.monitor.common.MimeUtil;
import org.warps.monitor.crawl.protocol.Content;
import org.warps.monitor.crawl.protocol.Protocol;
import org.warps.monitor.crawl.protocol.ProtocolException;
import org.warps.monitor.crawl.protocol.ProtocolOutput;
import org.warps.monitor.net.protocols.Response;
import org.warps.pulsar.common.DeflateUtils;
import org.warps.pulsar.common.GZIPUtils;
import org.warps.pulsar.common.Params;
import org.warps.pulsar.common.proxy.NoProxyException;
import org.warps.pulsar.persist.ProtocolStatus;
import org.warps.pulsar.persist.ProtocolStatusCodes;
import org.warps.pulsar.persist.WebPage;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.warps.pulsar.persist.metadata.Name.RESPONSE_TIME;

public abstract class HttpBase implements Protocol {

  public static final int BUFFER_SIZE = 8 * 1024;

  private static final byte[] EMPTY_CONTENT = new byte[0];

  /** The network timeout in millisecond */
  protected int timeout = 10000;

  /**
   * The max retry time if there is a network problem especially when uses a
   * proxy pool
   */
  protected int fetchMaxRetry = 3;

  /** The length limit for downloaded content, in bytes. */
  protected int maxContent = 64 * 1024;

  /** The PulsarConstants 'User-Agent' request header */
  protected String userAgent = getAgentString("PulsarCVS", null, "PulsarConstants",
      "http://pulsar.warpseed.org/bot.html", "agent@pulsar.warpseed.org");

  /** The "Accept-Language" request header value. */
  protected String acceptLanguage = "en-us,en-gb,en;q=0.7,*;q=0.3";

  /** The "Accept" request header value. */
  protected String accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

  /** The default LOG */
  private Logger LOG = LoggerFactory.getLogger(HttpBase.class);

  /** The pulsar configuration */
  private Configuration conf = null;

  private MimeUtil mimeTypes;

  /** Do we use HTTP/1.1? */
  protected boolean useHttp11 = false;

  /** Response Time */
  protected boolean storeResponseTime = true;

  /** Which TLS/SSL protocols to support */
  protected Set<String> tlsPreferredProtocols;

  /** Which TLS/SSL cipher suites to support */
  protected Set<String> tlsPreferredCipherSuites;

  /** Prevent multiple threads generate the same log unnecessary */
  private static AtomicBoolean parametersReported = new AtomicBoolean(false);

  /** Creates a new instance of HttpBase */
  public HttpBase(Logger LOG) {
    this.LOG = LOG;
  }

  // Inherited Javadoc
  public void setConf(Configuration conf) {
    this.conf = conf;
    this.timeout = conf.getInt("http.timeout", 10000);
    this.fetchMaxRetry = conf.getInt("http.fetch.max.retry", 3);
    this.maxContent = conf.getInt("http.content.limit", 64 * 1024);
//    this.userAgent = getAgentString(conf.get("http.agent.name"),
//        conf.get("http.agent.version"), conf.get("http.agent.description"),
//        conf.get("http.agent.url"), conf.get("http.agent.email"));
    this.userAgent = getAgentString(conf.get("http.agent.name"));

    this.acceptLanguage = conf.get("http.accept.language", acceptLanguage);
    this.accept = conf.get("http.accept", accept);
    this.mimeTypes = new MimeUtil(conf);
    this.useHttp11 = conf.getBoolean("http.useHttp11", false);
    this.storeResponseTime = conf.getBoolean("http.store.responsetime", true);

    String[] protocols = conf.getStrings("http.tls.supported.protocols",
        "TLSv1.2", "TLSv1.1", "TLSv1", "SSLv3");
    String[] ciphers = conf.getStrings("http.tls.supported.cipher.suites",
        "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
        "TLS_RSA_WITH_AES_256_CBC_SHA256",
        "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
        "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
        "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
        "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
        "TLS_DHE_RSA_WITH_AES_256_CBC_SHA", "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
        "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA", "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
        "SSL_RSA_WITH_RC4_128_SHA", "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
        "TLS_ECDH_RSA_WITH_RC4_128_SHA",
        "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
        "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
        "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "SSL_RSA_WITH_RC4_128_MD5",
        "TLS_EMPTY_RENEGOTIATION_INFO_SCSV", "TLS_RSA_WITH_NULL_SHA256",
        "TLS_ECDHE_ECDSA_WITH_NULL_SHA", "TLS_ECDHE_RSA_WITH_NULL_SHA",
        "SSL_RSA_WITH_NULL_SHA", "TLS_ECDH_ECDSA_WITH_NULL_SHA",
        "TLS_ECDH_RSA_WITH_NULL_SHA", "SSL_RSA_WITH_NULL_MD5",
        "SSL_RSA_WITH_DES_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA",
        "SSL_DHE_DSS_WITH_DES_CBC_SHA", "TLS_KRB5_WITH_RC4_128_SHA",
        "TLS_KRB5_WITH_RC4_128_MD5", "TLS_KRB5_WITH_3DES_EDE_CBC_SHA",
        "TLS_KRB5_WITH_3DES_EDE_CBC_MD5", "TLS_KRB5_WITH_DES_CBC_SHA",
        "TLS_KRB5_WITH_DES_CBC_MD5");

    tlsPreferredProtocols = new HashSet<>(Arrays.asList(protocols));
    tlsPreferredCipherSuites = new HashSet<>(Arrays.asList(ciphers));

    if (!parametersReported.get()) {
      LOG.info(Params.formatAsLine(
          "httpTimeout", timeout,
          "maxContent", maxContent,
          "userAgent", userAgent,
          "httpAcceptLanguage", acceptLanguage,
          "httpAccept", accept
      ));
      parametersReported.set(true);
    }
  }

  // Inherited Javadoc
  public Configuration getConf() {
    return this.conf;
  }

  public ProtocolOutput getProtocolOutput(String url, WebPage page) {
    try {
      URL u = new URL(url);
      Response response = null; // make a request

      Instant startTime = Instant.now();

      int retry = 0;
      Throwable lastThrowable = null;
      while (response == null && retry < this.fetchMaxRetry) {
        try {
          if (retry > 0) {
            LOG.info("Fetching {} retry for {} times", url, retry);
          }

          response = getResponse(u, page, false);
        } catch (Throwable e) {
          ++retry;
          response = null;
          lastThrowable = e;
          LOG.warn(e.toString());
        }
      }

      // TODO : Just use throw mechanism
      if (response == null) {
        ProtocolStatus protocolStatus;
        if (lastThrowable instanceof ConnectException) {
          protocolStatus = ProtocolStatus.makeStatus(ProtocolStatusCodes.CONNECTION_TIMED_OUT, "retry : " + retry);
        }
        else if (lastThrowable instanceof SocketTimeoutException) {
          protocolStatus = ProtocolStatus.makeStatus(ProtocolStatusCodes.CONNECTION_TIMED_OUT, "retry : " + retry);
        }
        else if (lastThrowable instanceof UnknownHostException) {
          protocolStatus = ProtocolStatus.makeStatus(ProtocolStatusCodes.UNKNOWN_HOST);
        }
        else {
          protocolStatus = ProtocolStatus.makeStatus(ProtocolStatusCodes.EXCEPTION, lastThrowable + ", retry : " + retry);
        }

        LOG.warn(ProtocolStatus.getMessage(protocolStatus));

        return new ProtocolOutput(null, protocolStatus);
      }

      if (this.storeResponseTime) {
        // -1 hour means an invalid response time
        Duration elapsedTime = Duration.between(startTime, Instant.now());
        page.putMetadata(RESPONSE_TIME, elapsedTime.toString());
      }

      int code = response.getCode();
      byte[] content = response.getContent();
      Content c = new Content(u.toString(), u.toString(),
          (content == null ? EMPTY_CONTENT : content),
          response.getHeader("Content-Type"), response.getHeaders(), mimeTypes);

      if (code == 200) { // got a good response
        return new ProtocolOutput(c); // return it
      } else if (code >= 300 && code < 400) { // handle redirect
        String location = response.getHeader("Location");
        // some broken servers, such as MS IIS, use lowercase header name...
        if (location == null)
          location = response.getHeader("location");
        if (location == null)
          location = "";
        u = new URL(u, location);
        int protocolStatusCode;
        switch (code) {
        case 300: // multiple choices, preferred value in Location
          protocolStatusCode = ProtocolStatusCodes.MOVED;
          break;
        case 301: // moved permanently
        case 305: // use proxy (Location is URL of proxy)
          protocolStatusCode = ProtocolStatusCodes.MOVED;
          break;
        case 302: // found (temporarily moved)
        case 303: // see other (redirect after POST)
        case 307: // temporary redirect
          protocolStatusCode = ProtocolStatus.TEMP_MOVED;
          break;
        case 304: // not modified
          protocolStatusCode = ProtocolStatus.NOTMODIFIED;
          break;
        default:
          protocolStatusCode = ProtocolStatus.MOVED;
        }
        // handle this in the higher layer.
        return new ProtocolOutput(c, ProtocolStatus.makeStatus(protocolStatusCode, u));
      } else if (code == 400) {
        LOG.trace("400 Bad request: " + u);
        return new ProtocolOutput(c, ProtocolStatus.makeStatus(ProtocolStatusCodes.GONE, "400 Bad request: " + u));
      } else if (code == 401) {
        // requires authorization, but no valid auth provided.
        LOG.trace("401 Authentication Required");
        return new ProtocolOutput(c, ProtocolStatus.makeStatus(ProtocolStatusCodes.ACCESS_DENIED, "Authentication required: " + url));
      } else if (code == 404) {
        return new ProtocolOutput(c, ProtocolStatus.makeStatus(ProtocolStatusCodes.NOTFOUND, u));
      } else if (code == 410) { // permanently GONE
        return new ProtocolOutput(c, ProtocolStatus.makeStatus(ProtocolStatusCodes.GONE, "Http: " + code + " url=" + u));
      } else {
        return new ProtocolOutput(c, ProtocolStatus.makeStatus(ProtocolStatusCodes.EXCEPTION, "Http code=" + code + ", url=" + u));
      }
    }
    catch (Throwable e) {
      // LOG.error("Failed to get response : ", e);
      return new ProtocolOutput(null, ProtocolStatus.makeStatus(ProtocolStatusCodes.EXCEPTION, e.toString()));
    }
  }

  public int getTimeout() {
    return timeout;
  }

  public int getMaxContent() {
    return maxContent;
  }

  public String getUserAgent() {
    return userAgent;
  }

  /**
   * Value of "Accept-Language" request header sent by PulsarConstants.
   * 
   * @return The value of the header "Accept-Language" header.
   */
  public String getAcceptLanguage() {
    return acceptLanguage;
  }

  public String getAccept() {
    return accept;
  }

  public boolean getUseHttp11() {
    return useHttp11;
  }

  public Set<String> getTlsPreferredCipherSuites() {
    return tlsPreferredCipherSuites;
  }

  public Set<String> getTlsPreferredProtocols() {
    return tlsPreferredProtocols;
  }

  private static String getAgentString(String agentName) {
    return agentName;
  }

  private static String getAgentString(String agentName, String agentVersion,
      String agentDesc, String agentURL, String agentEmail) {

    if ((agentName == null) || (agentName.trim().length() == 0)) {
      Protocol.LOG.error("No User-Agent string set (http.agent.name)!");
    }

    StringBuffer buf = new StringBuffer();

    buf.append(agentName);
    if (agentVersion != null) {
      buf.append("/");
      buf.append(agentVersion);
    }
    if (((agentDesc != null) && (agentDesc.length() != 0))
        || ((agentEmail != null) && (agentEmail.length() != 0))
        || ((agentURL != null) && (agentURL.length() != 0))) {
      buf.append(" (");

      if ((agentDesc != null) && (agentDesc.length() != 0)) {
        buf.append(agentDesc);
        if ((agentURL != null) || (agentEmail != null))
          buf.append("; ");
      }

      if ((agentURL != null) && (agentURL.length() != 0)) {
        buf.append(agentURL);
        if (agentEmail != null)
          buf.append("; ");
      }

      if ((agentEmail != null) && (agentEmail.length() != 0))
        buf.append(agentEmail);

      buf.append(")");
    }
    return buf.toString();
  }

  public byte[] processGzipEncoded(byte[] compressed, URL url) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("uncompressing....");
    }

    byte[] content;
    if (getMaxContent() >= 0) {
      content = GZIPUtils.unzipBestEffort(compressed, getMaxContent());
    } else {
      content = GZIPUtils.unzipBestEffort(compressed);
    }

    if (content == null) {
      throw new IOException("unzipBestEffort returned null");
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace("fetched " + compressed.length
          + " bytes of compressed content (expanded to " + content.length
          + " bytes) from " + url);
    }
    return content;
  }

  public byte[] processDeflateEncoded(byte[] compressed, URL url) throws IOException {

    if (LOG.isTraceEnabled()) {
      LOG.trace("inflating....");
    }

    byte[] content = DeflateUtils.inflateBestEffort(compressed, getMaxContent());

    if (content == null)
      throw new IOException("inflateBestEffort returned null");

    if (LOG.isTraceEnabled()) {
      LOG.trace("fetched " + compressed.length
          + " bytes of compressed content (expanded to " + content.length
          + " bytes) from " + url);
    }
    return content;
  }

  protected static void main(HttpBase http, String[] args) throws Exception {
    @SuppressWarnings("unused")
    boolean verbose = false;
    String url = null;

    String usage = "Usage: Http [-verbose] [-timeout N] url";

    if (args.length == 0) {
      System.err.println(usage);
      System.exit(-1);
    }

    for (int i = 0; i < args.length; i++) { // parse command line
      if (args[i].equals("-timeout")) { // found -timeout option
        http.timeout = Integer.parseInt(args[++i]) * 1000;
      } else if (args[i].equals("-verbose")) { // found -verbose option
        verbose = true;
      } else if (i != args.length - 1) {
        System.err.println(usage);
        System.exit(-1);
      } else
        // root is required parameter
        url = args[i];
    }

    ProtocolOutput out = http.getProtocolOutput(url, WebPage.newWebPage());
    Content content = out.getContent();

    System.out.println("Status: " + out.getStatus());
    if (content != null) {
      System.out.println("Content Type: " + content.getContentType());
      System.out.println("Content Length: "
          + content.getMetadata().get(Response.CONTENT_LENGTH));
      System.out.println("Content:");
      String text = new String(content.getContent());
      System.out.println(text);
    }
  }

  protected abstract Response getResponse(URL url, WebPage page,
      boolean followRedirects) throws ProtocolException, IOException, NoProxyException;
}
