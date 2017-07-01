/*
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
package org.warps.monitor.common;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configuration;
import org.warps.monitor.net.protocols.Response;
import org.warps.pulsar.persist.WebPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple class for detecting character encodings.
 * 
 * <p>
 * Broadly this encompasses two functions, which are distinctly separate:
 * 
 * <ol>
 * <li>Auto detecting a set of "clues" from input text.</li>
 * <li>Taking a set of clues and making a "best guess" as to the "real"
 * encoding.</li>
 * </ol>
 * </p>
 * 
 * <p>
 * A caller will often have some extra information about what the encoding might
 * be (e.g. from the HTTP header or HTML meta-tags, often wrong but still
 * potentially useful clues). The types of clues may differ from caller to
 * caller. Thus a typical calling sequence is:
 * <ul>
 * <li>Run step (1) to generate a set of auto-detected clues;</li>
 * <li>Combine these clues with the caller-dependent "extra clues" available;</li>
 * <li>Run step (2) to guess what the most probable answer is.</li>
 * </p>
 */
public class EncodingDetector {

  public final static Utf8 CONTENT_TYPE_UTF8 = new Utf8(Response.CONTENT_TYPE);

  // I used 1000 bytes at first, but found that some documents have
  // meta tag well past the first 1000 bytes.
  // (e.g. http://cn.promo.yahoo.com/customcare/music.html)
  private static final int CHUNK_SIZE = 2000;

  // PULSAR-1006 Meta equiv with single quotes not accepted
  private static Pattern metaPattern = Pattern.compile(
      "<meta\\s+([^>]*http-equiv=(\"|')?content-type(\"|')?[^>]*)>",
      Pattern.CASE_INSENSITIVE);
  private static Pattern charsetPattern = Pattern.compile(
      "charset=\\s*([a-z][_\\-0-9a-z]*)", Pattern.CASE_INSENSITIVE);
  private static Pattern charsetPatternHTML5 = Pattern.compile(
      "<meta\\s+charset\\s*=\\s*[\"']?([a-z][_\\-0-9a-z]*)[^>]*>",
      Pattern.CASE_INSENSITIVE);

  private class EncodingClue {
    private final String value;
    private final String source;
    private final int confidence;

    // Constructor for clues with no confidence values (ignore thresholds)
    public EncodingClue(String value, String source) {
      this(value, source, NO_THRESHOLD);
    }

    public EncodingClue(String value, String source, int confidence) {
      this.value = value.toLowerCase();
      this.source = source;
      this.confidence = confidence;
    }

    @SuppressWarnings("unused")
    public String getSource() {
      return source;
    }

    @SuppressWarnings("unused")
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return value + " (" + source
          + ((confidence >= 0) ? ", " + confidence + "% confidence" : "") + ")";
    }

    public boolean isEmpty() {
      return (value == null || "".equals(value));
    }

