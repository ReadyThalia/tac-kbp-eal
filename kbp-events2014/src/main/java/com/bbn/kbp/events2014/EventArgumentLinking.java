package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.*;
import com.google.common.collect.*;

import java.util.Set;

import static com.google.common.base.Functions.compose;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.and;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;
import static com.google.common.collect.Iterables.transform;

public final class EventArgumentLinking {
    private final Symbol docID;
    private final ImmutableSet<TypeRoleFillerRealisSet> coreffedArgSets;
    private final ImmutableSet<TypeRoleFillerRealis> incomplete;

    private EventArgumentLinking(Symbol docID, Iterable<TypeRoleFillerRealisSet> coreffedArgSets,
                                 Iterable<TypeRoleFillerRealis> incomplete)
    {
        this.docID = checkNotNull(docID);
        this.coreffedArgSets = ImmutableSet.copyOf(coreffedArgSets);
        this.incomplete = ImmutableSet.copyOf(incomplete);
    }

    public static EventArgumentLinking create(Symbol docID,
                                              Iterable<TypeRoleFillerRealisSet> coreffedArgSets,
                                              Iterable<TypeRoleFillerRealis> incomplete)
    {
        return new EventArgumentLinking(docID, coreffedArgSets, incomplete);
    }

    public Symbol docID() {
        return docID;
    }

    public ImmutableSet<TypeRoleFillerRealisSet> linkedAsSet() {
        return coreffedArgSets;
    }

    public ImmutableSet<TypeRoleFillerRealis> incomplete() {
        return incomplete;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(docID, coreffedArgSets, incomplete);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final EventArgumentLinking other = (EventArgumentLinking) obj;
        return Objects.equal(this.docID, other.docID) && Objects.equal(this.coreffedArgSets, other.coreffedArgSets)
                && Objects.equal(this.incomplete, other.incomplete);
    }

    private static final Function<AssessedResponse,KBPRealis> REALIS_OF_RESPONSE =
            compose(Response.realisFunction(), AssessedResponse.Response);


    public static EventArgumentLinking createMinimalLinkingFrom(AnswerKey answerKey,
                                                                Set<KBPRealis> realisesToLink)
    {
        final Function<Response, TypeRoleFillerRealis> ToEquivalenceClass =
                TypeRoleFillerRealis.extractFromSystemResponse(
                        answerKey.corefAnnotation().strictCASNormalizerFunction());

        final Predicate<AssessedResponse> RealisIsRelevant =
                compose(in(realisesToLink), REALIS_OF_RESPONSE);
        final Predicate<AssessedResponse> CorrectWithRelevantRealis = and(
                AssessedResponse.IsCorrectUpToInexactJustifications,
                RealisIsRelevant);

        return EventArgumentLinking.create(answerKey.docId(),
                ImmutableSet.<TypeRoleFillerRealisSet>of(),
                ImmutableSet.copyOf(FluentIterable.from(answerKey.annotatedResponses())
                        .filter(CorrectWithRelevantRealis)
                        .transform(AssessedResponse.Response)
                        .transform(ToEquivalenceClass)));
    }
}