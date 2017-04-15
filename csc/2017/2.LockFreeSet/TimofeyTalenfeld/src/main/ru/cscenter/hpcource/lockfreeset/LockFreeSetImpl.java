package ru.cscenter.hpcource.lockfreeset;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    /**
     * Добавить ключ к множеству
     * <p>
     * Алгоритм должен быть как минимум lock-free
     *
     * @param value значение ключа
     * @return false если value уже существует в множестве, true если элемент был добавлен
     */
    public boolean add(T value) {
        return false;
    }

    /**
     * Удалить ключ из множества
     * <p>
     * Алгоритм должен быть как минимум lock-free
     *
     * @param value значение ключа
     * @return false если ключ не был найден, true если ключ успешно удален
     */
    public boolean remove(T value) {
        return false;
    }

    /**
     * Проверка наличия ключа в множестве
     * <p>
     * Алгоритм должен быть как минимум wait-free
     *
     * @param value значение ключа
     * @return true если элемент содержится в множестве, иначе - false
     */
    public boolean contains(T value) {
        return false;
    }

    /**
     * Проверка множества на пустоту
     * <p>
     * Алгоритм должен быть как минимум wait-free
     *
     * @return true если множество пусто, иначе - false
     */
    public boolean isEmpty() {
        return false;
    }
}
