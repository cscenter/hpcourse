package kornilova.set;

import java.util.Collection;
import java.util.HashSet;

interface SnapCollector<T extends Comparable<T>> {
    /**
     * @return true if the Deactivate() method has not yet been called,
     *         and false otherwise.
     *         (True means the iteration is still ongoing and further
     *         pointers might still be installed in the snapshot object.)
     */
    boolean isActive();

    /**
     * After this method is complete, any call to {@link #isActive()} returns false,
     * whereas before this method is invoked for the first time, isActive returns true.
     */
    void deactivate();

    /**
     * In-stalls a pointer to the given node.
     * May fail to install the pointer if the BlockFurther-Pointers() method (see below)
     * has previously been invoked.
     */
    void addNode(Node<T> node);

    /**
     * Required to synchronize between multiple iterators.
     * After this method is completed, any further calls to AddNode will do nothing.
     * Calls to AddNode concurrent with BlockFurtherPointers may fail or succeed arbitrarily.
     */
    void blockFurtherNodes();

    void blockFurtherReports();

    HashSet<Node<T>> readNodes();

    Collection<Report<T>> readReports();

    /**
     * Installs the given report.
     * May fail to install the report if the BlockFurtherReports()
     * method (see below) has previously been invoked
     */
    void report(Report<T> report);

    enum ReportType {
        INSERT,
        DELETE
    }
}
