package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.scorer.KBPScorer;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.scorer.observers.*;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.HTMLErrorRecorder;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.NullHTMLErrorRecorder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.union;

public final class KBPScorerBin {
    private static Logger log = LoggerFactory.getLogger(KBPScorerBin.class);

    private KBPScorerBin() {
        throw new UnsupportedOperationException();
    }

    private static void usage() {
        log.warn("usage: kbpScorer param_file\n" +
            "parameters are:\n" +
            "\tanswerKey: annotation store to score against\n" +
            "If running on a single output store:\n" +
                "\tscoringOutput: directory to write scoring observer logs to\n"+
                "\tsystemOutput: system output store to score\n"+
                "\tdocumentsToScore: (optional) file listing which documents to score. If not present, scores all in either store."+
            "If running on multiple stores:\n" +
                "\tscoringOutputRoot: directory to write scoring observer logs to. A subdirectory will be created for each input store.\n"+
                "\tsystemOutputsDir: each subdirectory of this is expected to be a system output store to score\n"+
                "\tdocumentsToScore: file listing which documents to score."
        );
        System.exit(1);
    }

    private static List<KBPScoringObserver<TypeRoleFillerRealis>> getCorpusObservers(Parameters params) {
        final List<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers = Lists.newArrayList();

        if (params.getBoolean("annotationComplete")) {
            corpusObservers.add(ExitOnUnannotatedAnswerKey.<TypeRoleFillerRealis>create());
        }


        final HTMLErrorRecorder dummyRecorder = NullHTMLErrorRecorder.getInstance();
        // Lax scorer
        corpusObservers.add(BuildConfusionMatrix.<TypeRoleFillerRealis>forAnswerFunctions(
                "Lax", KBPScorer.IsPresent, KBPScorer.AnyAnswerSemanticallyCorrect));
        corpusObservers.add(StrictStandardScoringObserver.strictScorer(dummyRecorder));
        corpusObservers.add(StrictStandardScoringObserver.standardScorer(dummyRecorder));
        corpusObservers.add(SkipIncompleteAnnotations.wrap(StrictStandardScoringObserver.strictScorer(dummyRecorder)));
        corpusObservers.add(SkipIncompleteAnnotations.wrap(StrictStandardScoringObserver.standardScorer(dummyRecorder)));
        return corpusObservers;
    }

    public static void main(final String[] argv) throws IOException {
        if (argv.length != 1) {
            usage();
        }
        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());

        final KBPScorer scorer = KBPScorer.create();
        final AnnotationStore goldAnswerStore = AssessmentSpecFormats.openAnnotationStore(params
                .getExistingDirectory("answerKey"));
        final List<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers = getCorpusObservers(params);


        checkArgument(params.isPresent("systemOutput") != params.isPresent("systemOutputsDir"),
                "Exactly one of systemOutput and systemOutputsDir must be specified");
        if (params.isPresent("systemOutput")) {
            scoreSingleSystemOutputStore(goldAnswerStore, corpusObservers, scorer, params);
        } else if (params.isPresent("systemOutputsDir")) {
            scoreMultipleSystemOutputStores(goldAnswerStore, scorer, corpusObservers, params);
        } else {
            throw new RuntimeException("Can't happen");
        }
    }

    private static void scoreMultipleSystemOutputStores(AnnotationStore goldAnswerStore, KBPScorer scorer, List<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers, Parameters params) throws IOException {
        final File systemOutputsDir = params.getExistingDirectory("systemOutputsDir");
        final File scoringOutputRoot = params.getCreatableDirectory("scoringOutputRoot");

        log.info("Scoring all subdirectories of {}", systemOutputsDir);

        final Set<Symbol> documentsToScore = loadDocumentsToScore(params);

        for (File subDir : systemOutputsDir.listFiles()) {
            if (subDir.isDirectory()) {
                final SystemOutputStore systemOutputStore = AssessmentSpecFormats.openSystemOutputStore(subDir);
                final File outputDir = new File(scoringOutputRoot, subDir.getName());
                outputDir.mkdirs();
                scorer.run(systemOutputStore, goldAnswerStore, documentsToScore,
                        corpusObservers, outputDir);
            }
        }
    }

    private static void scoreSingleSystemOutputStore(AnnotationStore goldAnswerStore, List<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers, KBPScorer scorer, Parameters params) throws IOException {
        final File systemOutputDir = params.getExistingDirectory("systemOutput");
        log.info("Scoring single system output {}", systemOutputDir);
        final SystemOutputStore systemOutputStore = AssessmentSpecFormats.openSystemOutputStore(systemOutputDir);
        final Set<Symbol> documentsToScore;
        if (params.isPresent("documentsToScore")) {
            documentsToScore = loadDocumentsToScore(params);
        } else {
            documentsToScore = union(systemOutputStore.docIDs(), goldAnswerStore.docIDs());
        }

        scorer.run(systemOutputStore, goldAnswerStore, documentsToScore,
                corpusObservers, params.getCreatableDirectory("scoringOutput"));
    }

    private static ImmutableSet<Symbol> loadDocumentsToScore(Parameters params) throws IOException {
        final File docsToScoreList = params.getExistingFile("documentsToScore");
        final ImmutableSet<Symbol> ret = ImmutableSet.copyOf(FileUtils.loadSymbolList(docsToScoreList));
        log.info("Scoring over {} documents specified in {}", ret.size(), docsToScoreList);
        return ret;
    }

}
