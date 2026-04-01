package repository.interfaces;

import model.Categoria;
import model.Plato;

import java.util.List;

public interface PlatoRepository extends Repository<Plato, String> {
    List<Plato> findByCategoria(Categoria categoria);
    List<Plato> findByEstaActivo(boolean estaActivo);
    List<Plato> findByNombre(String nombre);
}
