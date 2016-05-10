/*
 * Copyright (c) 2016 The Ontario Institute for Cancer Research. All rights reserved.                             
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.repository.gdc.util;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.net.HttpHeaders.ACCEPT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.icgc.dcc.common.core.json.Jackson.DEFAULT;
import static org.icgc.dcc.common.core.json.JsonNodeBuilders.array;
import static org.icgc.dcc.common.core.json.JsonNodeBuilders.object;
import static org.icgc.dcc.common.core.util.Joiners.COMMA;

import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * http
 * 'https://gdc-api.nci.nih.gov/files?size=1&expand=cases&filter={“op”:”in”,”content”:{“field”:”program”,”value":[TCGA]}
 * }'
 * 
 * @see https://gdc-docs.nci.nih.gov/API/Users_Guide/Search_and_Retrieval/#files-endpoint
 */
@Slf4j
@RequiredArgsConstructor
public class GDCClient {

  /**
   * Constants
   */
  private static final String DEFAULT_API_URL = "https://gdc-api.nci.nih.gov";

  private static final int MAX_ATTEMPTS = 10;
  private static final int READ_TIMEOUT = (int) SECONDS.toMillis(60);
  private static final String APPLICATION_JSON = "application/json";

  private static final String TCGA_PROGRAM = "TCGA";
  private static final int PAGE_SIZE = 200;

  public GDCClient() {
    this(DEFAULT_API_URL);
  }

  /**
   * Configuration.
   */
  @NonNull
  private final String url;

  public JsonNode getFilesMapping() {
    return getMapping("/files");
  }

  public List<ObjectNode> getFiles() {
    return getFiles(ImmutableList.of());
  }

  public List<ObjectNode> getFiles(@NonNull List<String> expand) {
    return getFiles(expand, PAGE_SIZE, 1);
  }

  public List<ObjectNode> getFiles(@NonNull List<String> expand, int size, int from) {
    Pagination pagination = null;

    val results = ImmutableList.<ObjectNode> builder();
    do {
      val response = readFiles(expand, size, from);
      val hits = getHits(response);
      pagination = getPagination(response);
      log.info("{}", pagination);

      for (val hit : hits) {
        results.add((ObjectNode) hit);
      }

      from += size;
    } while (pagination.getPage() < pagination.getPages());

    val files = results.build();
    checkState(pagination.getTotal() == files.size(),
        "Pagination size (%s) not equal to files size (%s). There is a either a logic error or new files have been added while iterating",
        pagination.getCount(), files.size());

    return files;
  }

  private JsonNode readFiles(@NonNull List<String> expand, int size, int from) {
    val filters = createFilter(TCGA_PROGRAM);
    val request = "/files?size=" + size + "&from=" + from + "&filters=" + filters + "&expand=" + COMMA.join(expand);

    int attempts = 0;
    while (++attempts <= MAX_ATTEMPTS) {
      try {
        val connection = openConnection(request);

        val response = readResponse(connection);
        checkWarnings(response);

        return response;
      } catch (SocketTimeoutException e) {
        log.warn("Socket timeout for {} after {} attempt(s)", request, attempts);
      }
    }

    throw new IllegalStateException("Could not get " + request);
  }

  @SneakyThrows
  private JsonNode getMapping(String path) {
    val connection = openConnection(path + "/_mapping");

    return readResponse(connection);
  }

  private static ObjectNode createFilter(String program) {
    return object()
        .with("op", "in")
        .with("content",
            object()
                .with("field", "cases.project.program.name")
                .with("value", array(program)))
        .end();
  }

  @SneakyThrows
  private HttpURLConnection openConnection(String path) throws SocketTimeoutException {
    val request = new URL(url + path);

    log.info("Request: {}", request);
    val connection = (HttpsURLConnection) request.openConnection();
    connection.setRequestProperty(ACCEPT, APPLICATION_JSON);
    connection.setReadTimeout(READ_TIMEOUT);
    connection.setConnectTimeout(READ_TIMEOUT);

    return connection;
  }

  private static void checkWarnings(JsonNode response) {
    val warnings = response.path("warnings");
    if (warnings.size() > 0) {
      log.warn("Warnings: {}", warnings);
    }
  }

  @SneakyThrows
  private static JsonNode readResponse(URLConnection connection) {
    return DEFAULT.readTree(connection.getInputStream());
  }

  private static JsonNode getHits(JsonNode response) {
    return response.path("data").path("hits");
  }

  private static Pagination getPagination(JsonNode response) {
    return DEFAULT.convertValue(response.path("data").path("pagination"), Pagination.class);
  }

  @Data
  private static class Pagination {

    int count;
    String sort;
    int from;
    int page;
    int total;
    int pages;
    int size;

  }

}