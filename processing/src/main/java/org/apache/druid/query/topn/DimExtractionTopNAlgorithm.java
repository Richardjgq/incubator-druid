/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.query.topn;

import org.apache.druid.query.ColumnSelectorPlus;
import org.apache.druid.query.aggregation.Aggregator;
import org.apache.druid.query.topn.types.TopNColumnSelectorStrategy;
import org.apache.druid.segment.Cursor;
import org.apache.druid.segment.StorageAdapter;

import java.util.Map;

/**
 * This has to be its own strategy because the pooled topn algorithm assumes each index is unique, and cannot handle multiple index numerals referencing the same dimension value.
 */
public class DimExtractionTopNAlgorithm
    extends BaseTopNAlgorithm<Aggregator[][], Map<Comparable, Aggregator[]>, TopNParams>
{
  private final TopNQuery query;

  public DimExtractionTopNAlgorithm(
      StorageAdapter storageAdapter,
      TopNQuery query
  )
  {
    super(storageAdapter);

    this.query = query;
  }

  @Override
  public TopNParams makeInitParams(
      final ColumnSelectorPlus<TopNColumnSelectorStrategy> selectorPlus,
      final Cursor cursor
  )
  {
    return new TopNParams(
        selectorPlus,
        cursor,
        Integer.MAX_VALUE
    );
  }

  @Override
  protected Aggregator[][] makeDimValSelector(TopNParams params, int numProcessed, int numToProcess)
  {
    if (params.getCardinality() < 0) {
      throw new UnsupportedOperationException("Cannot operate on a dimension with unknown cardinality");
    }
    ColumnSelectorPlus<TopNColumnSelectorStrategy> selectorPlus = params.getSelectorPlus();
    return selectorPlus.getColumnSelectorStrategy().getDimExtractionRowSelector(query, params, storageAdapter);
  }

  @Override
  protected Aggregator[][] updateDimValSelector(Aggregator[][] aggregators, int numProcessed, int numToProcess)
  {
    return aggregators;
  }

  @Override
  protected Map<Comparable, Aggregator[]> makeDimValAggregateStore(TopNParams params)
  {
    final ColumnSelectorPlus<TopNColumnSelectorStrategy> selectorPlus = params.getSelectorPlus();
    return selectorPlus.getColumnSelectorStrategy().makeDimExtractionAggregateStore();
  }

  @Override
  public long scanAndAggregate(
      TopNParams params,
      Aggregator[][] rowSelector,
      Map<Comparable, Aggregator[]> aggregatesStore
  )
  {
    final Cursor cursor = params.getCursor();
    final ColumnSelectorPlus<TopNColumnSelectorStrategy> selectorPlus = params.getSelectorPlus();

    return selectorPlus.getColumnSelectorStrategy().dimExtractionScanAndAggregate(
        query,
        selectorPlus.getSelector(),
        cursor,
        rowSelector,
        aggregatesStore
    );
  }

  @Override
  protected void updateResults(
      TopNParams params,
      Aggregator[][] rowSelector,
      Map<Comparable, Aggregator[]> aggregatesStore,
      TopNResultBuilder resultBuilder
  )
  {
    final ColumnSelectorPlus<TopNColumnSelectorStrategy> selectorPlus = params.getSelectorPlus();
    selectorPlus.getColumnSelectorStrategy().updateDimExtractionResults(
        aggregatesStore,
        resultBuilder
    );
  }

  @Override
  protected void closeAggregators(Map<Comparable, Aggregator[]> valueMap)
  {
    for (Aggregator[] aggregators : valueMap.values()) {
      for (Aggregator agg : aggregators) {
        agg.close();
      }
    }
  }

  @Override
  public void cleanup(TopNParams params)
  {
  }
}
