package com.github.dsilva.flowtc;


import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;


/**
 * Plugin entry point
 */
public class FlowTypeCheckInspection extends LocalInspectionTool {
    private static final Logger log = Logger.getInstance(FlowTypeCheckInspection.class);

    @NotNull
    public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
        log.info("Flow inspection running");
        return TypeCheck.errors(file).stream()
                .map(error -> manager.createProblemDescriptor(
                        file,
                        error.range(),
                        error.message(),
                        ProblemHighlightType.ERROR,
                        isOnTheFly))
                .toArray(ProblemDescriptor[]::new);
    }

}
