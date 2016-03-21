package com.bbn.kbp.events2014;


import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.SortedSet;

import static com.google.common.base.Preconditions.checkState;


@TextGroupPublicImmutable
@Value.Immutable
@Functional
public abstract class _QueryResponse2016 {

  public abstract Symbol queryID();

  public abstract Symbol docID();

  public abstract Symbol systemID();

  @Value.NaturalOrder
  public abstract SortedSet<CharOffsetSpan> predicateJustifications();

  @Value.Check
  protected void check() {
    checkState(!queryID().asString().isEmpty(), "Empty query IDs not allowed!");
    checkState(!docID().asString().isEmpty(), "Empty doc IDs not allowed!");
    checkState(!systemID().asString().isEmpty(), "Empty system IDs not allowed!");
    checkState(predicateJustifications().size() > 0, "Must provide PredicateJustifications!");
  }
}
