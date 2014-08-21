package tuke.netbeans.dsl.support;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.fold.Fold;
import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.spi.editor.fold.FoldHierarchyTransaction;
import org.netbeans.spi.editor.fold.FoldManager;
import org.netbeans.spi.editor.fold.FoldOperation;
import org.openide.text.NbDocument;

public class EDSLFoldManager implements FoldManager {

    private FoldOperation operation;
    private Document doc;
    private static Pattern pattern = Pattern.compile("(EDSLAddon_start_fold( *desc=\"([\\S \\t]*?)\")?)|(EDSLAddon_end_fold)");
    private static final Logger LOG = Logger.getLogger(EDSLFoldManager.class.getName());

    @Override
    public void init(FoldOperation fo) {
        this.operation = fo;
    }

    @Override
    public void initFolds(FoldHierarchyTransaction fht) {
        SwingUtilities.invokeLater(new AddFolds());
    }

    @Override
    public void insertUpdate(DocumentEvent de, FoldHierarchyTransaction fht) {
        SwingUtilities.invokeLater(new AddFolds());
    }

    @Override
    public void removeUpdate(DocumentEvent de, FoldHierarchyTransaction fht) {
        //SwingUtilities.invokeLater(new AddFolds()); -- this should be redundant, as folds are guareded, they cannot be damaged
    }

    @Override
    public void changedUpdate(DocumentEvent de, FoldHierarchyTransaction fht) {
        //SwingUtilities.invokeLater(new AddFolds()); -- this causes inifinite looping because adding guards 
        //                                               apparently calls this notification
    }

    @Override
    public void removeEmptyNotify(Fold fold) {
    }

    @Override
    public void removeDamagedNotify(Fold fold) {
    }

    @Override
    public void expandNotify(Fold fold) {
    }

    @Override
    public void release() {
    }

    private List<EDSLFoldManager.Mark> validateMarks() {
        List<EDSLFoldManager.Mark> marks = scanTokens();
        List<EDSLFoldManager.Mark> preparedMarks = new LinkedList<EDSLFoldManager.Mark>();
        Stack<EDSLFoldManager.Mark> stack = new Stack<EDSLFoldManager.Mark>();
        for (EDSLFoldManager.Mark mark : marks) { // pair them up
            if (mark.start) {
                stack.push(mark);
            } else {
                if (!stack.empty()) {
                    EDSLFoldManager.Mark starting = stack.pop();
                    starting.ending = mark;
                    // take care of IllegalArgumentException of Fold constructor
                    if (starting.position.getOffset() < starting.ending.position.getOffset()
                            && (starting.ending.position.getOffset() - starting.position.getOffset()) >= (starting.GP + starting.ending.GP)) {
                        preparedMarks.add(starting);
                    }
                }
            }
        }
        return preparedMarks;
    }

    private List<EDSLFoldManager.Mark> scanTokens() {
        List<EDSLFoldManager.Mark> marks = new LinkedList<EDSLFoldManager.Mark>();
        TokenHierarchy th = TokenHierarchy.get(doc);
        TokenSequence ts = th.tokenSequence();
        for (ts.moveStart(); ts.moveNext();) {
            Token token = ts.token();
            EDSLFoldManager.Mark info = null;
            try {
                info = scanToken(token);
            } catch (BadLocationException e) {
                LOG.log(Level.WARNING, null, e);
            }

            if (info != null) {
                marks.add(info);
            }
        }
        return marks;
    }

    private EDSLFoldManager.Mark scanToken(Token token) throws BadLocationException {
        // ignore any token that is not comment
        if ("comment".equals(token.id().primaryCategory())) {
            Matcher matcher = pattern.matcher(token.text());
            if (matcher.find()) {
                if (matcher.group(1) != null) { // fold's start mark found
                    return new EDSLFoldManager.Mark(doc, token.offset(null), true, matcher.end(0), (matcher.group(3) != null ? matcher.group(3) : ""));
                } else { // fold's end mark found
                    return new EDSLFoldManager.Mark(doc, token.offset(null) + token.length() - 1, false, matcher.end(0), null);
                }
            }
        }
        return null;
    }

    private class AddFolds implements Runnable {

        private boolean insideRender;

        public void run() {
            if (!insideRender) {
                insideRender = true;
                operation.getHierarchy().getComponent().getDocument().render(this);
                return;
            }
            doc = operation.getHierarchy().getComponent().getDocument();

            List<Integer> fromTo = new LinkedList<Integer>();

            operation.getHierarchy().lock();

            FoldHierarchyTransaction transaction = operation.openTransaction();
            List<Mark> marks = validateMarks();
            try {
                for (EDSLFoldManager.Mark mark : marks) {
                    operation.addToHierarchy(new FoldType("EDSLAddon"),
                            mark.description,
                            true,
                            mark.position.getOffset(),
                            mark.ending.position.getOffset(),
                            mark.GP,
                            mark.ending.GP,
                            mark,
                            transaction);
                    fromTo.add(mark.position.getOffset());
                    fromTo.add(mark.ending.position.getOffset() - mark.position.getOffset());
                }
            } catch (BadLocationException ex) {
                LOG.log(Level.WARNING, null, ex);
            } finally {
                transaction.commit();
            }
            operation.getHierarchy().unlock();

            for (Mark mark : marks) {
                NbDocument.markGuarded((StyledDocument) doc,
                        mark.position.getOffset(),
                        mark.ending.position.getOffset() - mark.position.getOffset() + 1);
            }
        }
    }

    protected static final class Mark {

        private final Position position;
        private final boolean start;
        private final int GP;
        private EDSLFoldManager.Mark ending;
        private final String description;

        public Mark(Document doc, int position, boolean start, int GP, String description) throws BadLocationException {
            this.position = doc.createPosition(position);
            this.start = start;
            this.GP = GP;
            this.description = description;
        }
    }
}
