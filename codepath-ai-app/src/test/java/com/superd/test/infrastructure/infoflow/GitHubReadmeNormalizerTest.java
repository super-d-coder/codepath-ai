package com.superd.test.infrastructure.infoflow;

import com.superd.infrastructure.gateway.infoflow.GitHubReadmeNormalizer;
import org.junit.Assert;
import org.junit.Test;

/** README URL 规范化离线测试，不访问网络。 */
public class GitHubReadmeNormalizerTest {

    private final GitHubReadmeNormalizer normalizer = new GitHubReadmeNormalizer();

    @Test
    public void test_normalize_shouldResolveMarkdownImageFromReadmeDirectory() {
        String result = normalizer.normalize("![logo](../assets/logo.png)", "acme/demo", "develop", "docs/README.md");
        Assert.assertEquals("![logo](https://raw.githubusercontent.com/acme/demo/develop/assets/logo.png)", result);
    }

    @Test
    public void test_normalize_shouldResolveMarkdownDocumentLink() {
        String result = normalizer.normalize("[install](./install.md)", "acme/demo", "main", "docs/README.md");
        Assert.assertEquals("[install](https://github.com/acme/demo/blob/main/docs/install.md)", result);
    }

    @Test
    public void test_normalize_shouldResolveHtmlImageAndLink() {
        String content = "<img src='./images/logo.png'><a href=\"../LICENSE\">license</a>";
        String result = normalizer.normalize(content, "acme/demo", "main", "docs/README.md");
        Assert.assertEquals("<img src='https://raw.githubusercontent.com/acme/demo/main/docs/images/logo.png'><a href=\"https://github.com/acme/demo/blob/main/LICENSE\">license</a>", result);
    }

    @Test
    public void test_normalize_shouldKeepExternalAndAnchorLinks() {
        String content = "[site](https://example.com) [install](#installation)";
        Assert.assertEquals(content, normalizer.normalize(content, "acme/demo", "main", "README.md"));
    }

    @Test
    public void test_normalize_shouldBlockDangerousProtocols() {
        String content = "[bad](javascript:alert(1)) <img src=\"file:///tmp/a.png\">";
        String result = normalizer.normalize(content, "acme/demo", "main", "README.md");
        Assert.assertFalse(result.contains("javascript:"));
        Assert.assertFalse(result.contains("file:"));
    }

    @Test
    public void test_normalize_shouldPreserveMarkdownTitleSuffix() {
        String result = normalizer.normalize("![logo](./logo.png \"Logo\")", "acme/demo", "main", "README.md");
        Assert.assertEquals("![logo](https://raw.githubusercontent.com/acme/demo/main/logo.png \"Logo\")", result);
    }
}
