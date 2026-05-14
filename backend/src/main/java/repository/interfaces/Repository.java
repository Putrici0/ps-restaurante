package repository.interfaces;

import java.util.List;
import java.util.Optional;

public interface Repository<T, ID> {
    List<T> findAll();
    List<T> findPage(int limit, ID cursor);
    Optional<T>  findById(ID id);
    T save(T entity);
    T update(ID id, T entity);
    void deleteById(ID id);
    boolean existsById(ID id);
}
