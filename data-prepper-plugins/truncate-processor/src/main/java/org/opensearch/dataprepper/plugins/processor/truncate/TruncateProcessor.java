/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.truncate;

import org.opensearch.dataprepper.expression.ExpressionEvaluator;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.model.processor.AbstractProcessor;
import org.opensearch.dataprepper.model.processor.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

import static org.opensearch.dataprepper.logging.DataPrepperMarkers.EVENT;

/**
 * This processor takes in a key and truncates its value to a string with
 * characters from the front or at the end or at both removed.
 * If the value is not a string, no action is performed.
 */
@DataPrepperPlugin(name = "truncate", pluginType = Processor.class, pluginConfigurationType = TruncateProcessorConfig.class)
public class TruncateProcessor extends AbstractProcessor<Record<Event>, Record<Event>>{
    private static final Logger LOG = LoggerFactory.getLogger(TruncateProcessor.class);
    private final ExpressionEvaluator expressionEvaluator;
    private final List<TruncateProcessorConfig.Entry> entries;

    @DataPrepperPluginConstructor
    public TruncateProcessor(final PluginMetrics pluginMetrics, final TruncateProcessorConfig config, final ExpressionEvaluator expressionEvaluator) {
        super(pluginMetrics);
        this.expressionEvaluator = expressionEvaluator;
        this.entries = config.getEntries();
    }

    private String getTruncatedValue(final String value, final int startIndex, final Integer length) {
        String truncatedValue = 
            (length == null || startIndex+length >= value.length()) ? 
            value.substring(startIndex) : 
            value.substring(startIndex, startIndex + length);

        return truncatedValue;
    }

    @Override
    public Collection<Record<Event>> doExecute(final Collection<Record<Event>> records) {
        for(final Record<Event> record : records) {
            final Event recordEvent = record.getData();

            try {
                for (TruncateProcessorConfig.Entry entry : entries) {
                    final List<String> sourceKeys = entry.getSourceKeys();
                    final String truncateWhen = entry.getTruncateWhen();
                    final int startIndex = entry.getStartAt() == null ? 0 : entry.getStartAt();
                    final Integer length = entry.getLength();
                    if (truncateWhen != null && !expressionEvaluator.evaluateConditional(truncateWhen, recordEvent)) {
                        continue;
                    }
                    for (String sourceKey : sourceKeys) {
                        if (!recordEvent.containsKey(sourceKey)) {
                            continue;
                        }

                        final Object value = recordEvent.get(sourceKey, Object.class);
                        if (value instanceof String) {
                            recordEvent.put(sourceKey, getTruncatedValue((String) value, startIndex, length));
                        } else if (value instanceof List) {
                            List<Object> result = new ArrayList<>();
                            for (Object listItem : (List) value) {
                                if (listItem instanceof String) {
                                    result.add(getTruncatedValue((String) listItem, startIndex, length));
                                } else {
                                    result.add(listItem);
                                }
                            }
                            recordEvent.put(sourceKey, result);
                        }
                    }
                }
            } catch (final Exception e) {
                LOG.error(EVENT, "There was an exception while processing Event [{}]", recordEvent, e);
            }
        }

        return records;
    }

    @Override
    public void prepareForShutdown() {

    }

    @Override
    public boolean isReadyForShutdown() {
        return true;
    }

    @Override
    public void shutdown() {

    }
}

