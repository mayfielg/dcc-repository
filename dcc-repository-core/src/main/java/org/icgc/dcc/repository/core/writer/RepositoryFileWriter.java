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
package org.icgc.dcc.repository.core.writer;

import static org.icgc.dcc.common.core.util.Formats.formatCount;
import static org.icgc.dcc.repository.core.model.RepositoryCollection.FILE;

import org.icgc.dcc.repository.core.model.RepositoryCollection;
import org.icgc.dcc.repository.core.model.RepositoryFile;
import org.icgc.dcc.repository.core.util.AbstractJongoWriter;
import org.jongo.MongoCollection;

import com.mongodb.MongoClientURI;

import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepositoryFileWriter extends AbstractJongoWriter<Iterable<RepositoryFile>> {

  /**
   * Configuration.
   */
  @Getter
  @NonNull
  private final RepositoryCollection fileCollection;

  /**
   * Dependencies.
   */
  @NonNull
  private final MongoCollection collection;

  public RepositoryFileWriter(MongoClientURI mongoUri) {
    this(mongoUri, FILE);
  }

  public RepositoryFileWriter(MongoClientURI mongoUri, @NonNull RepositoryCollection fileCollection) {
    super(mongoUri);
    this.fileCollection = fileCollection;
    this.collection = getCollection(fileCollection);
  }

  @Override
  public void write(@NonNull Iterable<RepositoryFile> files) {
    log.info("Clearing '{}' documents...", collection.getName());
    clearFiles();

    log.info("Writing {} '{}' documents...", formatCount(files), collection.getName());
    int writeCount = 0;
    for (val file : files) {
      saveFile(file);

      writeCount++;
      if (writeCount % 10000 == 0) {
        log.info("Wrote {} '{}' documents", formatCount(writeCount), collection.getName());
      }
    }

    log.info("Finished writing {} '{}' documents", formatCount(writeCount), collection.getName());
  }

  public void clearFiles() {
    clearDocuments(fileCollection);
  }

  protected void saveFile(RepositoryFile file) {
    collection.save(file);
  }

}
