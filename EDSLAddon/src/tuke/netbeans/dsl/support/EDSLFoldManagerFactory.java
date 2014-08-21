package tuke.netbeans.dsl.support;

import org.netbeans.spi.editor.fold.FoldManager;
import org.netbeans.spi.editor.fold.FoldManagerFactory;

public class EDSLFoldManagerFactory implements FoldManagerFactory {

    /** Creates a new instance of JavaElementFoldManagerFactory */
    public EDSLFoldManagerFactory() {
    }

    public FoldManager createFoldManager() {
        return new EDSLFoldManager();
    }

}