package dev.leetcodebreak.catalog;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.HttpRequests;
import dev.leetcodebreak.model.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public final class CatalogLoader {
    @FunctionalInterface public interface Transport { String post(String json) throws Exception; }
    public record Update(List<Problem> problems, int total, boolean complete) {}

    public static Transport publicTransport(Site site) {
        return json -> HttpRequests.post(site.origin() + "/graphql/", "application/json")
            .userAgent("LeetCodeBreak/0.1 (IntelliJ plugin)").connectTimeout(15_000).readTimeout(25_000)
            .tuner(connection -> connection.setRequestProperty("Referer", site.problemSet()))
            .connect(request -> {
                request.write(json);
                try (var input = request.getInputStream()) {
                    byte[] bytes = input.readNBytes(4_000_001);
                    if (bytes.length > 4_000_000) throw new IOException("题库响应过大");
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            });
    }

    public static void load(Site site, Transport transport, ProgressIndicator indicator, Consumer<Update> consumer) throws Exception {
        var catalog = new LinkedHashMap<String, Problem>();
        var schema = site == Site.CN ? LeetCodeApi.Schema.CN_PROBLEMSET : LeetCodeApi.Schema.QUESTION_LIST;
        for (int offset = 0; offset < 100_000;) {
            indicator.checkCanceled();
            String response = transport.post(LeetCodeApi.request(offset, schema));
            LeetCodeApi.Page page;
            try { page = LeetCodeApi.parse(response); }
            catch (LeetCodeApi.SchemaException e) {
                if (offset > 0) throw e;
                schema = schema == LeetCodeApi.Schema.CN_PROBLEMSET ? LeetCodeApi.Schema.QUESTION_LIST : LeetCodeApi.Schema.CN_PROBLEMSET;
                page = LeetCodeApi.parse(transport.post(LeetCodeApi.request(offset, schema)));
            }
            indicator.checkCanceled();
            int previous = catalog.size();
            page.problems().forEach(p -> catalog.put(p.slug(), p));
            offset += page.received();
            boolean complete = offset >= page.total();
            if (!complete && (page.received() == 0 || previous == catalog.size())) throw new IOException("官方题库分页未继续，已保留获取到的题目，请重新同步。");
            indicator.setFraction(page.total() == 0 ? 1 : Math.min(1, offset / (double) page.total()));
            indicator.setText2("已获取 " + catalog.size() + " / " + page.total() + " 道官方题目");
            consumer.accept(new Update(List.copyOf(catalog.values()), page.total(), complete));
            if (complete) return;
            // Sequential, modest requests; cancellation remains responsive.
            for (int i = 0; i < 4; i++) { indicator.checkCanceled(); Thread.sleep(100); }
        }
        throw new IOException("题库超出同步上限，请直接打开官方题库。");
    }
}
