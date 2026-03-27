package repository.interfaces;

import model.Categoria;
import model.Plato;

import java.util.List;

public interface PlatoRepository extends Repository<Plato, String> {
    List<Plato> findByCategoria(Categoria categoria);
    List<Plato> findByActivo(Boolean activo);
    List<Plato> findByNombre(String nombre);
}
