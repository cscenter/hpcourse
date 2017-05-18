/**
 * Lock-Free ���������.
 * @param <T> ��� ������
 */
public interface LockFreeSet<T extends Comparable<T>> {
    /**
     * �������� ���� � ���������
     *
     * �������� ������ ���� ��� ������� lock-free
     *
     * @param value �������� �����
     * @return false ���� value ��� ���������� � ���������, true ���� ������� ��� ��������
     */
    boolean add(T value);

    /**
     * ������� ���� �� ���������
     *
     * �������� ������ ���� ��� ������� lock-free
     *
     * @param value �������� �����
     * @return false ���� ���� �� ��� ������, true ���� ���� ������� ������
     */
    boolean remove(T value);

    /**
     * �������� ������� ����� � ���������
     *
     * �������� ������ ���� ��� ������� wait-free
     *
     * @param value �������� �����
     * @return true ���� ������� ���������� � ���������, ����� - false
     */
    boolean contains(T value);

    /**
     * �������� ��������� �� �������
     *
     * �������� ������ ���� ��� ������� wait-free
     *
     * @return true ���� ��������� �����, ����� - false
     */
    boolean isEmpty();
}