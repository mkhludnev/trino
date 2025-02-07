/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.hive.metastore;

import io.trino.filesystem.local.LocalFileSystemFactory;
import io.trino.plugin.hive.NodeVersion;
import io.trino.plugin.hive.metastore.file.FileHiveMetastore;
import io.trino.plugin.hive.metastore.file.FileHiveMetastoreConfig;
import org.junit.jupiter.api.AfterAll;

import java.io.IOException;
import java.nio.file.Path;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static java.nio.file.Files.createTempDirectory;

final class TestFileHiveMetastore
        extends AbstractTestHiveMetastore
{
    private final Path tempDir;

    TestFileHiveMetastore()
            throws IOException
    {
        tempDir = createTempDirectory("test");
        tempDir.toFile().mkdirs();
        LocalFileSystemFactory fileSystemFactory = new LocalFileSystemFactory(tempDir);

        setMetastore(new FileHiveMetastore(
                new NodeVersion("testversion"),
                fileSystemFactory,
                false,
                new FileHiveMetastoreConfig()
                        .setCatalogDirectory("local:///")
                        .setMetastoreUser("test")
                        .setDisableLocationChecks(true)));
    }

    @AfterAll
    void tearDown()
            throws IOException
    {
        deleteRecursively(tempDir, ALLOW_INSECURE);
    }
}
