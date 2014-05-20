package com.bbn.com.bbn.kbp.events2014.assessmentDiff;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.com.bbn.kbp.events2014.assessmentDiff.diffLoggers.BasicDiffLogger;
import com.bbn.com.bbn.kbp.events2014.assessmentDiff.diffLoggers.DiffLogger;
import com.bbn.com.bbn.kbp.events2014.assessmentDiff.observers.*;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AsssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.google.common.base.Charsets;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public final class KBPAssessmentDiff {
    private static final Logger log = LoggerFactory.getLogger(KBPAssessmentDiff.class);

    private KBPAssessmentDiff() {
        throw new UnsupportedOperationException();
    }

    private static void trueMain(String[] argv) throws IOException {
        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());

        final String leftName = params.getString("leftAnnotationName");
        final AnnotationStore leftAnnotationStore = AssessmentSpecFormats.openAnnotationStore(
                params.getExistingDirectory("leftAnnotationStore"));
        final String rightName = params.getString("rightAnnotationName");
        final AnnotationStore rightAnnotationStore = AssessmentSpecFormats.openAnnotationStore(
                params.getExistingDirectory("rightAnnotationStore"));
        final File outputDirectory = params.getCreatableDirectory("outputDirectory");

        final Set<Symbol> commonDocIds = Sets.intersection(leftAnnotationStore.docIDs(), rightAnnotationStore.docIDs());
        logCoverage(leftName, leftAnnotationStore.docIDs(), rightName, rightAnnotationStore.docIDs(),
                Files.asCharSink(new File(outputDirectory, "coverage.txt"), Charsets.UTF_8));

        final AssessmentOverlapObserver overlapObserver = new AssessmentOverlapObserver();
        final Map<String, AssessmentPairObserver> observers = createObservers(outputDirectory);

        final DiffLogger diffLogger = new BasicDiffLogger();

        for (final Symbol docId : commonDocIds) {
            final AnswerKey leftAnswers = leftAnnotationStore.readOrEmpty(docId);
            final AnswerKey rightAnswers = rightAnnotationStore.read(docId);
            overlapObserver.observe(leftAnswers, rightAnswers);

            final Set<Response> commonResponses =
             Sets.intersection(
                FluentIterable.from(leftAnswers.annotatedResponses())
                    .transform(AsssessedResponse.Response)
                    .toSet(),
                FluentIterable.from(rightAnswers.annotatedResponses())
                    .transform(AsssessedResponse.Response)
                    .toSet());

            for (final Response response : commonResponses) {
                final ResponseAssessment leftAssessment = leftAnswers.assessment(response).get();
                final ResponseAssessment rightAssessment = rightAnswers.assessment(response).get();

                for (final AssessmentPairObserver observer : observers.values()) {
                    observer.observe(response, leftAssessment, rightAssessment);
                }
            }
        }

        overlapObserver.report();
        for (final Map.Entry<String, AssessmentPairObserver> entry : observers.entrySet()) {
            final File observerOutputDir = new File(outputDirectory, entry.getKey());
            observerOutputDir.mkdirs();
            entry.getValue().finish(diffLogger, observerOutputDir);
        }
    }

    private static Map<String, AssessmentPairObserver> createObservers(File outputDirectory) {
        return ImmutableMap.<String, AssessmentPairObserver>builder()
                .put("aet", new AETObserver())
                .put("aer", new AERObserver())
                .put("baseFiller", new BaseFillerObserver())
                .put("CAS", new CASObserver())
                .put("realis", new RealisObserver())
                .put("mentionType", new MentionTypeObserver()).build();
    }

    private static void logCoverage(String leftName, Set<Symbol> leftDocIds, String rightName, Set<Symbol> rightDocIds,
                                    CharSink out) throws IOException
    {
        final String msg = String.format(
                "%d documents in %s; %d in %s. %d in common, %d left-only, %d right-only",
                leftDocIds.size(), leftName, rightDocIds.size(), rightName,
                Sets.intersection(leftDocIds, rightDocIds).size(),
                Sets.difference(leftDocIds, rightDocIds).size(),
                Sets.difference(rightDocIds, leftDocIds).size());
        log.info(msg);
        out.write(msg);
    }

    public static void main(String[] argv) {
        try {
            trueMain(argv);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}
