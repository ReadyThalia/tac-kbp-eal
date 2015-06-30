package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.collections.TableUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.bin.QA.AssessmentQA;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by jdeyoung on 6/4/15.
 */
public class ConflictingTypeWarningRule extends OverlapWarningRule {

  private static final Logger log = LoggerFactory.getLogger(ConflictingTypeWarningRule.class);

  private final ImmutableTable<Symbol, Symbol, ImmutableSet<Symbol>> eventToRoleToFillerType;

  private ConflictingTypeWarningRule(final Table<Symbol, Symbol, ImmutableSet<Symbol>> eventToRoleToFillerType) {
    super();
    this.eventToRoleToFillerType = ImmutableTable.copyOf(eventToRoleToFillerType);
  }


  @Override
  protected boolean warningAppliesTo(TypeRoleFillerRealis fst, TypeRoleFillerRealis snd) {
    return !fst.type().equals(snd.type());
  }

  @Override
  protected Multimap<Response, Warning> findOverlap(
      final TypeRoleFillerRealis fst, final Iterable<Response> first,
      final TypeRoleFillerRealis snd, final Iterable<Response> second) {
    final ImmutableMultimap.Builder<Response, Warning> result =
        ImmutableMultimap.builder();
    for (final Response a : first) {
      final Set<Symbol> possibleFillerTypesForFirst = eventToRoleToFillerType.get(a.type(), a.role());
      checkNotNull(possibleFillerTypesForFirst);
      for (final Response b : second) {
        if (a.equals(b) || b.canonicalArgument().string().trim().isEmpty()) {
          continue;
        }
        // changing this conditions means getTypeDescription must be updated as well
        if (a.canonicalArgument().equals(b.canonicalArgument())) {
          final Set<Symbol> possibleFillerTypesForSecond = eventToRoleToFillerType.get(b.type(), b.role());
          checkNotNull(possibleFillerTypesForSecond);
          final boolean noRolesPossibleForBoth =
              Sets.intersection(possibleFillerTypesForFirst, possibleFillerTypesForSecond).isEmpty();
          if (noRolesPossibleForBoth) {
            result.put(b, Warning.create(getTypeString(), String
                    .format(
                        " mismatched types %s/%s and %s/%s in trfr %s",
                        a.type().asString(), a.role().asString(),
                        b.type().asString(), b.role().asString(), AssessmentQA.readableTRFR(snd)),
                Warning.Severity.MINOR));
          }
        }
      }
    }
    return result.build();
  }

  public static ConflictingTypeWarningRule create(File argsFile, File rolesFile)
      throws IOException {
    final ImmutableMultimap<Symbol, Symbol> roleToTypes = FileUtils.loadSymbolMultimap(
        Files.asCharSource(rolesFile, Charsets.UTF_8));
    final ImmutableMultimap<Symbol, Symbol> eventTypesToRoles = FileUtils.loadSymbolMultimap(
        Files.asCharSource(argsFile, Charsets.UTF_8));

    final ImmutableTable<Symbol, Symbol, ImmutableSet<Symbol>> eventToRoleToFillerType =
        TableUtils.columnTransformerByKeyOnly(composeToTableOfSets(eventTypesToRoles, roleToTypes),
            new Function<Symbol, Symbol>() {
              @Override
              public Symbol apply(final Symbol input) {
                String key = input.asString().replaceAll("\\[.\\]", "");
                return Symbol.from(key);
              }
            });

    log.info("Role to type mapping: {}",
        StringUtils.NewlineJoiner.withKeyValueSeparator(" -> ").join(roleToTypes.asMap()));
    log.info("type to concrete type mapping: {}",
        StringUtils.NewlineJoiner.withKeyValueSeparator(" -> ").join(eventTypesToRoles.asMap()));
    for (Symbol r : eventToRoleToFillerType.rowKeySet()) {
      for (Symbol c : eventToRoleToFillerType.columnKeySet()) {
        if (c != null) {
          log.info("t: {}.{}: {}", r, c, eventToRoleToFillerType.get(r, c));
        }
      }
    }

    return new ConflictingTypeWarningRule(ImmutableTable.copyOf(eventToRoleToFillerType));
  }

  @MoveToBUECommon
  private static <R,C,V> ImmutableTable<R, C, ImmutableSet<V>> composeToTableOfSets(
      final Multimap<R, C> first,
      final Multimap<C, V> second) {
    final ImmutableTable.Builder<R,C,ImmutableSet<V>> ret = ImmutableTable.builder();
    for (final Map.Entry<R, C> firstEntry : first.entries()) {
      final R rowKey = firstEntry.getKey();
      final C colKey = firstEntry.getValue();
      log.info("{},{} -> {}", rowKey, colKey, second.get(colKey));

      ret.put(rowKey, colKey, ImmutableSet.copyOf(second.get(colKey)));
    }
    return ret.build();
  }

  @Override
  public String getTypeString() {
    return "Conflicting Role";
  }

  @Override
  public String getTypeDescription() {
    return "We think this response is the same as another (by identical string) but with an incompatible type, maybe it should not have this type/role?";
  }
}