    public boolean meetsThreshold() {
      return (confidence < 0 || (minConfidence >= 0 && confidence >= minConfidence));
    }
  }

  public static final Logger LOG = LoggerFactory.getLogger(EncodingDetector.class);

  public static final int NO_THRESHOLD = -1;

  public static final String MIN_CONFIDENCE_KEY = "encodingdetector.charset.min.confidence";

  private static final HashMap<String, String> ALIASES = new HashMap<>();

  private static final HashSet<String> DETECTABLES = new HashSet<>();

  private String defaultCharEncoding;

  // CharsetDetector will die without a minimum amount of data.
  private static final int MIN_LENGTH = 4;

  static {
    DETECTABLES.add("text/html");
    DETECTABLES.add("text/plain");
    DETECTABLES.add("text/richtext");
    DETECTABLES.add("text/rtf");
    DETECTABLES.add("text/sgml");
    DETECTABLES.add("text/tab-separated-values");
    DETECTABLES.add("text/xml");
    DETECTABLES.add("application/rss+xml");
    DETECTABLES.add("application/xhtml+xml");
    /*
     * the following map is not an alias mapping table, but maps character
     * encodings which are often used in mislabelled documents to their correct
     * encodings. For instance, there are a lot of documents labelled
     * 'ISO-8859-1' which contain characters not covered by ISO-8859-1 but
     * covered by windows-1252. Because windows-1252 is a superset of ISO-8859-1
     * (sharing code points for the common part), it's better to treat
     * ISO-8859-1 as synonymous with windows-1252 than to reject, as invalid,
     * documents labelled as ISO-8859-1 that have characters outside ISO-8859-1.
     */
    ALIASES.put("ISO-8859-1", "windows-1252");
    ALIASES.put("EUC-KR", "x-windows-949");
    ALIASES.put("x-EUC-CN", "GB18030");
    /**
     * GB18030有两个版本：GB18030-2000和GB18030-2005，
     * GB18030-2000是GBK的取代版本，它的主要特点是在GBK基础上增加了CJK统一汉字扩充A的汉字。
     * GB18030-2005的主要特点是在GB18030-2000基础上增加了CJK统一汉字扩充B的汉字。
     * @see http://www.fmddlmyy.cn/text24.html
     * */
    // ALIASES.put("GBK", "GB18030");
    // ALIASES.put("Big5", "Big5HKSCS");
    // ALIASES.put("TIS620", "Cp874");
    // ALIASES.put("ISO-8859-11", "Cp874");
  }

  private final int minConfidence;

  private final CharsetDetector detector = new CharsetDetector();

  private final List<EncodingClue> clues = new ArrayList<>();

  public EncodingDetector(Configuration conf) {
    this.minConfidence = conf.getInt(MIN_CONFIDENCE_KEY, -1);
    this.defaultCharEncoding = conf.get("parser.character.encoding.default", "windows-1252");
  }

  public String sniffEncoding(WebPage page) {
    autoDetectClues(page, true);
    addClue(sniffCharacterEncoding(page.getContent().array()), "sniffed");

    return guessEncoding(page, defaultCharEncoding);
  }

  public void autoDetectClues(WebPage page, boolean filter) {
    autoDetectClues(page.getContent(), page.getContentType(),
        parseCharacterEncoding(page.getHeaders().get(CONTENT_TYPE_UTF8)),
        filter);
  }

  /**
   * Given a <code>byte[]</code> representing an html file of an
   * <em>unknown</em> encoding, read out 'charset' parameter in the meta tag
   * from the first <code>CHUNK_SIZE</code> bytes. If there's no meta tag for
   * Content-Type or no charset is specified, the content is checked for a
   * Unicode Byte Order Mark (BOM). This will also cover non-byte oriented
   * character encodings (UTF-16 only). If no character set can be determined,
   * <code>null</code> is returned.
   * See also
   * http://www.w3.org/International/questions/qa-html-encoding-declarations,
   * http://www.w3.org/TR/2011/WD-html5-diff-20110405/#character-encoding, and
   * http://www.w3.org/TR/REC-xml/#sec-guessing
   *
   * @param content
   *          <code>byte[]</code> representation of an html file
   */
  public String sniffCharacterEncoding(byte[] content) {
    int length = content.length < CHUNK_SIZE ? content.length : CHUNK_SIZE;

    // We don't care about non-ASCII parts so that it's sufficient
    // to just inflate each byte to a 16-bit value by padding.
    // For instance, the sequence {0x41, 0x82, 0xb7} will be turned into
    // {U+0041, U+0082, U+00B7}.
    String str = new String(content, 0, length, StandardCharsets.US_ASCII);

    Matcher metaMatcher = metaPattern.matcher(str);
    String encoding = null;
    if (metaMatcher.find()) {
      Matcher charsetMatcher = charsetPattern.matcher(metaMatcher.group(1));
      if (charsetMatcher.find())
        encoding = charsetMatcher.group(1);
    }
    if (encoding == null) {
      // check for HTML5 meta charset
      metaMatcher = charsetPatternHTML5.matcher(str);
      if (metaMatcher.find()) {
        encoding = metaMatcher.group(1);
      }
    }
    if (encoding == null) {
      // check for BOM
      if (content.length >= 3 && content[0] == (byte) 0xEF
          && content[1] == (byte) 0xBB && content[2] == (byte) 0xBF) {
        encoding = "UTF-8";
      } else if (content.length >= 2) {
        if (content[0] == (byte) 0xFF && content[1] == (byte) 0xFE) {
          encoding = "UTF-16LE";
        } else if (content[0] == (byte) 0xFE && content[1] == (byte) 0xFF) {
          encoding = "UTF-16BE";
        }
      }
    }

    return encoding;
  }

  private void autoDetectClues(ByteBuffer dataBuffer, String contentType, String encoding, boolean filter) {
    int length = dataBuffer.remaining();

    if (minConfidence >= 0 && DETECTABLES.contains(contentType) && length > MIN_LENGTH) {
      CharsetMatch[] matches = null;

      // do all these in a try/catch; setText and detect/detectAll
      // will sometimes throw exceptions
      try {
        detector.enableInputFilter(filter);
        detector.setText(new ByteArrayInputStream(dataBuffer.array(),
            dataBuffer.arrayOffset() + dataBuffer.position(), length));
        matches = detector.detectAll();
      } catch (Exception e) {
        LOG.debug("Exception from ICU4J (ignoring): ", e);
      }

      if (matches != null) {
        for (CharsetMatch match : matches) {
          addClue(match.getName(), "detect", match.getConfidence());
        }
      }
    }

    // add character encoding coming from HTTP response header
    addClue(encoding, "header");
  }

  public void addClue(String value, String source, int confidence) {
    if (value == null || "".equals(value)) {
      return;
    }
    value = resolveEncodingAlias(value);
    if (value != null) {
      clues.add(new EncodingClue(value, source, confidence));
    }
  }

  public void addClue(String value, String source) {
    addClue(value, source, NO_THRESHOLD);
  }

  /**
   * Guess the encoding with the previously specified list of clues.
   * 
   * @param page
   *          URL's row
   * @param defaultValue
   *          Default encoding to return if no encoding can be detected with
   *          enough confidence. Note that this will <b>not</b> be normalized
   *          with {@link EncodingDetector#resolveEncodingAlias}
   * 
   * @return Guessed encoding or defaultValue
   */
  public String guessEncoding(WebPage page, String defaultValue) {
    return guessEncoding(page.getBaseUrl(), defaultValue);
  }

  /**
   * Guess the encoding with the previously specified list of clues.
   * 
   * @param baseUrl
   *          Base URL
   * @param defaultValue
   *          Default encoding to return if no encoding can be detected with
   *          enough confidence. Note that this will <b>not</b> be normalized
   *          with {@link EncodingDetector#resolveEncodingAlias}
   * 
   * @return Guessed encoding or defaultValue
   */
  private String guessEncoding(String baseUrl, String defaultValue) {
    /*
     * This algorithm could be replaced by something more sophisticated; ideally
     * we would gather a bunch of data on where various clues (autodetect, HTTP
     * headers, HTML meta tags, etc.) disagree, tag each with the correct
     * answer, and use machine learning/some statistical method to generate a
     * better heuristic.
     */

    if (LOG.isTraceEnabled()) {
      findDisagreements(baseUrl, clues);
    }

    /*
     * Go down the list of encoding "clues". Use a clue if: 1. Has a confidence
     * value which meets our confidence threshold, OR 2. Doesn't meet the
     * threshold, but is the best try, since nothing else is available.
     */
    EncodingClue defaultClue = new EncodingClue(defaultValue, "default");
    EncodingClue bestClue = defaultClue;

    for (EncodingClue clue : clues) {
      if (LOG.isTraceEnabled()) {
        LOG.trace(baseUrl + ": charset " + clue);
      }
      String charset = clue.value;
      if (minConfidence >= 0 && clue.confidence >= minConfidence) {
        if (LOG.isTraceEnabled()) {
          LOG.trace(baseUrl + ": Choosing encoding: " + charset + " with confidence " + clue.confidence);
        }
        return resolveEncodingAlias(charset);
      } else if (clue.confidence == NO_THRESHOLD && bestClue == defaultClue) {
        bestClue = clue;
      }
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace(baseUrl + ": Choosing encoding: " + bestClue);
    }

    return bestClue.value.toLowerCase();
  }

  /** Clears all clues. */
  public void clearClues() {
    clues.clear();
  }

  /*
   * Strictly for analysis, look for "disagreements." The top guess from each
   * source is examined; if these meet the threshold and disagree, then we log
   * the information -- useful for testing or generating training data for a
   * better heuristic.
   */
  private void findDisagreements(String url, List<EncodingClue> newClues) {
    HashSet<String> valsSeen = new HashSet<>();
    HashSet<String> sourcesSeen = new HashSet<>();
    boolean disagreement = false;
    for (int i = 0; i < newClues.size(); i++) {
      EncodingClue clue = newClues.get(i);
      if (!clue.isEmpty() && !sourcesSeen.contains(clue.source)) {
        if (valsSeen.size() > 0 && !valsSeen.contains(clue.value)
            && clue.meetsThreshold()) {
          disagreement = true;
        }
        if (clue.meetsThreshold()) {
          valsSeen.add(clue.value);
        }
        sourcesSeen.add(clue.source);
      }
    }
    if (disagreement) {
      // dump all values in case of disagreement
      StringBuilder sb = new StringBuilder();
      sb.append("Disagreement: ").append(url).append("; ");
      for (int i = 0; i < newClues.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(newClues.get(i));
      }
      LOG.trace(sb.toString());
    }
  }

  public static String resolveEncodingAlias(String encoding) {
    try {
      if (encoding == null || !Charset.isSupported(encoding)) {
        return null;
      }

      String canonicalName = Charset.forName(encoding).name();
      String encodingAlias = ALIASES.containsKey(canonicalName) ? ALIASES.get(canonicalName) : canonicalName;
      return encodingAlias.toLowerCase();
    } catch (Exception e) {
      LOG.warn("Invalid encoding " + encoding + " detected, using default.");
      return null;
    }
  }

  /**
   * ParseResult the character encoding from the specified content type header. If the
   * content type is null, or there is no explicit character encoding,
   * <code>null</code> is returned.
   * This method was copied from org.apache.catalina.util.RequestUtil, which is
   * licensed under the Apache License, Version 2.0 (the "License").
   * 
   * @param contentTypeUtf8
   */
  public static String parseCharacterEncoding(CharSequence contentTypeUtf8) {
    if (contentTypeUtf8 == null) {
      return null;
    }

    String contentType = contentTypeUtf8.toString();
    int start = contentType.indexOf("charset=");
    if (start < 0) {
      return null;
    }

    String encoding = contentType.substring(start + 8);
    int end = encoding.indexOf(';');
    if (end >= 0) {
      encoding = encoding.substring(0, end);
    }

    encoding = encoding.trim();
    if ((encoding.length() > 2) && (encoding.startsWith("\"")) && (encoding.endsWith("\""))) {
      encoding = encoding.substring(1, encoding.length() - 1);
    }

    return encoding.trim();
  }

  /*
   * public static void main(String[] args) throws IOException { if (args.length
   * != 1) { System.err.println("Usage: EncodingDetector <file>");
   * System.exit(1); }
   * 
   * Configuration conf = ConfigUtils.create(); EncodingDetector detector
   * = new EncodingDetector(ConfigUtils.create());
   * 
   * // do everything as bytes; don't want any conversion BufferedInputStream
   * istr = new BufferedInputStream(new FileInputStream(args[0]));
   * ByteArrayOutputStream ostr = new ByteArrayOutputStream(); byte[] bytes =
   * new byte[1000]; boolean more = true; while (more) { int len =
   * istr.read(bytes); if (len < bytes.length) { more = false; if (len > 0) {
   * ostr.write(bytes, 0, len); } } else { ostr.write(bytes); } }
   * 
   * byte[] data = ostr.toByteArray(); MimeUtil mimeTypes = new MimeUtil(conf);
   * 
   * // make a fake Content Content content = new Content("", "", data,
   * "text/html", new Metadata(), mimeTypes);
   * 
   * detector.autoDetectClues(content, true); String encoding =
   * detector.guessEncoding(content,
   * conf.get("parser.character.encoding.default"));
   * System.out.println("Guessed encoding: " + encoding); }
   */

}
