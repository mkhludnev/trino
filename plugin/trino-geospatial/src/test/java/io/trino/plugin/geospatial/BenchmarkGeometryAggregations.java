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
package io.trino.plugin.geospatial;

import com.google.common.collect.ImmutableMap;
import io.trino.plugin.memory.MemoryConnectorFactory;
import io.trino.testing.LocalQueryRunner;
import io.trino.testing.MaterializedResult;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.io.Resources.getResource;
import static io.trino.jmh.Benchmarks.benchmark;
import static io.trino.testing.TestingSession.testSessionBuilder;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.openjdk.jmh.annotations.Mode.AverageTime;
import static org.openjdk.jmh.annotations.Scope.Thread;

@OutputTimeUnit(MILLISECONDS)
@BenchmarkMode(AverageTime)
@Fork(3)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
public class BenchmarkGeometryAggregations
{
    @State(Thread)
    public static class Context
    {
        private LocalQueryRunner queryRunner;

        public LocalQueryRunner getQueryRunner()
        {
            return queryRunner;
        }

        @Setup
        public void setUp()
                throws Exception
        {
            queryRunner = LocalQueryRunner.create(testSessionBuilder()
                    .setCatalog("memory")
                    .setSchema("default")
                    .build());
            queryRunner.installPlugin(new GeoPlugin());
            queryRunner.createCatalog("memory", new MemoryConnectorFactory(), ImmutableMap.of());

            Path path = new File(getResource("us-states.tsv").toURI()).toPath();
            String polygonValues;
            try (Stream<String> lines = Files.lines(path)) {
                polygonValues = lines
                        .map(line -> line.split("\t"))
                        .map(parts -> format("('%s', '%s')", parts[0], parts[1]))
                        .collect(Collectors.joining(","));
            }

            queryRunner.execute(
                    format("CREATE TABLE memory.default.us_states AS SELECT ST_GeometryFromText(t.wkt) AS geom FROM (VALUES %s) as t (name, wkt)",
                            polygonValues));
        }

        @TearDown
        public void tearDown()
        {
            queryRunner.close();
            queryRunner = null;
        }
    }

    @Benchmark
    public MaterializedResult benchmarkArrayUnion(Context context)
    {
        return context.getQueryRunner()
                .execute("SELECT geometry_union(array_agg(p.geom)) FROM us_states p");
    }

    @Benchmark
    public MaterializedResult benchmarkUnion(Context context)
    {
        return context.getQueryRunner()
                .execute("SELECT geometry_union_agg(p.geom) FROM us_states p");
    }

    @Benchmark
    public MaterializedResult benchmarkConvexHull(Context context)
    {
        return context.getQueryRunner()
                .execute("SELECT convex_hull_agg(p.geom) FROM us_states p");
    }

    @Test
    public void verify()
            throws Exception
    {
        Context context = new Context();
        try {
            context.setUp();

            BenchmarkGeometryAggregations benchmark = new BenchmarkGeometryAggregations();
            benchmark.benchmarkUnion(context);
            benchmark.benchmarkArrayUnion(context);
            benchmark.benchmarkConvexHull(context);
        }
        finally {
            context.queryRunner.close();
        }
    }

    public static void main(String[] args)
            throws Exception
    {
        new BenchmarkGeometryAggregations().verify();

        benchmark(BenchmarkGeometryAggregations.class).run();
    }
}
