/*******************************************************************************
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
 ******************************************************************************/
package org.warps.monitor.crawl.parse;

import org.warps.pulsar.persist.Outlink;
import org.warps.pulsar.persist.ParseStatus;

import java.util.ArrayList;

public class ParseResult {

  public static ParseResult EMPTY_RESULT = new ParseResult();

  private String pageTitle;
  private String pageText;
  private ArrayList<Outlink> outlinks;
  private ParseStatus parseStatus;

  public ParseResult() {
    pageTitle = "";
    pageText = "";
    outlinks = new ArrayList<>();
    parseStatus = ParseStatus.STATUS_UNKNOWN;
  }

  public ParseResult(String pageTitle, String pageText, ArrayList<Outlink> outlinks, ParseStatus parseStatus) {
    this.pageTitle = pageTitle;
    this.pageText = pageText;
    this.outlinks = outlinks;
    this.parseStatus = parseStatus;
  }

  public ParseResult(Throwable e) {
    parseStatus = new ParseStatus(e);
  }

  public ParseResult(int minorCode, String message) {
    parseStatus = new ParseStatus(minorCode, message);
  }

  public String getPageText() { return pageText; }

  public void setPageText(String pageText) {
    this.pageText = pageText;
  }

  public String getPageTitle() { return pageTitle; }

  public void setPageTitle(String pageTitle) {
    this.pageTitle = pageTitle;
  }

  public ArrayList<Outlink> getOutlinks() { return outlinks; }

  public void setOutlinks(ArrayList<Outlink> outlinks) {
    this.outlinks = outlinks;
  }

  public ParseStatus getParseStatus() { return parseStatus; }

  public void setParseStatus(ParseStatus parseStatus) { this.parseStatus = parseStatus; }

  public boolean isSuccess() { return parseStatus.isSuccess(); }

  @Override
  public String toString() {
    return "pageTitle : " + pageText + ", parseStatus : " + ", " + parseStatus;
  }
}
