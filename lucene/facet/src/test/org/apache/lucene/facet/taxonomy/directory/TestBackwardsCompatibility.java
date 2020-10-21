package org.apache.lucene.facet.taxonomy.directory;

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

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/*
  Verify we can read previous versions' indexes, do searches
  against them, and add documents to them.
*/
// See: https://issues.apache.org/jira/browse/SOLR-12028 Tests cannot remove files on Windows machines occasionally
public class TestBackwardsCompatibility extends LuceneTestCase {

    // Backcompat index generation, described below, is mostly automated in:
    //
    //    dev-tools/scripts/addBackcompatIndexes.py
    //
    // For usage information, see:
    //
    //    http://wiki.apache.org/lucene-java/ReleaseTodo#Generate_Backcompat_Indexes
    //
    // -----
    //
    // To generate backcompat indexes with the current default codec, run the following ant command:
    //  ant test -Dtestcase=TestBackwardsCompatibility -Dtests.bwcdir=/path/to/store/indexes
    //           -Dtests.codec=default -Dtests.useSecurityManager=false
    // Also add testmethod with one of the index creation methods below, for example:
    //    -Dtestmethod=testCreateCFS
    //
    // Zip up the generated indexes:
    //
    //    cd /path/to/store/indexes/index.cfs   ; zip index.<VERSION>-cfs.zip *
    //    cd /path/to/store/indexes/index.nocfs ; zip index.<VERSION>-nocfs.zip *
    //
    // Then move those 2 zip files to your trunk checkout and add them
    // to the oldNames array.

    public void testCreateOldTaxonomy() throws IOException {
        createTaxoIndex("taxo.cfs");
    }

    private void createTaxoIndex(String dirName) throws IOException {
        Path indexDir = getIndexDir().resolve(dirName);
        Directory dir = newFSDirectory(indexDir);

        final IndexWriter iw = new IndexWriter(dir,
                new IndexWriterConfig(new MockAnalyzer(random()))
                        .setMergePolicy(new LogByteSizeMergePolicy()));
        DirectoryTaxonomyWriter writer = new DirectoryTaxonomyWriter(dir) {
            @Override
            protected IndexWriter openIndexWriter(Directory directory,
                                                  IndexWriterConfig config) throws IOException {
                return iw;
            }
        };
        TaxonomyReader reader = new DirectoryTaxonomyReader(dir);

        int  n = 10;
        for (int i=0; i<n; i++) {
            writer.addCategory(new FacetLabel("b",Integer.toString(i)));
        }
        iw.forceMerge(1);

        for (int i=0; i<n; i++) {
            int ord1 = reader.getOrdinal(new FacetLabel("a", Integer.toString(i)));
            int ord2 = reader.getOrdinal(new FacetLabel("b", Integer.toString(i)));
            assert ord1!=TaxonomyReader.INVALID_ORDINAL;
            assert ord2!=TaxonomyReader.INVALID_ORDINAL;
        }

        reader.close();
        writer.close();
        dir.close();
    }

    private Path getIndexDir() {
        String path = System.getProperty("tests.bwcdir");
        assumeTrue("backcompat creation tests must be run with -Dtests.bwcdir=/path/to/write/indexes", path != null);
        return Paths.get(path);
    }
}