/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.techsenger.jeditermfx.ui;

import com.techsenger.jeditermfx.core.model.hyperlinks.HyperlinkFilter;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkInfo;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResult;
import com.techsenger.jeditermfx.core.model.hyperlinks.LinkResultItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class DefaultHyperlinkFilter implements HyperlinkFilter {

    private static final Logger logger = LoggerFactory.getLogger(DefaultHyperlinkFilter.class);

    private static final Pattern URL_PATTERN = Pattern.compile("\\b(mailto:|(news|(ht|f)tp(s?))://|((?<![\\p{L}0-9_.])"
            + "(www\\.)))[-A-Za-z0-9+$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+$&@#/%=~_|]");

    /**
     * @return if false, then the line contains no URL; if true, then more heavy {@link #URL_PATTERN} check should be used.
     */
    public static boolean canContainUrl(@NotNull String line) {
        return line.contains("mailto:") || line.contains("://") || line.contains("www.");
    }

    @Nullable
    @Override
    public LinkResult apply(String line) {
        if (!canContainUrl(line)) return null;
        int textStartOffset = 0;
        Matcher m = URL_PATTERN.matcher(line);
        LinkResultItem item = null;
        List<LinkResultItem> items = null;
        while (m.find()) {
            if (item != null) {
                if (items == null) {
                    items = new ArrayList<>(2);
                    items.add(item);
                }
            }
            String url = m.group();
            item = new LinkResultItem(textStartOffset + m.start(), textStartOffset + m.end(), new LinkInfo(() -> {
                try {
                    var d = Desktop.getDesktop();
                    if (d != null) {
                        EventQueue.invokeLater(() -> {
                            try {
                                d.browse(new URI(url));
                            } catch (Exception ex) {
                                logger.error("Error opening url: {}", url, ex);
                            }
                        });
                    }
                } catch (Exception e) {
                    //pass
                }
            }));
            if (items != null) {
                items.add(item);
            }
        }
        return items != null ? new LinkResult(items)
                : item != null ? new LinkResult(item)
                : null;
    }
}
