/*
 * Copyright 2018 the original author or authors.
 *
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

package org.sahli.asciidoc.confluence.publisher.cli;

import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher;
import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisherListener;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestClient;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.PublishingStrategy;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluenceConverter;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider;
import org.sahli.asciidoc.confluence.publisher.converter.FolderBasedAsciidocPagesStructureProvider;
import org.sahli.asciidoc.confluence.publisher.converter.PageTitlePostProcessor;
import org.sahli.asciidoc.confluence.publisher.converter.PrefixAndSuffixPageTitlePostProcessor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.walkFileTree;
import static java.util.Arrays.stream;
import static org.sahli.asciidoc.confluence.publisher.client.metadata.PublishingStrategy.APPEND_TO_ANCESTOR;

public class AsciidocConfluencePublisherCommandLineClient {

    public static void main(String[] args) throws Exception {
        String rootConfluenceUrl = mandatoryArgument("rootConfluenceUrl", args);
        String username = mandatoryArgument("username", args);
        String password = mandatoryArgument("password", args);
        String spaceKey = mandatoryArgument("spaceKey", args);
        String ancestorId = mandatoryArgument("ancestorId", args);

        PublishingStrategy publishingStrategy = PublishingStrategy.valueOf(optionalArgument("strategy", args).orElse(APPEND_TO_ANCESTOR.name()));

        Path documentationRootFolder = Paths.get(mandatoryArgument("asciidocRootFolder", args));
        Path buildFolder = createTempDirectory("confluence-publisher");

        Charset sourceEncoding = Charset.forName(optionalArgument("sourceEncoding", args).orElse("UTF-8"));
        String prefix = optionalArgument("pageTitlePrefix", args).orElse(null);
        String suffix = optionalArgument("pageTitleSuffix", args).orElse(null);

        try {
            AsciidocPagesStructureProvider asciidocPagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(documentationRootFolder, sourceEncoding);
            PageTitlePostProcessor pageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor(prefix, suffix);

            AsciidocConfluenceConverter asciidocConfluenceConverter = new AsciidocConfluenceConverter(spaceKey, ancestorId);
            ConfluencePublisherMetadata confluencePublisherMetadata = asciidocConfluenceConverter.convert(asciidocPagesStructureProvider, pageTitlePostProcessor, buildFolder);
            confluencePublisherMetadata.setPublishingStrategy(publishingStrategy);

            ConfluenceRestClient confluenceClient = new ConfluenceRestClient(rootConfluenceUrl, username, password);
            ConfluencePublisher confluencePublisher = new ConfluencePublisher(confluencePublisherMetadata, confluenceClient, new SystemOutLoggingConfluencePublisherListener());
            confluencePublisher.publish();
        } finally {
            deleteDirectory(buildFolder);
        }
    }

    private static String mandatoryArgument(String key, String[] args) {
        return optionalArgument(key, args)
                .orElseThrow(() -> new IllegalArgumentException("mandatory argument '" + key + "' is missing"));
    }

    private static Optional<String> optionalArgument(String key, String[] args) {
        return stream(args)
                .filter((keyAndValue) -> keyAndValue.startsWith(key + "="))
                .map((keyAndValue) -> keyAndValue.substring(keyAndValue.indexOf('=') + 1))
                .filter((value) -> !value.isEmpty())
                .findFirst();
    }

    private static void deleteDirectory(Path buildFolder) throws IOException {
        walkFileTree(buildFolder, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                delete(path);

                return CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
                delete(path);

                return CONTINUE;
            }

        });
    }


    private static class SystemOutLoggingConfluencePublisherListener implements ConfluencePublisherListener {

        @Override
        public void pageAdded(ConfluencePage addedPage) {
            System.out.println("Added page '" + addedPage.getTitle() + "' (id " + addedPage.getContentId() + ")");
        }

        @Override
        public void pageUpdated(ConfluencePage existingPage, ConfluencePage updatedPage) {
            System.out.println("Updated page '" + updatedPage.getTitle() + "' (id " + updatedPage.getContentId() + ", version " + existingPage.getVersion() + " -> " + updatedPage.getVersion() + ")");
        }

        @Override
        public void pageDeleted(ConfluencePage deletedPage) {
            System.out.println("Deleted page '" + deletedPage.getTitle() + "' (id " + deletedPage.getContentId() + ")");
        }

        @Override
        public void publishCompleted() {
            System.out.println("Documentation successfully published to Confluence");
        }

    }

}
