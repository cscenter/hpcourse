package kornilova.set;

class Report<T extends Comparable<T>> {
    final SnapCollector.ReportType type;
    final Node<T> node;

    public Report(SnapCollector.ReportType type, Node<T> node) {
        this.type = type;
        this.node = node;
    }
}
